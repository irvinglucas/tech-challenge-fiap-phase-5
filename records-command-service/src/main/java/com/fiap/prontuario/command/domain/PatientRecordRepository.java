package com.fiap.prontuario.command.domain;

import com.fiap.prontuario.command.correlation.CorrelationIdContext;
import com.fiap.prontuario.command.eventstore.PatientRecordEventPublisher;
import com.fiap.prontuario.command.eventstore.PatientRecordEventStore;
import com.fiap.prontuario.common.event.AccessDenied;
import com.fiap.prontuario.common.event.PatientRecordEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;

/**
 * Fachada usada pelos comandos (issue #5) para carregar o agregado
 * PatientRecord (via replay dos eventos) e gravar novos eventos com
 * controle de concorrencia otimista (issue #4). Apos a gravacao, publica o
 * evento no broker (issue #6) para consumo pelo records-query-service e
 * pelo audit-service.
 */
@ApplicationScoped
public class PatientRecordRepository {

    private final PatientRecordEventStore eventStore;
    private final PatientRecordEventPublisher eventPublisher;
    private final CorrelationIdContext correlationIdContext;

    @Inject
    public PatientRecordRepository(
            PatientRecordEventStore eventStore,
            PatientRecordEventPublisher eventPublisher,
            CorrelationIdContext correlationIdContext) {
        this.eventStore = eventStore;
        this.eventPublisher = eventPublisher;
        this.correlationIdContext = correlationIdContext;
    }

    public PatientRecord load(String patientId) {
        return PatientRecord.replay(patientId, eventStore.loadEvents(patientId));
    }

    /** @return a nova versao do agregado apos a gravacao do evento. */
    public int append(String patientId, int expectedVersion, PatientRecordEvent event) {
        int newVersion = eventStore.append(patientId, expectedVersion, event);
        eventPublisher.publish(event, correlationIdContext.get());
        return newVersion;
    }

    /**
     * Publica uma tentativa de acesso negada (issue #7). Nao consome versao
     * do agregado: e um sinal de auditoria, nao uma mutacao de estado (ver
     * PatientRecord#apply, que trata AccessDenied como no-op).
     */
    public void publishAccessDenied(String patientId, String professionalId, String unitId, String reason) {
        AccessDenied event = new AccessDenied(UUID.randomUUID(), patientId, Instant.now(), professionalId, unitId, reason);
        eventPublisher.publish(event, correlationIdContext.get());
    }
}
