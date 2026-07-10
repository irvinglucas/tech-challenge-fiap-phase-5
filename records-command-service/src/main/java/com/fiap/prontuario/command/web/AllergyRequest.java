package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

public record AllergyRequest(
        @NotBlank String professionalId,
        @NotBlank String unitId,
        @NotBlank String substance,
        @NotBlank String severity) {
}
