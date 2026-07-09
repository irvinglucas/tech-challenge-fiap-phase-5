package com.fiap.prontuario.common.event;

import java.time.Instant;
import java.util.UUID;

/** Emitido pelo comando RegisterAllergy. */
public record AllergyRegistered(
        UUID eventId,
        String patientId,
        Instant occurredAt,
        String professionalId,
        String unitId,
        String substance,
        String severity) implements PatientRecordEvent {
}
