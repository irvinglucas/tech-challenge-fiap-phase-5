# Infra local

Sobe a infraestrutura local usada pelos 3 serviços do MVP: 3 bancos Postgres (um por serviço, "database per service"), um broker de eventos compatível com Kafka (Redpanda) com uma console web para inspecionar os tópicos, e o Jaeger para inspecionar o tracing distribuído ponta a ponta (issue #13).

## Subir tudo

```bash
cd infra
cp .env.example .env   # opcional: só se quiser mudar portas/credenciais
docker compose up -d
```

Acompanhar os logs / verificar que tudo ficou saudável:

```bash
docker compose ps
docker compose logs -f
```

## O que sobe

| Serviço | Container | Porta no host | Uso |
|---|---|---|---|
| `postgres-command` | `prontuario-postgres-command` | `5432` | event store do `records-command-service` |
| `postgres-query` | `prontuario-postgres-query` | `5433` | read models do `records-query-service` |
| `postgres-audit` | `prontuario-postgres-audit` | `5434` | trilha de auditoria do `audit-service` |
| `redpanda` | `prontuario-redpanda` | `19092` (Kafka), `18081` (Schema Registry), `18082` (Pandaproxy/REST), `19644` (Admin API) | broker de eventos |
| `redpanda-console` | `prontuario-redpanda-console` | `8080` | UI web para ver tópicos/mensagens ([http://localhost:8080](http://localhost:8080)) |
| `jaeger` | `prontuario-jaeger` | `16686` (UI), `4317` (OTLP gRPC), `4318` (OTLP HTTP) | tracing distribuído ([http://localhost:16686](http://localhost:16686)) |

Credenciais e portas padrão (usadas se você não criar um `.env`) estão documentadas em [.env.example](.env.example).

Cada serviço Quarkus (`records-command-service`, `records-query-service`, `audit-service`) usa essa infra em modo dev/prod: Postgres correspondente, Kafka em `localhost:19092` e spans exportados via OTLP para o Jaeger em `localhost:4317`. Em `%dev`/`%prod` um mesmo `X-Correlation-Id` (HTTP) ou header Kafka `correlation_id` acompanha uma operação pelos 3 serviços, aparecendo nos logs JSON estruturados (`quarkus-logging-json`, campo `correlationId` no topo do JSON) e como `traceId`/`spanId` do OpenTelemetry — no Jaeger, buscar pelo `service.name` de qualquer um dos 3 serviços mostra a trace completa (HTTP → publicação no Kafka → consumo pelos outros dois serviços), já que o conector Kafka do SmallRye Reactive Messaging propaga o contexto de trace automaticamente via header `traceparent`.

## Criar o tópico de eventos manualmente (opcional)

O `records-command-service` cria o tópico automaticamente ao publicar o primeiro evento, mas se quiser criá-lo antes:

```bash
docker compose exec redpanda rpk topic create patient-record-events patient-record-events-dlq
docker compose exec redpanda rpk topic list
```

## Verificar um banco manualmente

```bash
docker compose exec postgres-command psql -U command_service -d records_command
```

## Derrubar tudo

```bash
docker compose down          # mantém os volumes (dados persistem)
docker compose down -v       # remove tambem os volumes (reset completo)
```
