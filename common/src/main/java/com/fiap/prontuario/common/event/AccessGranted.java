package com.fiap.prontuario.common.event;

import java.time.Instant;
import java.util.UUID;

/** Emitido pelo comando GrantAccess. */
public record AccessGranted(
        UUID eventId,
        String patientId,
        Instant occurredAt,
        String grantedBy,
        String professionalId,
        String unitId) implements PatientRecordEvent {
}
