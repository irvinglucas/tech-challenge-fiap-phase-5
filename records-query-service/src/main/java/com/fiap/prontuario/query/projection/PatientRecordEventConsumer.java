package com.fiap.prontuario.query.projection;

import com.fiap.prontuario.common.event.EventHeaders;
import com.fiap.prontuario.common.event.PatientRecordEvent;
import com.fiap.prontuario.common.event.PatientRecordEventCodec;
import com.fiap.prontuario.query.correlation.CorrelationIdContext;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletionStage;

/**
 * Consome o topico {@code patient-record-events} (issue #8) e delega para o
 * {@link ReadModelProjector} a atualizacao das projecoes de leitura.
 *
 * <p>Resiliencia (issue #14): uma falha transitoria (ex.: Postgres
 * momentaneamente indisponivel) e reprocessada automaticamente (retry); se
 * as falhas persistirem, o circuit breaker abre para parar de sobrecarregar
 * a dependencia com tentativas inuteis. Se mesmo assim o evento nao puder
 * ser projetado, ele e enviado para a DLQ e a mensagem original e
 * confirmada (ack) - do contrario, uma mensagem "envenenada" travaria para
 * sempre o consumo de toda a particao.
 */
@ApplicationScoped
public class PatientRecordEventConsumer {

    private static final Logger LOG = Logger.getLogger(PatientRecordEventConsumer.class);

    private final ReadModelProjector projector;
    private final PatientRecordEventCodec codec;
    private final DeadLetterPublisher deadLetterPublisher;

    @Inject
    public PatientRecordEventConsumer(
            ReadModelProjector projector, PatientRecordEventCodec codec, DeadLetterPublisher deadLetterPublisher) {
        this.projector = projector;
        this.codec = codec;
        this.deadLetterPublisher = deadLetterPublisher;
    }

    @Incoming("patient-record-events-in")
    @Blocking
    public CompletionStage<Void> consume(Message<String> message) {
        String eventType = headerValue(message, EventHeaders.EVENT_TYPE);
        String correlationId = headerValue(message, EventHeaders.CORRELATION_ID);

        // Consumidor roda numa thread propria (nao a thread da requisicao HTTP
        // que originou o evento), entao o correlation id precisa ser colocado
        // no MDC aqui para aparecer nos logs JSON desta etapa (issue #13).
        MDC.put(CorrelationIdContext.MDC_KEY, correlationId);
        try {
            projectWithResilience(eventType, message.getPayload(), correlationId);
            return message.ack();
        } catch (Exception e) {
            LOG.errorf(e, "Evento %s do topico patient-record-events nao pode ser projetado apos retries/circuit breaker; enviando para a DLQ (correlationId=%s)",
                    eventType, correlationId);
            deadLetterPublisher.publish(eventType, message.getPayload(), correlationId, e.getMessage());
            return message.ack();
        } finally {
            MDC.remove(CorrelationIdContext.MDC_KEY);
        }
    }

    // requestVolumeThreshold alto o suficiente para nao abrir o circuito por
    // causa das tentativas (1 inicial + 3 retries) de uma unica mensagem
    // invalida - o objetivo do circuit breaker e proteger a projecao de um
    // padrao sustentado de falhas (ex.: Postgres fora do ar), nao reagir a
    // um unico evento "envenenado".
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5_000, delayUnit = ChronoUnit.MILLIS)
    void projectWithResilience(String eventType, String payload, String correlationId) {
        PatientRecordEvent event = codec.fromJson(eventType, payload);
        LOG.infof("Projetando evento %s do paciente %s (correlationId=%s)", eventType, event.patientId(), correlationId);
        projector.project(event);
    }

    private String headerValue(Message<String> message, String headerName) {
        return message.getMetadata(IncomingKafkaRecordMetadata.class)
                .map(metadata -> {
                    Header header = metadata.getHeaders().lastHeader(headerName);
                    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
                })
                .orElse(null);
    }
}
