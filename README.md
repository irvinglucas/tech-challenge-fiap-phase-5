# Prontuário Eletrônico Unificado SUS — Hackathon FIAP Fase 5

MVP de back-end para centralizar o histórico clínico de um paciente entre unidades do SUS, com acesso seguro (RBAC) e trilha de auditoria completa (Event Sourcing + CQRS), demonstrando os pilares de microsserviços da Fase 5: mensageria assíncrona, escalabilidade, alta disponibilidade e observabilidade.

- Contexto e escopo completo do problema: [Hackaton-9ADJT.pdf](Hackaton-9ADJT.pdf)
- Desenho de domínio (event storming): [docs/event-storming.md](docs/event-storming.md)
- **Arquitetura, diagrama e descrição dos 3 serviços: [docs/architecture.md](docs/architecture.md)**

## Estrutura do repositório

```
tech-challenge-fiap-phase-5/
  common/                     # eventos de dominio compartilhados + constantes de RBAC
  records-command-service/    # write side (CQRS): comandos + event sourcing
  records-query-service/      # read side (CQRS): projecoes de leitura
  audit-service/              # trilha de auditoria e governanca
  infra/                      # docker-compose com Postgres x3, Redpanda e Jaeger
  scripts/                    # teste de integracao ponta a ponta e seed de dados
  docs/
    event-storming.md         # eventos, comandos, politicas e diagramas do dominio
    architecture.md            # arquitetura, diagrama de componentes e atributos de qualidade
    postman/                   # colecao Postman + ambiente local
```

