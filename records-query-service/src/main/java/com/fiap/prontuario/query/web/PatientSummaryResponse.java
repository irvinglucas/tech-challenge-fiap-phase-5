package com.fiap.prontuario.query.web;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record PatientSummaryResponse(
        String patientId,
        String fullName,
        String cpf,
        String unitId,
        JsonNode allergies,
        JsonNode activeDiagnoses,
        JsonNode lastPrescriptions,
        Instant updatedAt) {
}
