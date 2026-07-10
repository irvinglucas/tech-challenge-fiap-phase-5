# Roteiros para os vídeos de entrega (issues #19 e #20)

Estes vídeos precisam ser **gravados manualmente** (voz/tela do apresentador) — não é algo que possa ser automatizado. Este documento existe para deixar o roteiro pronto e reduzir o trabalho manual a "seguir o script e gravar a tela".

## Vídeo 1 — Pitch (issue #19, máximo 8 minutos)

Estrutura exigida pelo PDF do hackathon: introdução (1min) → solução (3min) → impacto (2min) → próximos passos (2min).

### Introdução (~1 min)

- Apresente a equipe: nome e função de cada integrante (preencher conforme a equipe real).
- Problema: "O SUS opera de forma descentralizada — um paciente atendido numa UBS e depois num hospital frequentemente tem seu histórico de alergias, diagnósticos e medicações desconhecido pela segunda unidade. Isso aumenta o risco de erro médico, causa retrabalho com exames duplicados, e dificulta a governança sobre quem acessa dados sensíveis de saúde."

### A solução (~3 min)

- "Desenvolvemos um Prontuário Eletrônico Unificado para o SUS: um MVP de back-end com 3 microsserviços que centralizam o histórico clínico do paciente entre unidades, com controle de acesso baseado em papéis e trilha de auditoria completa."
- Mostre o diagrama de arquitetura ([docs/architecture.md](architecture.md)) e explique rapidamente:
  - `records-command-service`: registra tudo que acontece com o paciente como um evento imutável (Event Sourcing) — nada é sobrescrito, então sempre é possível reconstruir o histórico exato.
  - `records-query-service`: mantém uma visão consolidada, atualizada em tempo real via eventos (Kafka), que qualquer unidade autorizada pode consultar.
  - `audit-service`: grava toda alteração e todo acesso (autorizado ou negado) de forma independente, e **detecta automaticamente** quando um profissional acessa muitos pacientes de várias unidades em pouco tempo — um indício de uso indevido de credenciais.
- Diferencial: "diferente de um CRUD tradicional, a auditoria aqui não é um recurso adicionado depois — é uma consequência estrutural de como o sistema é construído: cada evento de mudança já alimenta a trilha de auditoria."

### Impacto (~2 min)

- Para os **profissionais de saúde**: acesso imediato ao histórico do paciente vindo de qualquer unidade, reduzindo decisões clínicas tomadas sem informação completa (ex.: prescrever algo a que o paciente é alérgico).
- Para os **pacientes**: menos exames repetidos, atendimento mais rápido e seguro, especialmente em emergências onde o histórico faz diferença.
- Para a **gestão/compliance**: trilha de auditoria completa e alertas automáticos de acesso anômalo, essencial para dados sensíveis de saúde (LGPD).
- Caso de uso real: "imagine um paciente idoso, com múltiplas comorbidades, que é atendido numa UBS e depois precisa ir a um hospital — hoje, o hospital não tem esse histórico. Com nossa solução, o médico do hospital vê imediatamente as alergias e medicações em uso, registradas pela UBS."

### Próximos passos (~2 min)

- Substituir o emissor de tokens de demonstração por um Authorization Server real (Keycloak).
- Adicionar um serviço de notificações (ex.: alertar o médico responsável quando um exame crítico é anexado ao prontuário).
- Snapshots do histórico para pacientes com volume alto de eventos.
- Testes de carga para validar os limites da escalabilidade horizontal já demonstrada.

---

## Vídeo 2 — Demo do MVP (issue #20, máximo 8 minutos)

Objetivo: demonstrar o funcionamento real via Postman/Swagger. Use a [coleção Postman](postman/) já pronta ([docs/postman](postman/)) — basta importar os dois arquivos (`prontuario-sus.postman_collection.json` + `prontuario-sus.postman_environment.json`) no Postman, com os 3 serviços rodando localmente (`docker compose up -d` na pasta `infra/`, depois `./mvnw -pl <servico> -am quarkus:dev` para cada um, ou os jars empacotados).

