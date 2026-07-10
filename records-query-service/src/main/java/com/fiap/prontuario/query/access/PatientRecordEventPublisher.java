package com.fiap.prontuario.query.access;

import com.fiap.prontuario.common.event.EventHeaders;
import com.fiap.prontuario.common.event.PatientRecordEvent;
import com.fiap.prontuario.common.event.PatientRecordEventCodec;

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
 * Publica RecordAccessed/AccessDenied (issue #9) no mesmo topico
 * patient-record-events consumido pelo issue #8, para que o audit-service
 * (issue #10/#11) monte a trilha de auditoria de leituras.
 */
@ApplicationScoped
public class PatientRecordEventPublisher {

    private final Emitter<String> emitter;
    private final PatientRecordEventCodec codec;

    @Inject
    public PatientRecordEventPublisher(
            @Channel("patient-record-events-out") Emitter<String> emitter, PatientRecordEventCodec codec) {
        this.emitter = emitter;
        this.codec = codec;
    }

    public void publish(PatientRecordEvent event, String correlationId) {
        String payload = codec.toJson(event);

        RecordHeaders headers = new RecordHeaders();
        headers.add(EventHeaders.EVENT_TYPE, codec.typeOf(event).getBytes(StandardCharsets.UTF_8));
        headers.add(EventHeaders.CORRELATION_ID, (correlationId == null ? "" : correlationId).getBytes(StandardCharsets.UTF_8));

        OutgoingKafkaRecordMetadata<String> kafkaMetadata = OutgoingKafkaRecordMetadata.<String>builder()
                .withKey(event.patientId())
                .withHeaders(headers)
                .build();

        emitter.send(Message.of(payload, Metadata.of(kafkaMetadata)));
    }
}
