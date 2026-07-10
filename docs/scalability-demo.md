# Demo de escalabilidade horizontal — `records-query-service`

Referente à issue [#15 - Demo de escalabilidade horizontal](https://github.com/irvinglucas/tech-challenge-fiap-phase-5/issues/15).

## O que está sendo demonstrado

O `records-query-service` (lado de leitura do CQRS) é **stateless**: todo estado vive no Postgres (`records_query`) e no Kafka (posição de consumo do tópico `patient-record-events`) — nenhum dado fica guardado em memória ou em disco local do container. Isso significa que:

1. Múltiplas réplicas idênticas podem rodar simultaneamente, cada uma processando parte do tráfego.
2. O Kafka (Redpanda) divide automaticamente as partições do tópico entre as réplicas do mesmo *consumer group* (`records-query-service`) — cada réplica processa um subconjunto dos eventos, sem coordenação manual.
3. Se uma réplica cai, as partições que ela segurava são **automaticamente redistribuídas** entre as réplicas restantes (rebalance), sem perda de eventos (o offset consumido fica salvo no Kafka, não na réplica).

## Como reproduzir

Pré-requisito: infra local no ar (`cd infra && docker compose up -d`).

```bash
./scripts/scale-demo.sh 3
```

O script (ver [scripts/scale-demo.sh](../scripts/scale-demo.sh)):

1. Empacota o `records-query-service` (`./mvnw package -DskipTests`).
2. Garante que o tópico `patient-record-events` tenha pelo menos 3 partições (por padrão o Redpanda cria com 1 partição — sem partições suficientes não há o que dividir entre réplicas).
3. Builda a imagem Docker do serviço ([Dockerfile.jvm](../records-query-service/src/main/docker/Dockerfile.jvm)).
4. Sobe **3 réplicas da mesma imagem** via `docker compose --profile scale-demo up --scale records-query-service=3` (o serviço fica atrás de um profile dedicado — não roda com `docker compose up -d` normal, para não interferir no fluxo de desenvolvimento via `quarkus:dev`).
5. Imprime a divisão de partições do consumer group (`rpk group describe`) e o log de inicialização de cada réplica.

## Evidência coletada (execução real, 2026-07-10)

### 1. Três réplicas da mesma imagem, todas saudáveis

```
NAME                                     IMAGE                                  COMMAND                  SERVICE                 STATUS
prontuario-sus-records-query-service-1   prontuario-sus-records-query-service   "java -jar /work/qua…"   records-query-service   Up 15 seconds
prontuario-sus-records-query-service-2   prontuario-sus-records-query-service   "java -jar /work/qua…"   records-query-service   Up 15 seconds
prontuario-sus-records-query-service-3   prontuario-sus-records-query-service   "java -jar /work/qua…"   records-query-service   Up 15 seconds
```

Log de inicialização de cada uma (mesma imagem, hostnames de container diferentes):

```
{"hostName":"f0f8c10dc7c6", "message":"records-query-service 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.37.2) started in 3.398s. Listening on: http://0.0.0.0:8082", ...}
{"hostName":"aa451cac23ee", "message":"records-query-service 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.37.2) started in 3.651s. Listening on: http://0.0.0.0:8082", ...}
{"hostName":"448eb3ab3aa6", "message":"records-query-service 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.37.2) started in 3.546s. Listening on: http://0.0.0.0:8082", ...}
```

### 2. Kafka divide as 3 partições entre as 3 réplicas (1 partição por réplica)

```
$ docker exec prontuario-redpanda rpk group describe records-query-service

GROUP                  records-query-service
STATE                  Stable
BALANCER               range
MEMBERS                3
TOTAL-LAG              0

TOPIC                  PARTITION  CURRENT-OFFSET  MEMBER-ID (truncado)  HOST
patient-record-events  0          41              ...5b45c6c5...        172.19.0.10
patient-record-events  1          -               ...ab82236b...        172.19.0.9
patient-record-events  2          -               ...e2ed4c93...        172.19.0.8
```

Cada uma das 3 réplicas (hosts `172.19.0.8`, `.9`, `.10`) ficou responsável por exatamente 1 das 3 partições — a carga de processamento de eventos se divide automaticamente entre elas, sem qualquer configuração manual de particionamento no código da aplicação.

### 3. Ao derrubar uma réplica, o Kafka redistribui as partições dela para as sobreviventes (tolerância a falhas)

```
$ docker stop prontuario-sus-records-query-service-1   # simula falha de uma replica

$ docker exec prontuario-redpanda rpk group describe records-query-service

GROUP                  records-query-service
STATE                  Stable
MEMBERS                2          # <- caiu de 3 para 2
TOTAL-LAG              0          # <- nenhum evento perdido

TOPIC                  PARTITION  CURRENT-OFFSET  MEMBER-ID (truncado)  HOST
patient-record-events  0          41              ...ab82236b...        172.19.0.9   # assumiu a particao 0
patient-record-events  1          -               ...ab82236b...        172.19.0.9   # continua com a particao 1
patient-record-events  2          -               ...e2ed4c93...        172.19.0.8   # sem mudanca
```

O host `172.19.0.9` (que antes só tinha a partição 1) automaticamente assumiu também a partição 0, que pertencia à réplica derrubada — sem intervenção manual e sem perda de eventos (`TOTAL-LAG` continua `0`). Isso confirma tanto a **escalabilidade horizontal** (adicionar réplicas divide a carga) quanto a **alta disponibilidade** (perder uma réplica não interrompe o processamento, o Kafka rebalanceia automaticamente).

## Por que não há um load balancer HTTP na demo

O objetivo desta demo é provar a escalabilidade do **processamento assíncrono de eventos** (a parte que efetivamente precisa escalar sob carga, já que é ela quem mantém as projeções de leitura atualizadas). As réplicas do `records-query-service` na demo não expõem porta HTTP para o host de propósito (`docker compose --profile scale-demo`, sem `ports:`) — em um ambiente real, um load balancer (ou o próprio orquestrador, ex.: Kubernetes Service) distribuiria as requisições REST entre as réplicas; isso é uma preocupação de infraestrutura ortogonal ao código da aplicação, que já é stateless e pronto para isso (nenhuma sessão ou estado em memória entre requisições).
