package com.fiap.prontuario.common.event;

import java.time.Instant;
import java.util.UUID;

/** Emitido pelo comando IssuePrescription. */
public record PrescriptionIssued(
        UUID eventId,
        String patientId,
        Instant occurredAt,
        String professionalId,
        String unitId,
        String medication,
        String dosage) implements PatientRecordEvent {
}
