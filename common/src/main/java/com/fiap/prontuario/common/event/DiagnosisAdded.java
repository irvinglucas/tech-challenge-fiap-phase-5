package com.fiap.prontuario.common.event;

import java.time.Instant;
import java.util.UUID;

/** Emitido pelo comando AddDiagnosis. */
public record DiagnosisAdded(
        UUID eventId,
        String patientId,
        Instant occurredAt,
        String professionalId,
        String unitId,
        String description,
        String cid10) implements PatientRecordEvent {
}
