package com.fiap.prontuario.common.event;

import java.time.Instant;
import java.util.UUID;

/** Emitido pelo comando RegisterPatient. */
public record PatientRegistered(
        UUID eventId,
        String patientId,
        Instant occurredAt,
        String fullName,
        String cpf,
        String unitId) implements PatientRecordEvent {
}
