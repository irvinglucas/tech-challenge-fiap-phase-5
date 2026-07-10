package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

public record PrescriptionRequest(
        @NotBlank String professionalId,
        @NotBlank String unitId,
        @NotBlank String medication,
        @NotBlank String dosage) {
}
