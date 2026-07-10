package com.fiap.prontuario.command.eventstore;

import com.fiap.prontuario.common.event.EventHeaders;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que um comando aceito publica de fato o evento resultante no
 * topico {@code patient-record-events} (issue #6), com a chave = patientId
 * e os headers {@code event_type}/{@code correlation_id}.
 */
@QuarkusTest
class PatientRecordEventPublisherTest {

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    void publishes_the_event_with_key_and_headers_after_a_successful_command() {
        String cpf = String.valueOf(System.nanoTime());
        String correlationId = "corr-" + cpf;

        given().contentType(ContentType.JSON)
                .header("X-Correlation-Id", correlationId)
                .body(Map.of("fullName", "Joana Souza", "cpf", cpf, "unitId", "unit-1"))
                .when().post("/patients")
                .then().statusCode(201);

        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(Collections.singletonList("patient-record-events"));

            ConsumerRecord<String, String> record = pollForRecordWithKey(consumer, cpf);

            assertThat(record).as("evento publicado para o paciente %s", cpf).isNotNull();
            assertThat(record.value()).contains(cpf);
            assertThat(headerValue(record, EventHeaders.EVENT_TYPE)).isEqualTo("PatientRegistered");
            assertThat(headerValue(record, EventHeaders.CORRELATION_ID)).isEqualTo(correlationId);
        }
    }

    private ConsumerRecord<String, String> pollForRecordWithKey(KafkaConsumer<String, String> consumer, String key) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        return null;
    }

    private String headerValue(ConsumerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private KafkaConsumer<String, String> newConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }
}
