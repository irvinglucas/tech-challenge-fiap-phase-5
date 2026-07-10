package com.fiap.prontuario.query.projection;

import com.fiap.prontuario.common.event.EventHeaders;
import com.fiap.prontuario.common.event.PatientRegistered;
import com.fiap.prontuario.common.event.PatientRecordEventCodec;

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

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a integracao ponta a ponta do consumer (issue #8): publica um
 * evento real no topico {@code patient-record-events} (mesmo formato usado
 * pelo records-command-service) e verifica que a projecao e atualizada.
 */
@QuarkusTest
class PatientRecordEventConsumerTest {

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    PatientRecordEventCodec codec;

    @Inject
    DataSource dataSource;

    @Test
    void consumes_a_published_event_and_updates_the_summary_projection() throws Exception {
        String patientId = "patient-" + UUID.randomUUID();
        PatientRegistered event = new PatientRegistered(
                UUID.randomUUID(), patientId, Instant.now(), "Carla Nunes", patientId, "unit-9");

        publish(event);

        awaitSummaryRow(patientId);
    }

    private void publish(PatientRegistered event) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader(EventHeaders.EVENT_TYPE, codec.typeOf(event).getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.CORRELATION_ID, "test-correlation".getBytes(StandardCharsets.UTF_8)));

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("patient-record-events", null, event.patientId(), codec.toJson(event), headers))
                    .get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void awaitSummaryRow(String patientId) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT full_name FROM resumo_paciente WHERE patient_id = ?")) {
                statement.setString(1, patientId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        assertThat(resultSet.getString("full_name")).isEqualTo("Carla Nunes");
                        return;
                    }
                }
            }
            Thread.sleep(300);
        }
        throw new AssertionError("resumo_paciente nao foi atualizado para " + patientId + " a tempo");
    }
}
