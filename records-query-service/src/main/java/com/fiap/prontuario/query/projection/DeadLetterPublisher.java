package com.fiap.prontuario.query.projection;

import com.fiap.prontuario.common.event.EventHeaders;

import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

import java.nio.charset.StandardCharsets;

/**
 * Publica na DLQ ({@code patient-record-events-dlq}, issue #14) os eventos
 * que continuam falhando na projecao de leitura depois dos retries e do
 * circuit breaker de {@link PatientRecordEventConsumer}, preservando o
 * payload e os headers originais mais o motivo da falha, para inspecao
 * manual (ex.: via Redpanda Console) sem bloquear o consumo do restante do
 * topico.
 */
@ApplicationScoped
public class DeadLetterPublisher {

    private final Emitter<String> emitter;

    @Inject
    public DeadLetterPublisher(@Channel("patient-record-events-dlq-out") Emitter<String> emitter) {
        this.emitter = emitter;
    }

    public void publish(String eventType, String payload, String correlationId, String failureReason) {
        RecordHeaders headers = new RecordHeaders();
        headers.add(EventHeaders.EVENT_TYPE, safe(eventType).getBytes(StandardCharsets.UTF_8));
        headers.add(EventHeaders.CORRELATION_ID, safe(correlationId).getBytes(StandardCharsets.UTF_8));
        headers.add(EventHeaders.FAILURE_REASON, safe(failureReason).getBytes(StandardCharsets.UTF_8));

        OutgoingKafkaRecordMetadata<String> kafkaMetadata = OutgoingKafkaRecordMetadata.<String>builder()
                .withHeaders(headers)
                .build();

        emitter.send(Message.of(payload, Metadata.of(kafkaMetadata)));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
