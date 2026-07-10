# Relatório do Projeto — Prontuário Eletrônico Unificado SUS

Hackathon FIAP — Pós-graduação em Arquitetura e Desenvolvimento Java, Fase 5.

> **Antes de entregar**: preencha o nome/função de cada integrante da equipe na seção "Equipe" abaixo, gere um link público de acesso ao repositório/drive com os materiais (vídeos + este relatório) e exporte este documento para `.txt` ou `.doc`, conforme exigido na sessão "Entrega" do PDF do hackathon.

## Equipe

| Nome | Função |
|---|---|
| _(preencher)_ | _(preencher)_ |

## 1. Resumo executivo

O **Prontuário Eletrônico Unificado SUS** é um MVP de back-end que centraliza o histórico clínico de um paciente entre diferentes unidades de saúde do SUS, permitindo que profissionais autorizados (médicos, enfermeiros) consultem dados clínicos atualizados de qualquer unidade, de forma segura e com controle de acesso baseado em papéis (RBAC). Toda alteração e todo acesso ao prontuário — autorizado ou negado — é registrado numa trilha de auditoria imutável, incluindo a detecção automática de padrões de acesso potencialmente anômalos (um profissional acessando muitos pacientes de várias unidades em pouco tempo).

O objetivo é reduzir a fragmentação de informação clínica entre unidades (hoje cada unidade frequentemente mantém seu próprio prontuário isolado), aumentando a qualidade e a velocidade do atendimento — um médico numa UBS pode ver imediatamente o histórico de alergias, diagnósticos e prescrições registrados num hospital diferente — sem abrir mão de governança e rastreabilidade sobre quem acessa dados sensíveis de saúde.

## 2. Problema identificado

O SUS opera de forma descentralizada, com milhares de unidades de saúde (UBS, hospitais, prontos-socorros) frequentemente utilizando sistemas de prontuário isolados entre si. Isso gera problemas concretos:

- **Perda de histórico clínico entre unidades**: um paciente atendido numa UBS e depois num hospital pode ter seu histórico de alergias, diagnósticos e medicações desconhecido pela segunda unidade, aumentando o risco de erros médicos (ex.: prescrever um medicamento ao qual o paciente é alérgico).
- **Retrabalho e exames duplicados**: sem acesso ao histórico, exames e anamneses são repetidos, sobrecarregando ainda mais um sistema já sob pressão de demanda.
- **Falta de rastreabilidade e governança**: em muitos sistemas legados não há uma trilha clara de quem acessou o quê, quando e de onde — dificultando auditorias de conformidade (ex.: LGPD, já que dados de saúde são dados sensíveis) e a detecção de uso indevido de credenciais.

Resolver esse problema tem impacto direto na qualidade e segurança do atendimento, especialmente para pacientes com múltiplas comorbidades que circulam entre diferentes níveis de atenção (UBS → especialista → hospital), e no compliance/governança de dados sensíveis de saúde.

## 3. Descrição da solução

A solução é um conjunto de 3 microsserviços independentes, desenhados com **CQRS** (Command Query Responsibility Segregation) e **Event Sourcing**:

- **`records-command-service`** — recebe os comandos que alteram o prontuário (registrar paciente, registrar evolução clínica, diagnóstico, prescrição, alergia, resultado de exame, conceder/revogar acesso de um profissional a um paciente). Cada alteração é validada contra as regras de negócio e persistida como um evento imutável — o *event store* é a fonte da verdade de todo o domínio.
- **`records-query-service`** — mantém projeções de leitura otimizadas para consulta (prontuário consolidado, resumo do paciente, listagem por unidade), atualizadas de forma assíncrona a partir dos eventos publicados pelo serviço de comando. É aqui que a query "ver prontuário consolidado" verifica se o profissional tem acesso concedido àquele paciente.
- **`audit-service`** — consome o mesmo fluxo de eventos para manter uma trilha de auditoria append-only (todo acesso, autorizado ou negado, e toda alteração), além de detectar automaticamente padrões de acesso anômalo (ex.: um profissional acessando pacientes de 3 unidades diferentes em poucos minutos), gerando alertas para o time de compliance.

