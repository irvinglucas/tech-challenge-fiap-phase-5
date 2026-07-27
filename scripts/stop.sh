#!/usr/bin/env bash
#
# Encerra os 3 servicos iniciados por ./scripts/start.sh.
#
# Uso:
#   ./scripts/stop.sh           # para apenas os servicos Quarkus
#   ./scripts/stop.sh --infra   # tambem derruba a infra (docker compose down)
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
PID_FILE="$RUN_DIR/services.pid"
STOP_INFRA=0

log()  { printf '\033[1;34m[stop]\033[0m %s\n' "$1"; }
ok()   { printf '  \033[1;32mOK\033[0m   %s\n' "$1"; }
warn() { printf '  \033[1;33mWARN\033[0m %s\n' "$1"; }

for arg in "$@"; do
  case "$arg" in
    --infra) STOP_INFRA=1 ;;
    -h|--help)
      sed -n '2,10p' "$0" | sed 's/^# \?//'
      exit 0
      ;;
    *) printf '\033[1;31m[stop]\033[0m Opcao desconhecida: %s\n' "$arg" >&2; exit 1 ;;
  esac
done

kill_pid() {
  local name="$1" pid="$2"
  if [[ -z "$pid" ]] || ! kill -0 "$pid" >/dev/null 2>&1; then
    warn "$name (pid ${pid:-?}) ja nao esta rodando"
    return 0
  fi
  kill "$pid" >/dev/null 2>&1 || true
  for _ in $(seq 1 20); do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      ok "$name encerrado (pid $pid)"
      return 0
    fi
    sleep 0.5
  done
  warn "$name nao encerrou a tempo — enviando SIGKILL"
  kill -9 "$pid" >/dev/null 2>&1 || true
}

stop_by_pid_file() {
  if [[ ! -f "$PID_FILE" ]]; then
    warn "Arquivo de PIDs nao encontrado ($PID_FILE)"
    return 1
  fi

  local stopped=0
  while read -r name pid port; do
    [[ -z "${name:-}" || -z "${pid:-}" ]] && continue
    kill_pid "$name" "$pid"
    stopped=1
  done <"$PID_FILE"

  rm -f "$PID_FILE"
  (( stopped == 1 ))
}

stop_by_port() {
  local name="$1" port="$2"
  local pids
  pids="$(ss -tlnp 2>/dev/null | awk -v p=":$port" '$4 ~ p {print}' | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -u || true)"
  if [[ -z "$pids" ]]; then
    # fallback: processos java com quarkus-run naquele modulo
    return 1
  fi
  for pid in $pids; do
    kill_pid "$name" "$pid"
  done
  return 0
}

main() {
  log "Encerrando servicos Quarkus..."
  if ! stop_by_pid_file; then
    warn "Tentando encerrar pelas portas 8081/8082/8083..."
    stop_by_port command 8081 || warn "nada escutando em :8081"
    stop_by_port query   8082 || warn "nada escutando em :8082"
    stop_by_port audit   8083 || warn "nada escutando em :8083"
  fi

  if (( STOP_INFRA == 1 )); then
    log "Derrubando infra (docker compose down)..."
    (cd "$ROOT_DIR/infra" && docker compose down)
    ok "infra derrubada (volumes preservados; use 'docker compose down -v' para reset)"
  else
    log "Infra Docker mantida. Para derruba-la: ./scripts/stop.sh --infra"
  fi

  ok "pronto"
}

main
