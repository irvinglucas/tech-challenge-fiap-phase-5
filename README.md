# Prontuário Eletrônico Unificado SUS — Hackathon FIAP Fase 5

MVP de back-end para centralizar o histórico clínico de um paciente entre unidades do SUS, com acesso seguro (RBAC) e trilha de auditoria completa (Event Sourcing + CQRS).

Contexto e escopo completo do problema: [Hackaton-9ADJT.pdf](Hackaton-9ADJT.pdf). Desenho de domínio: [docs/event-storming.md](docs/event-storming.md).

## Estrutura do repositório

```
tech-challenge-fiap-phase-5/
  common/                     # eventos de dominio compartilhados + constantes de RBAC
  records-command-service/    # write side (CQRS): comandos + event sourcing
  records-query-service/      # read side (CQRS): projecoes de leitura
  audit-service/              # trilha de auditoria e governanca
  docs/
    event-storming.md         # eventos, comandos, politicas e diagramas do dominio
```

Cada serviço é uma aplicação [Quarkus](https://quarkus.io/) independente (Java 21), publicando na sua própria porta HTTP local:

| Serviço | Porta |
|---|---|
| records-command-service | 8081 |
| records-query-service | 8082 |
| audit-service | 8083 |

## Infra local

3 bancos Postgres (um por serviço) + broker de eventos compatível com Kafka (Redpanda):

```bash
cd infra
docker compose up -d
```

Detalhes, portas e credenciais em [infra/README.md](infra/README.md).

## Build

```bash
./mvnw clean install
```

Compila os 4 módulos e roda os testes de cada serviço.

## Rodando um serviço em modo dev

```bash
./mvnw -pl records-command-service -am quarkus:dev
```

Endpoints disponíveis em cada serviço (troque a porta conforme a tabela acima):

- `GET /info` — nome do serviço e status (verificação manual do scaffold)
- `GET /q/health` — health check (liveness/readiness)
- `GET /q/swagger-ui` — Swagger UI

## Testes e demo

- **Testes automatizados de cada serviço**: `./mvnw clean install` (unitários + integração com Quarkus Dev Services, um Postgres/Kafka efêmero por execução).
- **Teste de integração ponta a ponta dos 3 serviços**: com a infra local e os 3 serviços buildados (`./mvnw clean package -DskipTests`), rode `./scripts/integration-test.sh`. Ele sobe os 3 serviços de verdade e reproduz o fluxo principal via HTTP (registrar paciente → negar acesso → conceder acesso → registrar evolução clínica → consultar prontuário consolidado → consultar auditoria).
- **Coleção Postman**: [docs/postman/prontuario-sus.postman_collection.json](docs/postman/prontuario-sus.postman_collection.json) + [ambiente local](docs/postman/prontuario-sus.postman_environment.json), cobrindo os endpoints dos 3 serviços na ordem do fluxo principal. Importe os dois arquivos no Postman (ou rode com `npx newman run docs/postman/prontuario-sus.postman_collection.json -e docs/postman/prontuario-sus.postman_environment.json`) com os 3 serviços de pé.
- **Seed de dados para demo/vídeo**: `./scripts/seed.sh` cria pacientes de exemplo em unidades diferentes, demonstra um acesso negado e gera um alerta de acesso anômalo (mesmo profissional acessando pacientes de 3 unidades em poucos segundos), imprimindo os IDs e tokens gerados para uso manual.

## Status do projeto

Acompanhe o progresso no [Project "FIAP Tech Challenge 5"](https://github.com/users/irvinglucas/projects/2).