Os 3 serviços nunca se comunicam via chamadas HTTP síncronas entre si — toda comunicação é assíncrona, via eventos publicados num broker compatível com Kafka (Redpanda). Isso garante que cada serviço escale e falhe de forma independente, e que a trilha de auditoria continue completa mesmo que o serviço de leitura esteja temporariamente indisponível.

**Diferenciais em relação a um CRUD tradicional de prontuário:**

- **Trilha de auditoria imutável por construção**: como cada mudança é um evento, e a auditoria é alimentada pelo mesmo fluxo de eventos (não por triggers de banco ou logs de aplicação que podem ser adulterados), a rastreabilidade é uma propriedade estrutural do sistema, não um recurso adicionado depois.
- **Detecção de acesso anômalo nativa**: o `audit-service` já emite alertas automáticos de possível uso indevido de credenciais, sem depender de uma ferramenta de SIEM externa.
- **Resiliência de ponta a ponta**: retries, circuit breaker e fila de mensagens mortas (DLQ) garantem que uma falha transitória num serviço downstream não trave o fluxo, nem perca eventos.
- **Escalabilidade horizontal demonstrada**: o lado de leitura escala adicionando réplicas stateless, com o Kafka dividindo automaticamente a carga entre elas (ver [docs/scalability-demo.md](scalability-demo.md)).

## 4. Processo de desenvolvimento

O trabalho começou com uma sessão leve de **event storming** ([docs/event-storming.md](event-storming.md)) para mapear os atores (recepção, médico, enfermeiro, gestor de unidade, auditor), o agregado central (`PatientRecord`), os eventos de domínio, os comandos que os geram e as políticas (reações automáticas a eventos) — esse desenho guiou diretamente o desenho técnico dos 3 serviços e evitou retrabalho de modelagem durante a implementação.

