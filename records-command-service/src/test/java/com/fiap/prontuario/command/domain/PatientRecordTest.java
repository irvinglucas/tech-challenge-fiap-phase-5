package com.fiap.prontuario.command.domain;

import com.fiap.prontuario.common.event.AccessGranted;
import com.fiap.prontuario.common.event.AccessRevoked;
import com.fiap.prontuario.common.event.AllergyRegistered;
import com.fiap.prontuario.common.event.DiagnosisAdded;
import com.fiap.prontuario.common.event.PatientRecordEvent;
import com.fiap.prontuario.common.event.PatientRegistered;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a reconstrucao do agregado PatientRecord via replay de eventos
 * (issue #4) - nao depende de banco de dados.
 */
class PatientRecordTest {

    private static final String PATIENT_ID = "patient-1";
    private static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    @Test
    void an_unregistered_patient_has_version_zero_and_is_not_registered() {
        PatientRecord record = PatientRecord.replay(PATIENT_ID, List.of());

        assertThat(record.version()).isZero();
        assertThat(record.isRegistered()).isFalse();
    }

    @Test
    void replaying_events_reconstructs_the_expected_state_and_version() {
        List<PatientRecordEvent> events = List.of(
                new PatientRegistered(UUID.randomUUID(), PATIENT_ID, NOW, "Maria Silva", "12345678900", "unit-1"),
                new DiagnosisAdded(UUID.randomUUID(), PATIENT_ID, NOW, "prof-1", "unit-1", "Hipertensao", "I10"),
                new AllergyRegistered(UUID.randomUUID(), PATIENT_ID, NOW, "prof-1", "unit-1", "Dipirona", "ALTA"),
                new AccessGranted(UUID.randomUUID(), PATIENT_ID, NOW, "gestor-1", "prof-1", "unit-1"));

        PatientRecord record = PatientRecord.replay(PATIENT_ID, events);

        assertThat(record.isRegistered()).isTrue();
        assertThat(record.fullName()).isEqualTo("Maria Silva");
        assertThat(record.cpf()).isEqualTo("12345678900");
        assertThat(record.unitId()).isEqualTo("unit-1");
        assertThat(record.diagnoses()).containsExactly("Hipertensao");
        assertThat(record.allergies()).containsExactly("Dipirona");
        assertThat(record.isAuthorized("prof-1")).isTrue();
        assertThat(record.isAuthorized("prof-2")).isFalse();
        assertThat(record.version()).isEqualTo(events.size());
    }

    @Test
    void revoking_access_removes_the_professional_from_the_authorized_set() {
        List<PatientRecordEvent> events = List.of(
                new PatientRegistered(UUID.randomUUID(), PATIENT_ID, NOW, "Maria Silva", "12345678900", "unit-1"),
                new AccessGranted(UUID.randomUUID(), PATIENT_ID, NOW, "gestor-1", "prof-1", "unit-1"),
                new AccessRevoked(UUID.randomUUID(), PATIENT_ID, NOW, "gestor-1", "prof-1"));

        PatientRecord record = PatientRecord.replay(PATIENT_ID, events);

        assertThat(record.isAuthorized("prof-1")).isFalse();
        assertThat(record.version()).isEqualTo(3);
    }
}