Cada serviço é uma aplicação [Quarkus](https://quarkus.io/) independente (Java 21), com seu próprio banco de dados (*database per service*), publicando na sua própria porta HTTP local:

| Serviço | Porta | Responsabilidade |
|---|---|---|
| `records-command-service` | 8081 | Comandos (escrita) do prontuário, via Event Sourcing |
| `records-query-service` | 8082 | Consultas (leitura), projeções CQRS atualizadas via Kafka |
| `audit-service` | 8083 | Trilha de auditoria e detecção de acesso anômalo |

Mais detalhes de cada serviço e do fluxo de eventos entre eles em [docs/architecture.md](docs/architecture.md).

## Quickstart

```bash
# 1. Sobe a infra local (Postgres x3, Redpanda, Jaeger)
cd infra && docker compose up -d && cd ..

# 2. Compila e testa os 4 modulos
./mvnw clean install

# 3. Roda cada servico (em 3 terminais separados)
./mvnw -pl records-command-service -am quarkus:dev
./mvnw -pl records-query-service -am quarkus:dev
./mvnw -pl audit-service -am quarkus:dev

# 4. (opcional) Popula dados de exemplo para explorar/demonstrar
./scripts/seed.sh
```

Com os 3 serviços de pé, explore via Swagger UI (`http://localhost:8081/q/swagger-ui`, troque a porta) ou importe a [coleção Postman](docs/postman).

## Infra local

3 bancos Postgres (um por serviço) + broker de eventos compatível com Kafka (Redpanda) + Jaeger para tracing distribuído:

```bash
cd infra
docker compose up -d
```

Detalhes, portas e credenciais em [infra/README.md](infra/README.md).

## Build

```bash
./mvnw clean install
```

Compila os 4 módulos e roda os testes de cada serviço (unitários + integração, com Quarkus Dev Services subindo um Postgres/Kafka efêmero por execução).

## Rodando um serviço em modo dev

```bash
./mvnw -pl records-command-service -am quarkus:dev
```

Endpoints disponíveis em cada serviço (troque a porta conforme a tabela acima):

- `GET /info` — nome do serviço e status (verificação manual do scaffold)
- `GET /q/health` — health check (liveness/readiness)
- `GET /q/metrics` — métricas no formato Prometheus
- `GET /q/swagger-ui` — Swagger UI

## Testes e demo

- **Testes automatizados de cada serviço**: `./mvnw clean install` (unitários + integração com Quarkus Dev Services, um Postgres/Kafka efêmero por execução).
- **Teste de integração ponta a ponta dos 3 serviços**: com a infra local e os 3 serviços buildados (`./mvnw clean package -DskipTests`), rode `./scripts/integration-test.sh`. Ele sobe os 3 serviços de verdade e reproduz o fluxo principal via HTTP (registrar paciente → negar acesso → conceder acesso → registrar evolução clínica → consultar prontuário consolidado → consultar auditoria).
- **Coleção Postman**: [docs/postman/prontuario-sus.postman_collection.json](docs/postman/prontuario-sus.postman_collection.json) + [ambiente local](docs/postman/prontuario-sus.postman_environment.json), cobrindo os endpoints dos 3 serviços na ordem do fluxo principal. Importe os dois arquivos no Postman (ou rode com `npx newman run docs/postman/prontuario-sus.postman_collection.json -e docs/postman/prontuario-sus.postman_environment.json`) com os 3 serviços de pé.
- **Seed de dados para demo/vídeo**: `./scripts/seed.sh` cria pacientes de exemplo em unidades diferentes, demonstra um acesso negado e gera um alerta de acesso anômalo (mesmo profissional acessando pacientes de 3 unidades em poucos segundos), imprimindo os IDs e tokens gerados para uso manual.
- **Escalabilidade horizontal**: `./scripts/scale-demo.sh 3` builda e sobe 3 réplicas stateless do `records-query-service`, provando que o Kafka divide as partições do tópico entre elas e as redistribui automaticamente se uma réplica cair. Evidência completa em [docs/scalability-demo.md](docs/scalability-demo.md).

## Segurança e papéis (RBAC)

Autenticação via JWT (RSA), com a identidade do profissional sempre extraída do token (`sub`) — nunca do corpo da requisição. Papéis (ver [docs/event-storming.md](docs/event-storming.md) para o mapeamento completo de comando → papel):

| Papel | Pode |
|---|---|
| `MEDICO` | Registrar paciente, evolução clínica, diagnóstico, prescrição, alergia, resultado de exame |
| `ENFERMEIRO` | Registrar paciente, evolução clínica, alergia, resultado de exame |
| `GESTOR` | Conceder/revogar acesso de um profissional a um paciente |
| `AUDITOR` | Consultar a trilha de auditoria e os alertas de acesso anômalo |

Em ambiente local/hackathon, `POST /dev/tokens` (no `records-command-service`) emite tokens de demonstração assinados com a chave privada de demo — **não existe em um cenário de produção real**, onde um Authorization Server (ex.: Keycloak) emitiria os tokens.

## Observabilidade

- **Métricas**: Prometheus em `/q/metrics` nos 3 serviços.
- **Health checks**: `/q/health`, `/q/health/live`, `/q/health/ready`.
- **Logs**: estruturados em JSON, com `correlationId` propagado entre a requisição HTTP e o processamento assíncrono via Kafka.
- **Tracing distribuído**: OpenTelemetry + [Jaeger](http://localhost:16686), cobrindo chamadas HTTP e consumo Kafka entre os 3 serviços.
- **Redpanda Console**: `http://localhost:8080` — inspeciona tópicos, mensagens e consumer groups (incluindo a DLQ `patient-record-events-dlq`).

## Materiais de entrega do hackathon

- **Relatório do projeto**: [docs/relatorio-final.md](docs/relatorio-final.md) — cobre todas as seções exigidas pelo PDF do hackathon (resumo executivo, problema, solução, processo de desenvolvimento, detalhes técnicos, links úteis, aprendizados e próximos passos). Falta apenas preencher a equipe e exportar para `.txt`/`.doc` antes de publicar.
- **Roteiros para os vídeos de pitch e de demo**: [docs/video-scripts.md](docs/video-scripts.md) — a gravação em si é manual, mas o roteiro passo a passo (mapeado para a coleção Postman) já está pronto.

## Status do projeto

Acompanhe o progresso no [Project "FIAP Tech Challenge 5"](https://github.com/users/irvinglucas/projects/2).
