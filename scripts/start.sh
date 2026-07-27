#!/usr/bin/env bash
#
# Sobe o projeto local completo: infra (Postgres x3, Redpanda, Jaeger) +
# os 3 servicos Quarkus em background.
#
# Uso:
#   ./scripts/start.sh              # sobe tudo (empacota se faltar jar)
#   ./scripts/start.sh --rebuild    # forca ./mvnw package -DskipTests
#   ./scripts/start.sh --seed       # apos ficar pronto, roda ./scripts/seed.sh
#   ./scripts/start.sh --rebuild --seed
#
# Para encerrar: ./scripts/stop.sh
#
# Pre-requisitos: Docker rodando e usuario no grupo docker; Java 21; Maven
# Wrapper (./mvnw).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
LOG_DIR="$RUN_DIR/logs"
PID_FILE="$RUN_DIR/services.pid"

COMMAND_URL="http://localhost:8081"
QUERY_URL="http://localhost:8082"
AUDIT_URL="http://localhost:8083"

REBUILD=0
SEED=0

log()  { printf '\033[1;34m[start]\033[0m %s\n' "$1"; }
ok()   { printf '  \033[1;32mOK\033[0m   %s\n' "$1"; }
warn() { printf '  \033[1;33mWARN\033[0m %s\n' "$1"; }
die()  { printf '\033[1;31m[start]\033[0m %s\n' "$1" >&2; exit 1; }

usage() {
  sed -n '2,16p' "$0" | sed 's/^# \?//'
  exit 0
}

for arg in "$@"; do
  case "$arg" in
    --rebuild) REBUILD=1 ;;
    --seed)    SEED=1 ;;
    -h|--help) usage ;;
    *) die "Opcao desconhecida: $arg (use --help)" ;;
  esac
done

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Comando '$1' nao encontrado no PATH"
}

port_in_use() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -tln | grep -qE ":${port}\\s"
  else
    curl -fsS "http://localhost:${port}/q/health" >/dev/null 2>&1
  fi
}

service_already_up() {
  curl -fsS "$1/q/health/ready" >/dev/null 2>&1
}

wait_for_container() {
  local name="$1"
  local attempts="${2:-60}"
  for _ in $(seq 1 "$attempts"); do
    local status
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      return 0
    fi
    sleep 2
  done
  return 1
}

wait_ready() {
  local name="$1" url="$2"
  local attempts="${3:-90}"
  for _ in $(seq 1 "$attempts"); do
    if curl -fsS "$url/q/health/ready" >/dev/null 2>&1; then
      ok "$name pronto ($url)"
      return 0
    fi
    sleep 2
  done
  echo "----- ultimas linhas de $LOG_DIR/$name.log -----"
  tail -n 50 "$LOG_DIR/$name.log" 2>/dev/null || true
  die "$name nao respondeu /q/health/ready a tempo"
}

start_service() {
  local name="$1" module="$2" port="$3"
  local jar="$ROOT_DIR/$module/target/quarkus-app/quarkus-run.jar"

  if service_already_up "http://localhost:$port"; then
    warn "$name ja esta respondendo em :$port — reutilizando"
    return 0
  fi

  if port_in_use "$port"; then
    die "Porta $port em uso, mas $name nao responde /q/health/ready. Liberte a porta ou rode ./scripts/stop.sh"
  fi

  [[ -f "$jar" ]] || die "Jar nao encontrado: $jar. Rode com --rebuild"

  nohup java -Dquarkus.profile=prod -jar "$jar" >"$LOG_DIR/$name.log" 2>&1 &
  local pid=$!
  echo "$name $pid $port" >>"$PID_FILE"
  log "Iniciando $name (pid $pid, porta $port, log $LOG_DIR/$name.log)"
}

