package com.fiap.prontuario.command.eventstore;

import com.fiap.prontuario.command.domain.PatientRecord;
import com.fiap.prontuario.command.domain.PatientRecordRepository;
import com.fiap.prontuario.common.event.AccessGranted;
import com.fiap.prontuario.common.event.DiagnosisAdded;
import com.fiap.prontuario.common.event.PatientRegistered;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa a persistencia real no event store (tabela {@code event_store},
 * migrada via Flyway) e o controle de concorrencia otimista (issue #4).
 *
 * <p>Requer Docker: o Quarkus Dev Services sobe um Postgres efemero
 * automaticamente (nenhuma configuracao de datasource e feita para o
 * profile de teste - ver application.properties).
 */
@QuarkusTest
class PatientRecordEventStoreTest {

    @Inject
    PatientRecordEventStore eventStore;

    @Inject
    PatientRecordRepository repository;

    @Test
    void appends_an_event_and_loads_it_back() {
        String patientId = "patient-" + UUID.randomUUID();

        int newVersion = eventStore.append(patientId, 0,
                new PatientRegistered(UUID.randomUUID(), patientId, Instant.now(), "Maria Silva", "12345678900", "unit-1"));

        assertThat(newVersion).isEqualTo(1);
        assertThat(eventStore.loadEvents(patientId)).hasSize(1);
    }

    @Test
    void repository_replays_persisted_events_into_the_aggregate() {
        String patientId = "patient-" + UUID.randomUUID();

        eventStore.append(patientId, 0,
                new PatientRegistered(UUID.randomUUID(), patientId, Instant.now(), "Joao Souza", "98765432100", "unit-2"));
        eventStore.append(patientId, 1,
                new DiagnosisAdded(UUID.randomUUID(), patientId, Instant.now(), "prof-1", "unit-2", "Diabetes", "E11"));

        PatientRecord record = repository.load(patientId);

        assertThat(record.isRegistered()).isTrue();
        assertThat(record.fullName()).isEqualTo("Joao Souza");
        assertThat(record.diagnoses()).containsExactly("Diabetes");
        assertThat(record.version()).isEqualTo(2);
    }

    @Test
    void rejects_a_concurrent_write_at_a_stale_version() {
        String patientId = "patient-" + UUID.randomUUID();
        eventStore.append(patientId, 0,
                new PatientRegistered(UUID.randomUUID(), patientId, Instant.now(), "Ana Costa", "11122233344", "unit-1"));

        // Duas "requisicoes" leem a versao 1 e tentam escrever a versao 2 ao mesmo tempo.
        eventStore.append(patientId, 1,
                new AccessGranted(UUID.randomUUID(), patientId, Instant.now(), "gestor-1", "prof-1", "unit-1"));

        assertThatThrownBy(() -> eventStore.append(patientId, 1,
                new AccessGranted(UUID.randomUUID(), patientId, Instant.now(), "gestor-1", "prof-2", "unit-1")))
                .isInstanceOf(ConcurrencyConflictException.class);

        assertThat(repository.load(patientId).version()).isEqualTo(2);
    }

    @Test
    void loading_events_for_an_unknown_patient_returns_an_empty_list() {
        assertThat(eventStore.loadEvents("patient-" + UUID.randomUUID())).isEmpty();
    }
}
