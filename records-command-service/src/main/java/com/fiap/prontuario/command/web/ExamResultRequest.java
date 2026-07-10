package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

/** O profissional (professionalId) e obtido do JWT autenticado, nao deste corpo. */
public record ExamResultRequest(
        @NotBlank String unitId,
        @NotBlank String examType,
        @NotBlank String resultSummary) {
}