A partir do desenho de domínio, o trabalho foi quebrado em issues incrementais num [board do GitHub Projects](https://github.com/users/irvinglucas/projects/2), organizadas em milestones:

1. **M1 — Fundacão**: event storming, scaffold do repositório multi-módulo e infraestrutura local (Docker Compose com Postgres x3 e Redpanda).
2. **M2 — Write side**: `records-command-service` com o agregado `PatientRecord`, event store com controle de concorrência otimista, e os endpoints de comando.
3. **M3 — Read side**: `records-query-service` consumindo os eventos via Kafka e mantendo as projeções de leitura, com o controle de acesso da query `ViewPatientRecord`.
4. **M4 — Segurança e auditoria**: JWT + RBAC nos 3 serviços, `audit-service` com a trilha de auditoria e a detecção de acesso anômalo.
5. **M5 — Qualidade não funcional**: métricas Prometheus, health checks, logs estruturados com correlation id, tracing distribuído (OpenTelemetry + Jaeger), e resiliência (retry, circuit breaker, DLQ) nos consumidores Kafka.
6. **M6 — Fechamento**: testes de integração ponta a ponta, coleção Postman + script de seed, documentação de arquitetura, demo de escalabilidade horizontal, e os materiais de entrega (vídeos e este relatório).

Cada issue foi implementada, testada (testes unitários e de integração via Quarkus Dev Services, com um Postgres/Kafka efêmero por execução) e commitada individualmente, permitindo validar o sistema incrementalmente em vez de integrar tudo apenas no final.

## 5. Detalhes técnicos

- **Linguagem/Framework**: Java 21 + [Quarkus](https://quarkus.io/) 3.37
- **Persistência**: PostgreSQL 16, um banco por serviço (*database per service*), com Flyway para migrações versionadas
- **Mensageria**: Redpanda (compatível com Kafka), via SmallRye Reactive Messaging
- **Segurança**: JWT assinado com RSA (SmallRye JWT) + RBAC (`@RolesAllowed`) — papéis `MEDICO`, `ENFERMEIRO`, `GESTOR`, `AUDITOR`
- **Resiliência**: SmallRye Fault Tolerance (retry + circuit breaker) nos consumidores Kafka, com fila de mensagens mortas (DLQ) dedicada
- **Observabilidade**: Micrometer + Prometheus (métricas), health checks (liveness/readiness), logs estruturados em JSON com correlation id propagado entre HTTP e Kafka, OpenTelemetry + Jaeger (tracing distribuído)
- **Build**: Maven multi-módulo (`common`, `records-command-service`, `records-query-service`, `audit-service`)
- **Infraestrutura local**: Docker Compose (3x Postgres, Redpanda + console web, Jaeger)
- **Testes**: JUnit 5 + Quarkus Dev Services (Testcontainers) para testes de integração isolados por execução; script de integração ponta a ponta (`scripts/integration-test.sh`) exercitando o fluxo completo entre os 3 serviços reais

**Arquitetura**: diagrama de componentes completo, descrição de cada serviço e dos atributos de qualidade (segurança, auditabilidade, observabilidade, resiliência, consistência eventual, escalabilidade) em [docs/architecture.md](architecture.md).

## 6. Links úteis

- **Repositório de código**: https://github.com/irvinglucas/tech-challenge-fiap-phase-5
- **Board de acompanhamento (GitHub Projects)**: https://github.com/users/irvinglucas/projects/2
- **Event storming do domínio**: [docs/event-storming.md](event-storming.md)
- **Arquitetura e diagrama de componentes**: [docs/architecture.md](architecture.md)
- **Evidência da demo de escalabilidade horizontal**: [docs/scalability-demo.md](scalability-demo.md)
- **Coleção Postman + ambiente local**: [docs/postman](postman/)
- **README (setup e quickstart)**: [README.md](../README.md)
- _(preencher)_ Link público do drive com os vídeos do pitch e da demo do MVP

## 7. Aprendizados e próximos passos

### Aprendizados

- **CQRS + Event Sourcing** exigem projetar desde o início para consistência eventual: a UI/cliente precisa tolerar um pequeno delay entre um comando e sua reflexão nas consultas — isso ficou muito visível nos testes automatizados, que precisaram de retries/polling explícitos em vez de assumir consistência imediata (ver `scripts/integration-test.sh`).
- Separar o **RBAC de escrita** (no agregado, via `AccessGranted`/`AccessRevoked`) do **RBAC de leitura** (na projeção `pacientes_por_unidade`) tornou explícita uma regra de negócio importante: nem quem registra um paciente tem acesso automático ao seu prontuário — é necessário um `GESTOR` conceder esse acesso explicitamente, o que reforça o princípio de menor privilégio.
- **Resiliência tem efeitos colaterais em testes**: um circuit breaker configurado de forma agressiva (poucas chamadas para abrir o circuito) tornou os testes automatizados instáveis, porque um único evento malformado já era suficiente para abrir o circuito e afetar os testes seguintes na mesma suíte. Ajustar os limiares para refletir um padrão *sustentado* de falha (não uma falha isolada) resolveu o problema e é, na prática, o comportamento correto também em produção.
- **Escalabilidade horizontal do lado de leitura é "de graça"** quando o design já é stateless e baseado em consumer groups do Kafka — não foi necessário nenhum código adicional para dividir a carga entre réplicas, apenas garantir partições suficientes no tópico.

### Próximos passos

- Substituir o emissor de tokens de demonstração (`/dev/tokens`) por um Authorization Server real (ex.: Keycloak), incluindo um cadastro de profissionais e unidades (hoje `unitId`/`professionalId` são apenas strings informadas pelo cliente).
- Adicionar um serviço de notificações (ex.: alertar o médico responsável quando um exame crítico é anexado ao prontuário de um paciente sob seus cuidados), demonstrando a extensibilidade do modelo de eventos sem alterar o `records-command-service`.
- Persistir *snapshots* do agregado `PatientRecord` para pacientes com histórico muito extenso, evitando que o replay de todos os eventos desde o registro fique lento com o tempo.
- Expor as projeções de leitura também via GraphQL, para permitir que um futuro front-end busque exatamente os campos necessários por tela.
- Adicionar testes de carga (ex.: k6 ou Gatling) para validar os limites reais de escalabilidade horizontal demonstrada em [docs/scalability-demo.md](scalability-demo.md).
