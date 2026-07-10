package com.fiap.prontuario.query.access;

import java.time.Instant;

public record PatientSummary(
        String patientId,
        String fullName,
        String cpf,
        String unitId,
        String allergiesJson,
        String activeDiagnosesJson,
        String lastPrescriptionsJson,
        Instant updatedAt) {
}