ensure_build() {
  local need_build=$REBUILD
  for module in records-command-service records-query-service audit-service; do
    if [[ ! -f "$ROOT_DIR/$module/target/quarkus-app/quarkus-run.jar" ]]; then
      need_build=1
    fi
  done

  if (( need_build == 1 )); then
    log "Empacotando modulos (./mvnw package -DskipTests)..."
    (cd "$ROOT_DIR" && ./mvnw -q package -DskipTests)
    ok "build concluido"
  else
    ok "jars ja existem (use --rebuild para recompilar)"
  fi
}

main() {
  require_cmd docker
  require_cmd java
  require_cmd curl

  if ! docker info >/dev/null 2>&1; then
    die "Docker nao acessivel. Verifique se o daemon esta rodando e se seu usuario esta no grupo docker"
  fi

  mkdir -p "$LOG_DIR"

  if service_already_up "$COMMAND_URL" && service_already_up "$QUERY_URL" && service_already_up "$AUDIT_URL"; then
    ok "os 3 servicos ja estao no ar"
    if (( SEED == 1 )); then
      log "Rodando seed de dados de exemplo..."
      "$ROOT_DIR/scripts/seed.sh"
    fi
    cat <<EOF

Projeto ja estava no ar.

  Servicos
    command  $COMMAND_URL   health: $COMMAND_URL/q/health
    query    $QUERY_URL   health: $QUERY_URL/q/health
    audit    $AUDIT_URL   health: $AUDIT_URL/q/health

  Swagger
    $COMMAND_URL/q/swagger-ui
    $QUERY_URL/q/swagger-ui
    $AUDIT_URL/q/swagger-ui

  Infra
    Redpanda Console  http://localhost:8080
    Jaeger            http://localhost:16686

  Postman
    Importe docs/postman/prontuario-sus.postman_collection.json
    e docs/postman/prontuario-sus.postman_environment.json

  Logs: $LOG_DIR/
  Parar: ./scripts/stop.sh

EOF
    return 0
  fi

  : >"$PID_FILE"

  log "1/4 Subindo infra (docker compose)..."
  (cd "$ROOT_DIR/infra" && docker compose up -d)

  log "Aguardando containers saudaveis..."
  for name in prontuario-postgres-command prontuario-postgres-query prontuario-postgres-audit prontuario-redpanda; do
    wait_for_container "$name" || die "Container '$name' nao ficou healthy a tempo"
  done
  wait_for_container prontuario-jaeger 30 || warn "jaeger ainda nao reportou healthy (seguindo assim mesmo)"
  wait_for_container prontuario-redpanda-console 30 || warn "redpanda-console ainda nao reportou healthy (seguindo assim mesmo)"
  ok "infra no ar"

  log "2/4 Garantindo build dos servicos..."
  ensure_build

  log "3/4 Iniciando os 3 servicos..."
  start_service command records-command-service 8081
  start_service query   records-query-service   8082
  start_service audit   audit-service           8083

  log "4/4 Aguardando /q/health/ready..."
  wait_ready command "$COMMAND_URL"
  wait_ready query   "$QUERY_URL"
  wait_ready audit   "$AUDIT_URL"

  if (( SEED == 1 )); then
    log "Rodando seed de dados de exemplo..."
    "$ROOT_DIR/scripts/seed.sh"
  fi

  printf '\n\033[1;32mProjeto no ar.\033[0m\n\n'
  cat <<EOF
  Servicos
    command  $COMMAND_URL   health: $COMMAND_URL/q/health
    query    $QUERY_URL   health: $QUERY_URL/q/health
    audit    $AUDIT_URL   health: $AUDIT_URL/q/health

  Swagger
    $COMMAND_URL/q/swagger-ui
    $QUERY_URL/q/swagger-ui
    $AUDIT_URL/q/swagger-ui

  Infra
    Redpanda Console  http://localhost:8080
    Jaeger            http://localhost:16686

  Postman
    Importe docs/postman/prontuario-sus.postman_collection.json
    e docs/postman/prontuario-sus.postman_environment.json

  Logs: $LOG_DIR/
  Parar: ./scripts/stop.sh

EOF
}

main
