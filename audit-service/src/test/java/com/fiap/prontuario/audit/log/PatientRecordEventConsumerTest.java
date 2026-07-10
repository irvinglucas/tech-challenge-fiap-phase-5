package com.fiap.prontuario.audit.log;

import com.fiap.prontuario.common.event.EventHeaders;
import com.fiap.prontuario.common.event.PatientRecordEventCodec;
import com.fiap.prontuario.common.event.PatientRegistered;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a integracao ponta a ponta do consumer de auditoria (issue #10):
 * publica um evento real no topico patient-record-events e verifica que a
 * trilha de auditoria e atualizada.
 */
@QuarkusTest
class PatientRecordEventConsumerTest {

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    PatientRecordEventCodec codec;

    @Inject
    AuditLogWriter auditLogWriter;

    @Test
    void consumes_a_published_event_and_records_it_in_the_audit_log() {
        String patientId = "patient-" + UUID.randomUUID();
        PatientRegistered event = new PatientRegistered(
                UUID.randomUUID(), patientId, Instant.now(), "Carla Nunes", patientId, "unit-9");

        publish(event, "test-correlation");

        awaitAuditLogEntry(patientId);
    }

    private void publish(PatientRegistered event, String correlationId) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader(EventHeaders.EVENT_TYPE, codec.typeOf(event).getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8)));

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("patient-record-events", null, event.patientId(), codec.toJson(event), headers))
                    .get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void awaitAuditLogEntry(String patientId) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            List<AuditLogEntry> entries = auditLogWriter.findByPatientId(patientId);
            if (!entries.isEmpty()) {
                assertThat(entries).hasSize(1);
                assertThat(entries.get(0).eventType()).isEqualTo("PatientRegistered");
                assertThat(entries.get(0).detail()).contains("Carla Nunes");
                return;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new AssertionError("audit_log nao foi atualizado para " + patientId + " a tempo");
    }
}
