#!/usr/bin/env bash
#
# Teste de integracao ponta a ponta dos 3 servicos (issue #16).
#
# Como os tres servicos sao aplicacoes Quarkus independentes (processos
# separados, cada uma com seu proprio banco), nao e possivel exercitar o
# fluxo completo num unico @QuarkusTest. Em vez disso, este script sobe os
# tres servicos de verdade (apontando para a infra do infra/docker-compose.yml)
# e reproduz o fluxo principal via HTTP, com asserts em cada etapa:
#
#   1. MEDICO registra um paciente (records-command-service)
#   2. MEDICO registra uma evolucao clinica (consulta)              (command)
#   3. Um profissional SEM acesso concedido tenta consultar o        (query)
#      prontuario consolidado -> 403 (AccessDenied)
#   4. GESTOR concede acesso ao MEDICO que fez o atendimento         (command)
#   5. O MEDICO consulta o prontuario consolidado -> 200 com o       (query)
#      historico completo (RecordAccessed)
#   6. AUDITOR consulta a trilha de auditoria do paciente e confere  (audit)
#      que todos os eventos anteriores foram registrados
#
# Uso:
#   ./scripts/integration-test.sh
#
# Pre-requisitos: infra do infra/docker-compose.yml rodando (postgres x3 +
# redpanda) e os 3 servicos buildados (./mvnw clean package -DskipTests).
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$(mktemp -d)"
COMMAND_URL="http://localhost:8081"
QUERY_URL="http://localhost:8082"
AUDIT_URL="http://localhost:8083"

PASS=0
FAIL=0
PIDS=()

log()  { printf '\033[1;34m[integration-test]\033[0m %s\n' "$1"; }
ok()   { PASS=$((PASS+1)); printf '  \033[1;32mOK\033[0m   %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  \033[1;31mFAIL\033[0m %s\n' "$1"; }

