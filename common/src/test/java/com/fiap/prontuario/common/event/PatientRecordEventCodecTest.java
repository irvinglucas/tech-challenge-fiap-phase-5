package com.fiap.prontuario.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste puro (sem Quarkus/CDI) do round-trip de serializacao dos eventos de
 * dominio, usado tanto pelo event store quanto pela publicacao no broker.
 */
class PatientRecordEventCodecTest {

    private final PatientRecordEventCodec codec =
            new PatientRecordEventCodec(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void round_trips_every_event_type() {
        Instant now = Instant.parse("2026-07-09T12:00:00Z");
        String patientId = "patient-1";

        PatientRecordEvent[] events = {
                new PatientRegistered(UUID.randomUUID(), patientId, now, "Maria Silva", "12345678900", "unit-1"),
                new ConsultationRecorded(UUID.randomUUID(), patientId, now, "prof-1", "unit-1", "Consulta de rotina"),
                new DiagnosisAdded(UUID.randomUUID(), patientId, now, "prof-1", "unit-1", "Hipertensao", "I10"),
                new PrescriptionIssued(UUID.randomUUID(), patientId, now, "prof-1", "unit-1", "Losartana", "50mg 1x/dia"),
                new AllergyRegistered(UUID.randomUUID(), patientId, now, "prof-1", "unit-1", "Dipirona", "ALTA"),
                new ExamResultAttached(UUID.randomUUID(), patientId, now, "prof-1", "unit-1", "Hemograma", "Normal"),
                new AccessGranted(UUID.randomUUID(), patientId, now, "gestor-1", "prof-1", "unit-1"),
                new AccessRevoked(UUID.randomUUID(), patientId, now, "gestor-1", "prof-1"),
                new RecordAccessed(UUID.randomUUID(), patientId, now, "prof-1", "unit-1"),
                new AccessDenied(UUID.randomUUID(), patientId, now, "prof-2", "unit-2", "fora do escopo de acesso"),
        };

        for (PatientRecordEvent event : events) {
            String type = codec.typeOf(event);
            String json = codec.toJson(event);
            PatientRecordEvent roundTripped = codec.fromJson(type, json);

            assertThat(roundTripped)
                    .as("round-trip de %s", type)
                    .isEqualTo(event);
        }
    }

    @Test
    void rejects_unknown_event_type() {
        assertThatThrownBy(() -> codec.fromJson("EventoInexistente", "{}"))
                .isInstanceOf(EventSerializationException.class)
                .hasMessageContaining("EventoInexistente");
    }
}
