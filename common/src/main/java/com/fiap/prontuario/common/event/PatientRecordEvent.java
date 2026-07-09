package com.fiap.prontuario.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio do agregado PatientRecord.
 *
 * <p>Ver docs/event-storming.md na raiz do repositorio para a descricao
 * completa de cada evento, o comando que o origina e as politicas que
 * reagem a ele.
 */
public sealed interface PatientRecordEvent
        permits PatientRegistered, ConsultationRecorded, DiagnosisAdded, PrescriptionIssued,
        AllergyRegistered, ExamResultAttached, AccessGranted, AccessRevoked,
        RecordAccessed, AccessDenied {

    UUID eventId();

    String patientId();

    Instant occurredAt();
}
