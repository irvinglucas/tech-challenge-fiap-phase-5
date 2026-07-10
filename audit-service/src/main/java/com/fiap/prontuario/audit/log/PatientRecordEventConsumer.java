package com.fiap.prontuario.audit.log;

import com.fiap.prontuario.audit.anomaly.SuspiciousAccessDetector;
import com.fiap.prontuario.common.event.EventHeaders;
import com.fiap.prontuario.common.event.PatientRecordEvent;
import com.fiap.prontuario.common.event.PatientRecordEventCodec;
import com.fiap.prontuario.common.event.RecordAccessed;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;

/**
 * Consome o topico {@code patient-record-events} (issue #10) e grava cada
 * evento na trilha de auditoria append-only, alem de disparar a deteccao de
 * acesso anomalo (issue #11) apos cada {@link RecordAccessed}.
 */
@ApplicationScoped
public class PatientRecordEventConsumer {

    private static final Logger LOG = Logger.getLogger(PatientRecordEventConsumer.class);

    private final AuditLogWriter auditLogWriter;
    private final SuspiciousAccessDetector suspiciousAccessDetector;
    private final PatientRecordEventCodec codec;

    @Inject
    public PatientRecordEventConsumer(
            AuditLogWriter auditLogWriter,
            SuspiciousAccessDetector suspiciousAccessDetector,
            PatientRecordEventCodec codec) {
        this.auditLogWriter = auditLogWriter;
        this.suspiciousAccessDetector = suspiciousAccessDetector;
        this.codec = codec;
    }

    @Incoming("patient-record-events-in")
    @Blocking
    public CompletionStage<Void> consume(Message<String> message) {
        String eventType = headerValue(message, EventHeaders.EVENT_TYPE);
        String correlationId = headerValue(message, EventHeaders.CORRELATION_ID);

        PatientRecordEvent event = codec.fromJson(eventType, message.getPayload());
        AuditFieldsExtractor.AuditFields fields = AuditFieldsExtractor.extract(event);

        LOG.infof("Registrando na auditoria: evento=%s paciente=%s profissional=%s (correlationId=%s)",
                eventType, event.patientId(), fields.professionalId(), correlationId);

        auditLogWriter.append(
                event.patientId(), eventType, event.occurredAt(),
                fields.professionalId(), fields.unitId(), fields.detail(), correlationId);

        if (event instanceof RecordAccessed recordAccessed) {
            suspiciousAccessDetector.checkAfterAccess(recordAccessed.professionalId(), recordAccessed.occurredAt());
        }

        return message.ack();
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
