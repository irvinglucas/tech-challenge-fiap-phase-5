package com.fiap.prontuario.common.event;

import java.time.Instant;
import java.util.UUID;

/** Emitido pela query ViewPatientRecord quando o acesso e autorizado. */
public record RecordAccessed(
        UUID eventId,
        String patientId,
        Instant occurredAt,
        String professionalId,
        String unitId) implements PatientRecordEvent {
}
