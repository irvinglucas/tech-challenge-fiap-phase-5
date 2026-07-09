package com.fiap.prontuario.common.event;

import java.time.Instant;
import java.util.UUID;

/** Emitido pelo comando RevokeAccess. */
public record AccessRevoked(
        UUID eventId,
        String patientId,
        Instant occurredAt,
        String revokedBy,
        String professionalId) implements PatientRecordEvent {
}
