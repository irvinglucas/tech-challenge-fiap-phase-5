#!/usr/bin/env bash
#
# Popula os servicos com dados de exemplo para a demo/video (issue #17):
# alguns pacientes, atendimentos em unidades diferentes, um acesso negado e
# um alerta de acesso anomalo (mesmo profissional acessando varios pacientes
# de varias unidades em poucos minutos).
#
# Uso:
#   ./scripts/seed.sh
#
# Pre-requisitos: infra do infra/docker-compose.yml rodando e os 3 servicos
# de pe (./mvnw clean package -DskipTests && java -jar .../quarkus-run.jar,
# ou mvn quarkus:dev em cada modulo).
set -uo pipefail

COMMAND_URL="${COMMAND_URL:-http://localhost:8081}"
QUERY_URL="${QUERY_URL:-http://localhost:8082}"
AUDIT_URL="${AUDIT_URL:-http://localhost:8083}"

UNIT_A="unit-ubs-vila-esperanca"
UNIT_B="unit-hospital-central"
UNIT_C="unit-ubs-jardim-sul"

log() { printf '\033[1;34m[seed]\033[0m %s\n' "$1"; }

token_for() {
  local professional_id="$1" role="$2"
  curl -fsS -X POST "$COMMAND_URL/dev/tokens" \
    -H 'Content-Type: application/json' \
    -d "{\"professionalId\":\"$professional_id\",\"roles\":[\"$role\"]}" | jq -r '.token'
}

register_patient() {
  local token="$1" full_name="$2" cpf="$3" unit_id="$4"
  curl -fsS -X POST "$COMMAND_URL/patients" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"fullName\":\"$full_name\",\"cpf\":\"$cpf\",\"unitId\":\"$unit_id\"}" | jq -r '.patientId'
}

grant_access() {
  local gestor_token="$1" patient_id="$2" professional_id="$3" unit_id="$4"
  curl -fsS -X POST "$COMMAND_URL/patients/$patient_id/access-grants" \
    -H "Authorization: Bearer $gestor_token" -H 'Content-Type: application/json' \
    -d "{\"professionalId\":\"$professional_id\",\"unitId\":\"$unit_id\"}" >/dev/null
}

record_consultation() {
  local token="$1" patient_id="$2" unit_id="$3" notes="$4"
  curl -fsS -o /dev/null -X POST "$COMMAND_URL/patients/$patient_id/consultations" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"unitId\":\"$unit_id\",\"notes\":\"$notes\"}"
}

add_diagnosis() {
  local token="$1" patient_id="$2" unit_id="$3" description="$4" cid10="$5"
  curl -fsS -o /dev/null -X POST "$COMMAND_URL/patients/$patient_id/diagnoses" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"unitId\":\"$unit_id\",\"description\":\"$description\",\"cid10\":\"$cid10\"}"
}

query_record() {
  local token="$1" patient_id="$2" unit_id="$3"
  curl -fsS -o /dev/null -w '%{http_code}' "$QUERY_URL/patients/$patient_id/record?unitId=$unit_id" \
    -H "Authorization: Bearer $token"
}

main() {
  log "Emitindo tokens de demonstracao..."
  local gestor_token dra_ana_token dr_bruno_token enf_carla_token auditor_token
  gestor_token="$(token_for gestor-carlos GESTOR)"
  dra_ana_token="$(token_for medica-ana MEDICO)"
  dr_bruno_token="$(token_for medico-bruno MEDICO)"
  enf_carla_token="$(token_for enfermeira-carla ENFERMEIRO)"
  auditor_token="$(token_for auditor-diego AUDITOR)"

  log "Registrando pacientes..."
  local p1 p2 p3
  p1="$(register_patient "$dra_ana_token" "Joana Pereira" "$(date +%s)01" "$UNIT_A")"
  log "  Joana Pereira -> $p1 ($UNIT_A)"
  p2="$(register_patient "$dr_bruno_token" "Marcos Oliveira" "$(date +%s)02" "$UNIT_B")"
  log "  Marcos Oliveira -> $p2 ($UNIT_B)"
  p3="$(register_patient "$dra_ana_token" "Fernanda Costa" "$(date +%s)03" "$UNIT_C")"
  log "  Fernanda Costa -> $p3 ($UNIT_C)"

  log "Concedendo acesso e registrando atendimentos..."
  grant_access "$gestor_token" "$p1" "medica-ana" "$UNIT_A"
  record_consultation "$dra_ana_token" "$p1" "$UNIT_A" "Consulta de rotina, sem queixas."
  add_diagnosis "$dra_ana_token" "$p1" "$UNIT_A" "Rinite alergica" "J30"

  grant_access "$gestor_token" "$p2" "medico-bruno" "$UNIT_B"
  record_consultation "$dr_bruno_token" "$p2" "$UNIT_B" "Dor lombar apos esforco fisico."
  add_diagnosis "$dr_bruno_token" "$p2" "$UNIT_B" "Lombalgia" "M54.5"

  grant_access "$gestor_token" "$p3" "medica-ana" "$UNIT_C"
  record_consultation "$dra_ana_token" "$p3" "$UNIT_C" "Acompanhamento pre-natal, 20 semanas."

  log "Aguardando projecao assincrona..."
  sleep 3

  log "Demonstrando acesso negado: enfermeira sem acesso concedido tentando ler o prontuario de Marcos"
  local denied_status
  denied_status="$(query_record "$enf_carla_token" "$p2" "$UNIT_B")"
  log "  status recebido: $denied_status (esperado 403)"

  log "Gerando alerta de acesso anomalo: gestor-carlos consultando 3 pacientes de unidades diferentes em poucos segundos"
  grant_access "$gestor_token" "$p1" "gestor-carlos" "$UNIT_A"
  grant_access "$gestor_token" "$p2" "gestor-carlos" "$UNIT_B"
  grant_access "$gestor_token" "$p3" "gestor-carlos" "$UNIT_C"
  sleep 2
  query_record "$gestor_token" "$p1" "$UNIT_A" >/dev/null
  query_record "$gestor_token" "$p2" "$UNIT_B" >/dev/null
  query_record "$gestor_token" "$p3" "$UNIT_C" >/dev/null

  echo
  log "Seed concluido. IDs para usar na demo/Postman:"
  echo "  patientId Joana Pereira   = $p1 (unidade $UNIT_A)"
  echo "  patientId Marcos Oliveira = $p2 (unidade $UNIT_B)"
  echo "  patientId Fernanda Costa  = $p3 (unidade $UNIT_C)"
  echo "  medicoToken (Dra. Ana)     = $dra_ana_token"
  echo "  medicoToken (Dr. Bruno)    = $dr_bruno_token"
  echo "  gestorToken (Carlos)       = $gestor_token"
  echo "  auditorToken (Diego)       = $auditor_token"
  echo
  log "Confira o alerta de acesso anomalo em: GET $AUDIT_URL/alerts/suspicious-access (Authorization: Bearer \$auditorToken)"
}

main
