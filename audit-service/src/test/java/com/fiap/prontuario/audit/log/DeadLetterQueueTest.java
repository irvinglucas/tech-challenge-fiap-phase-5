package com.fiap.prontuario.audit.log;

import com.fiap.prontuario.common.event.EventHeaders;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a resiliencia do consumer de auditoria (issue #14): um evento que
 * nunca consegue ser desserializado/gravado (aqui, um tipo de evento
 * desconhecido) esgota os retries do circuit breaker e acaba na DLQ
 * {@code patient-record-events-dlq}, sem travar o consumo do topico
 * principal.
 */
@QuarkusTest
class DeadLetterQueueTest {

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    void an_event_that_cannot_be_processed_ends_up_in_the_dead_letter_topic() {
        String patientId = "patient-" + UUID.randomUUID();
        String correlationId = "corr-" + UUID.randomUUID();
        String payload = "{\"patientId\":\"" + patientId + "\"}";

        publishToMainTopic("UnknownEventType", payload, correlationId);

        ConsumerRecord<String, String> dlqRecord = awaitDlqRecord(patientId);

        assertThat(headerValue(dlqRecord, EventHeaders.EVENT_TYPE)).isEqualTo("UnknownEventType");
        assertThat(headerValue(dlqRecord, EventHeaders.CORRELATION_ID)).isEqualTo(correlationId);
        assertThat(headerValue(dlqRecord, EventHeaders.FAILURE_REASON)).isNotBlank();
        assertThat(dlqRecord.value()).isEqualTo(payload);
    }

    private void publishToMainTopic(String eventType, String payload, String correlationId) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader(EventHeaders.EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8)));

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("patient-record-events", null, "some-key", payload, headers)).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ConsumerRecord<String, String> awaitDlqRecord(String patientId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList("patient-record-events-dlq"));
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && record.value().contains(patientId)) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("Nenhuma mensagem chegou na DLQ para " + patientId + " a tempo");
    }

    private String headerValue(ConsumerRecord<String, String> record, String headerName) {
        var header = record.headers().lastHeader(headerName);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
