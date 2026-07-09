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

## Status do projeto

Acompanhe o progresso no [Project "FIAP Tech Challenge 5"](https://github.com/users/irvinglucas/projects/2).
