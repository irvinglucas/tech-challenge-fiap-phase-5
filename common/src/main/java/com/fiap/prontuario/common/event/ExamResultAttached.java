package com.fiap.prontuario.common.event;

import java.time.Instant;
import java.util.UUID;

/** Emitido pelo comando AttachExamResult. */
public record ExamResultAttached(
        UUID eventId,
        String patientId,
        Instant occurredAt,
        String professionalId,
        String unitId,
        String examType,
        String resultSummary) implements PatientRecordEvent {
}
