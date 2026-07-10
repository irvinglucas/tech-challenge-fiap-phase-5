#!/usr/bin/env bash
#
# Demo de escalabilidade horizontal do records-query-service (issue #15):
# builda a imagem do servico, garante que o topico patient-record-events
# tenha mais de uma particao (para ter o que dividir entre replicas) e sobe
# 3 instancias via `docker compose --profile scale-demo up --scale`,
# mostrando que:
#
#   1. As 3 replicas sobem com sucesso a partir da MESMA imagem (stateless:
#      nenhuma delas guarda estado local, tudo vive no Postgres/Kafka
#      compartilhados).
#   2. O Kafka (Redpanda) divide as particoes do topico entre as 3 replicas
#      do mesmo consumer group (records-query-service) automaticamente.
#
# Uso:
#   ./scripts/scale-demo.sh [N]     # N = numero de replicas (default 3)
#
# Pre-requisito: infra do infra/docker-compose.yml no ar (docker compose up -d).
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPLICAS="${1:-3}"
TOPIC="patient-record-events"
GROUP="records-query-service"

log() { printf '\033[1;34m[scale-demo]\033[0m %s\n' "$1"; }

main() {
  cd "$ROOT_DIR/infra"

  log "1) Garantindo que a infra base esteja de pe..."
  docker compose up -d postgres-query redpanda

  log "2) Empacotando o records-query-service (jar)..."
  (cd "$ROOT_DIR" && ./mvnw -q -pl common,records-query-service -am package -DskipTests)

  log "3) Garantindo que o topico '$TOPIC' tenha pelo menos $REPLICAS particoes..."
  current_partitions="$(docker exec prontuario-redpanda rpk topic describe "$TOPIC" 2>/dev/null | awk '/^PARTITIONS/ {print $2}')"
  if [[ -z "$current_partitions" ]]; then
    docker exec prontuario-redpanda rpk topic create "$TOPIC" --partitions "$REPLICAS"
  elif (( current_partitions < REPLICAS )); then
    docker exec prontuario-redpanda rpk topic add-partitions "$TOPIC" --num "$((REPLICAS - current_partitions))"
  fi
  docker exec prontuario-redpanda rpk topic describe "$TOPIC" | grep -E 'PARTITIONS|NAME'

  log "4) Buildando a imagem do records-query-service..."
  docker compose --profile scale-demo build records-query-service

  log "5) Subindo $REPLICAS replicas (docker compose --profile scale-demo up --scale)..."
  docker compose --profile scale-demo up -d --scale "records-query-service=$REPLICAS" records-query-service

  log "Aguardando as replicas ficarem prontas..."
  sleep 15

  log "=== docker compose ps (evidencia: $REPLICAS containers da MESMA imagem) ==="
  docker compose --profile scale-demo ps records-query-service

  log "=== Divisao das particoes do consumer group '$GROUP' entre as replicas (rpk group describe) ==="
  docker exec prontuario-redpanda rpk group describe "$GROUP"

  log "=== Ultimas linhas de log de cada replica (evidencia: cada uma iniciou e assumiu particoes) ==="
  for cid in $(docker compose --profile scale-demo ps -q records-query-service); do
    echo "--- container $cid ---"
    docker logs "$cid" 2>&1 | grep -Ei "started in|partitions assigned|rebalance" | tail -5
  done

  echo
  log "Demo concluida. Para derrubar as replicas: docker compose --profile scale-demo down records-query-service"
}

main
