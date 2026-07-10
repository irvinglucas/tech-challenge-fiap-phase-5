package com.fiap.prontuario.query.access;

import com.fiap.prontuario.common.event.AccessDenied;
import com.fiap.prontuario.common.event.PatientRecordEvent;
import com.fiap.prontuario.common.event.RecordAccessed;
import com.fiap.prontuario.query.correlation.CorrelationIdContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implementa a query ViewPatientRecord do event storming: verifica se o
 * profissional autenticado tem acesso concedido ao paciente e publica
 * {@code RecordAccessed} (autorizado) ou {@code AccessDenied} (negado) no
 * mesmo topico de eventos de dominio, para consumo pelo audit-service
 * (issue #10/#11).
 */
@ApplicationScoped
public class PatientQueryService {

    private final PatientQueryRepository repository;
    private final PatientRecordEventPublisher eventPublisher;
    private final CorrelationIdContext correlationIdContext;

    @Inject
    public PatientQueryService(
            PatientQueryRepository repository,
            PatientRecordEventPublisher eventPublisher,
            CorrelationIdContext correlationIdContext) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.correlationIdContext = correlationIdContext;
    }

    public List<TimelineEntry> getConsolidatedRecord(String patientId, String professionalId, String unitId) {
        requireAuthorized(patientId, professionalId, unitId);
        return repository.findTimeline(patientId);
    }

    public PatientSummary getSummary(String patientId, String professionalId, String unitId) {
        requireAuthorized(patientId, professionalId, unitId);
        return repository.findSummary(patientId).orElseThrow(() -> new PatientNotFoundException(patientId));
    }

    public List<PatientByUnit> listPatientsByUnit(String unitId) {
        return repository.findPatientsByUnit(unitId);
    }

    private void requireAuthorized(String patientId, String professionalId, String unitId) {
        if (!repository.exists(patientId)) {
            throw new PatientNotFoundException(patientId);
        }
        if (!repository.isAuthorized(patientId, professionalId)) {
            publish(new AccessDenied(UUID.randomUUID(), patientId, Instant.now(), professionalId, unitId,
                    "profissional sem acesso concedido"));
            throw new UnauthorizedQueryException(patientId, professionalId);
        }
        publish(new RecordAccessed(UUID.randomUUID(), patientId, Instant.now(), professionalId, unitId));
    }

    private void publish(PatientRecordEvent event) {
        eventPublisher.publish(event, correlationIdContext.get());
    }
}