cleanup() {
  log "Encerrando servicos iniciados por este script..."
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT

wait_for_infra() {
  log "Verificando infra (postgres x3 + redpanda)..."
  for name in prontuario-postgres-command prontuario-postgres-query prontuario-postgres-audit prontuario-redpanda; do
    if ! docker inspect -f '{{.State.Health.Status}}' "$name" 2>/dev/null | grep -q healthy; then
      echo "Container '$name' nao esta 'healthy'. Suba a infra com:"
      echo "  cd infra && docker compose up -d"
      exit 1
    fi
  done
  ok "infra saudavel"
}

start_service() {
  local name="$1" module="$2" port="$3"
  local jar="$ROOT_DIR/$module/target/quarkus-app/quarkus-run.jar"
  if [[ ! -f "$jar" ]]; then
    echo "Jar nao encontrado para $module ($jar). Rode: ./mvnw clean package -DskipTests"
    exit 1
  fi
  java -Dquarkus.profile=prod -jar "$jar" >"$LOG_DIR/$name.log" 2>&1 &
  PIDS+=("$!")
  log "Iniciando $name (pid ${PIDS[-1]}, porta $port, log em $LOG_DIR/$name.log)..."
}

wait_ready() {
  local name="$1" url="$2"
  for _ in $(seq 1 60); do
    if curl -fsS "$url/q/health/ready" >/dev/null 2>&1; then
      ok "$name pronto ($url)"
      return 0
    fi
    sleep 1
  done
  bad "$name nao respondeu /q/health/ready a tempo"
  echo "----- ultimas linhas de $LOG_DIR/$name.log -----"
  tail -n 40 "$LOG_DIR/$name.log" 2>/dev/null || true
  exit 1
}

token_for() {
  local professional_id="$1" role="$2"
  curl -fsS -X POST "$COMMAND_URL/dev/tokens" \
    -H 'Content-Type: application/json' \
    -d "{\"professionalId\":\"$professional_id\",\"roles\":[\"$role\"]}" | jq -r '.token'
}

assert_status() {
  local description="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    ok "$description (status $actual)"
  else
    bad "$description (esperado $expected, recebido $actual)"
  fi
}

assert_contains() {
  local description="$1" haystack="$2" needle="$3"
  if echo "$haystack" | grep -q "$needle"; then
    ok "$description"
  else
    bad "$description (nao encontrou '$needle')"
  fi
}

main() {
  wait_for_infra

  start_service "records-command-service" "records-command-service" 8081
  start_service "records-query-service" "records-query-service" 8082
  start_service "audit-service" "audit-service" 8083

  wait_ready "records-command-service" "$COMMAND_URL"
  wait_ready "records-query-service" "$QUERY_URL"
  wait_ready "audit-service" "$AUDIT_URL"

  local run_id medico_id enfermeiro_id gestor_id auditor_id unit_id
  run_id="$(date +%s)-$$"
  medico_id="medico-$run_id"
  enfermeiro_id="enfermeiro-$run_id"
  gestor_id="gestor-$run_id"
  auditor_id="auditor-$run_id"
  unit_id="unit-$run_id"

  local medico_token gestor_token enfermeiro_token auditor_token
  medico_token="$(token_for "$medico_id" MEDICO)"
  gestor_token="$(token_for "$gestor_id" GESTOR)"
  enfermeiro_token="$(token_for "$enfermeiro_id" ENFERMEIRO)"
  auditor_token="$(token_for "$auditor_id" AUDITOR)"

  log "1) MEDICO registra um paciente"
  local register_response register_status patient_id
  register_response="$(curl -sS -w '\n%{http_code}' -X POST "$COMMAND_URL/patients" \
    -H "Authorization: Bearer $medico_token" -H 'Content-Type: application/json' \
    -d "{\"fullName\":\"Paciente Teste $run_id\",\"cpf\":\"cpf-$run_id\",\"unitId\":\"$unit_id\"}")"
  register_status="$(echo "$register_response" | tail -n1)"
  patient_id="$(echo "$register_response" | sed '$d' | jq -r '.patientId')"
  assert_status "registrar paciente" 201 "$register_status"

  log "2) ENFERMEIRO sem acesso concedido tenta registrar uma evolucao clinica -> esperado 403 (AccessDenied, write-side RBAC)"
  local unauthorized_write_status
  unauthorized_write_status="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$COMMAND_URL/patients/$patient_id/consultations" \
    -H "Authorization: Bearer $enfermeiro_token" -H 'Content-Type: application/json' \
    -d "{\"unitId\":\"$unit_id\",\"notes\":\"tentativa sem acesso\"}")"
  assert_status "escrita negada sem acesso concedido" 403 "$unauthorized_write_status"

  log "3) GESTOR concede acesso ao MEDICO que registrou o paciente"
  local grant_status
  grant_status="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$COMMAND_URL/patients/$patient_id/access-grants" \
    -H "Authorization: Bearer $gestor_token" -H 'Content-Type: application/json' \
    -d "{\"professionalId\":\"$medico_id\",\"unitId\":\"$unit_id\"}")"
  assert_status "conceder acesso" 201 "$grant_status"

  log "4) MEDICO (agora autorizado) registra uma evolucao clinica (consulta) para o paciente $patient_id"
  local consultation_status
  consultation_status="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$COMMAND_URL/patients/$patient_id/consultations" \
    -H "Authorization: Bearer $medico_token" -H 'Content-Type: application/json' \
    -d "{\"unitId\":\"$unit_id\",\"notes\":\"Paciente estavel, retorno em 30 dias.\"}")"
  assert_status "registrar evolucao clinica" 201 "$consultation_status"

  log "5) Aguardando projecao assincrona (Kafka -> records-query-service) e validando que o ENFERMEIRO ainda sem acesso e negado na consulta (AccessDenied)"
  local denied_status=""
  for _ in $(seq 1 20); do
    denied_status="$(curl -sS -o /dev/null -w '%{http_code}' "$QUERY_URL/patients/$patient_id/record?unitId=$unit_id" \
      -H "Authorization: Bearer $enfermeiro_token")"
    [[ "$denied_status" == "403" ]] && break
    sleep 1
  done
  assert_status "acesso negado ao profissional sem grant" 403 "$denied_status"

  log "6) MEDICO consulta o prontuario consolidado (agora autorizado) -> esperado 200"
  local record_response record_status
  for _ in $(seq 1 20); do
    record_response="$(curl -sS -w '\n%{http_code}' "$QUERY_URL/patients/$patient_id/record?unitId=$unit_id" \
      -H "Authorization: Bearer $medico_token")"
    record_status="$(echo "$record_response" | tail -n1)"
    [[ "$record_status" == "200" ]] && break
    sleep 1
  done
  assert_status "consultar prontuario consolidado apos acesso concedido" 200 "$record_status"
  local record_body
  record_body="$(echo "$record_response" | sed '$d')"
  assert_contains "prontuario contem PatientRegistered" "$record_body" "PatientRegistered"
  assert_contains "prontuario contem ConsultationRecorded" "$record_body" "ConsultationRecorded"

  log "7) AUDITOR consulta a trilha de auditoria do paciente"
  local audit_body audit_status
  local audit_full_response
  for _ in $(seq 1 20); do
    audit_full_response="$(curl -sS -w '\n%{http_code}' "$AUDIT_URL/patients/$patient_id/audit-log" \
      -H "Authorization: Bearer $auditor_token")"
    audit_status="$(echo "$audit_full_response" | tail -n1)"
    audit_body="$(echo "$audit_full_response" | sed '$d')"
    if [[ "$audit_status" == "200" ]] && echo "$audit_body" | grep -q "AccessGranted"; then
      break
    fi
    sleep 1
  done
  assert_status "consultar trilha de auditoria" 200 "$audit_status"
  assert_contains "auditoria registrou PatientRegistered" "$audit_body" "PatientRegistered"
  assert_contains "auditoria registrou ConsultationRecorded" "$audit_body" "ConsultationRecorded"
  assert_contains "auditoria registrou AccessDenied" "$audit_body" "AccessDenied"
  assert_contains "auditoria registrou AccessGranted" "$audit_body" "AccessGranted"
  assert_contains "auditoria registrou RecordAccessed" "$audit_body" "RecordAccessed"

  echo
  log "Resultado: $PASS ok, $FAIL falhas"
  [[ "$FAIL" -eq 0 ]]
}

main
