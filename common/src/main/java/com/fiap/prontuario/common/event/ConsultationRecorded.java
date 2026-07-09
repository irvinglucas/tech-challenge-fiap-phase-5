package com.fiap.prontuario.common.event;

import java.time.Instant;
import java.util.UUID;

/** Emitido pelo comando RecordConsultation. */
public record ConsultationRecorded(
        UUID eventId,
        String patientId,
        Instant occurredAt,
        String professionalId,
        String unitId,
        String notes) implements PatientRecordEvent {
}
