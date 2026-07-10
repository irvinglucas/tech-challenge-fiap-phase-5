package com.fiap.prontuario.command.domain;

import com.fiap.prontuario.command.eventstore.PatientRecordEventStore;
import com.fiap.prontuario.common.event.PatientRecordEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Fachada usada pelos comandos (issue #5) para carregar o agregado
 * PatientRecord (via replay dos eventos) e gravar novos eventos com
 * controle de concorrencia otimista.
 */
@ApplicationScoped
public class PatientRecordRepository {

    private final PatientRecordEventStore eventStore;

    @Inject
    public PatientRecordRepository(PatientRecordEventStore eventStore) {
        this.eventStore = eventStore;
    }

    public PatientRecord load(String patientId) {
        return PatientRecord.replay(patientId, eventStore.loadEvents(patientId));
    }

    /** @return a nova versao do agregado apos a gravacao do evento. */
    public int append(String patientId, int expectedVersion, PatientRecordEvent event) {
        return eventStore.append(patientId, expectedVersion, event);
    }
}
