package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

public record ConsultationRequest(
        @NotBlank String professionalId,
        @NotBlank String unitId,
        @NotBlank String notes) {
}
