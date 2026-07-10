package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

public record ExamResultRequest(
        @NotBlank String professionalId,
        @NotBlank String unitId,
        @NotBlank String examType,
        @NotBlank String resultSummary) {
}
