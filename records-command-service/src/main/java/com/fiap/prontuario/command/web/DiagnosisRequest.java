package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

public record DiagnosisRequest(
        @NotBlank String professionalId,
        @NotBlank String unitId,
        @NotBlank String description,
        @NotBlank String cid10) {
}
