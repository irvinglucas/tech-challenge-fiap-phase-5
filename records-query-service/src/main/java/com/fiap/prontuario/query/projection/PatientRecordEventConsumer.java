package com.fiap.prontuario.query.projection;

import com.fiap.prontuario.common.event.EventHeaders;
import com.fiap.prontuario.common.event.PatientRecordEvent;
import com.fiap.prontuario.common.event.PatientRecordEventCodec;

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
 * Consome o topico {@code patient-record-events} (issue #8) e delega para o
 * {@link ReadModelProjector} a atualizacao das projecoes de leitura.
 */
@ApplicationScoped
public class PatientRecordEventConsumer {

    private static final Logger LOG = Logger.getLogger(PatientRecordEventConsumer.class);

    private final ReadModelProjector projector;
    private final PatientRecordEventCodec codec;

    @Inject
    public PatientRecordEventConsumer(ReadModelProjector projector, PatientRecordEventCodec codec) {
        this.projector = projector;
        this.codec = codec;
    }

    @Incoming("patient-record-events-in")
    @Blocking
    public CompletionStage<Void> consume(Message<String> message) {
        String eventType = headerValue(message, EventHeaders.EVENT_TYPE);
        String correlationId = headerValue(message, EventHeaders.CORRELATION_ID);

        PatientRecordEvent event = codec.fromJson(eventType, message.getPayload());
        LOG.infof("Projetando evento %s do paciente %s (correlationId=%s)", eventType, event.patientId(), correlationId);
        projector.project(event);
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