Sugestão de gravação: tela dividida ou alternando entre o Postman e uma explicação rápida em voz do que está acontecendo.

### Roteiro passo a passo (mapeado 1:1 para a pasta correspondente na coleção Postman)

1. **(30s) Contexto**: "Vou demonstrar o fluxo completo: registrar um paciente numa unidade, tentar acessá-lo sem permissão, conceder acesso, registrar uma evolução clínica, consultar o prontuário consolidado de outra unidade, e ver tudo isso refletido na trilha de auditoria."

2. **(1 min) Pasta "0. Autenticacao"**: rode as 4 requisições de emissão de token (MEDICO, ENFERMEIRO, GESTOR, AUDITOR). Explique: "cada papel tem um conjunto de permissões diferente — isso é RBAC via JWT."

3. **(1min30) Pasta "1. Comandos" — registro e RBAC de escrita**:
   - "Registrar paciente (MEDICO)" → mostre o `201 Created` e o `patientId` gerado.
   - "Registrar evolução clínica sem acesso (ENFERMEIRO)" → mostre o `403 Forbidden`: "nem quem registrou o paciente tem acesso automático — é necessário um gestor conceder esse acesso explicitamente."
   - "Conceder acesso ao MEDICO (GESTOR)" → `201 Created`.
   - "Registrar evolução clínica (MEDICO, autorizado)" → agora `201 Created`. Rode também "Adicionar diagnóstico" e "Emitir prescrição" para ter dados variados na consulta seguinte.

4. **(2min) Pasta "2. Consultas" — leitura consolidada entre unidades**:
   - "Consultar prontuário consolidado sem acesso (ENFERMEIRO)" → `403 Forbidden`, e explique que esse evento (`AccessDenied`) já está sendo enviado para a auditoria.
   - "Consultar prontuário consolidado (MEDICO, autorizado)" → `200 OK`, mostre a timeline com todos os eventos registrados (`PatientRegistered`, `ConsultationRecorded`, `DiagnosisAdded`, `PrescriptionIssued`). Destaque: "essa é a visão *consolidada* — viria de qualquer unidade que tivesse acesso, mesmo que os atendimentos tenham ocorrido em unidades diferentes."
   - "Consultar resumo do paciente" → mostre o resumo agregado (alergias, diagnósticos ativos, últimas prescrições).

5. **(1min30) Pasta "3. Auditoria" — trilha completa e governança**:
   - "Trilha de auditoria completa do paciente" → mostre TODOS os eventos (incluindo o acesso negado do enfermeiro).
   - "Quem acessou o prontuário do paciente" → filtro apenas de acessos/negações.
   - "Listar alertas de acesso anômalo" → explique a funcionalidade (mesmo que a lista esteja vazia nesta demo pontual — para gerar um alerta real, rode `./scripts/seed.sh`, que já provoca esse cenário automaticamente).

6. **(30s) Fechamento**: "Toda essa comunicação entre os 3 serviços acontece de forma assíncrona via Kafka — o comando de registrar o paciente já retornou antes mesmo da consulta e da auditoria serem atualizadas, o que garante que os serviços escalem e falhem de forma independente." (Opcional, se houver tempo: mostre o Redpanda Console em `http://localhost:8080` com o tópico `patient-record-events` recebendo as mensagens.)

### Se o tempo permitir (não obrigatório)

- Mostrar `./scripts/seed.sh` rodando e o alerta de acesso anômalo sendo gerado automaticamente (evidência real em [docs/scalability-demo.md](scalability-demo.md) para escalabilidade, mas o seed serve bem para a anomalia de acesso).
- Mostrar o Jaeger (`http://localhost:16686`) com o trace de uma requisição atravessando os 3 serviços.
