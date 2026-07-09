package com.fiap.prontuario.common.event;

import java.time.Instant;
import java.util.UUID;

/** Emitido pela query ViewPatientRecord quando o acesso e negado. */
public record AccessDenied(
        UUID eventId,
        String patientId,
        Instant occurredAt,
        String professionalId,
        String unitId,
        String reason) implements PatientRecordEvent {
}
