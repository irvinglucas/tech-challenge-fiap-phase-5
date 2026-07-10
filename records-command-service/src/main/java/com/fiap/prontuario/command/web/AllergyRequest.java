package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

/** O profissional (professionalId) e obtido do JWT autenticado, nao deste corpo. */
public record AllergyRequest(
        @NotBlank String unitId,
        @NotBlank String substance,
        @NotBlank String severity) {
}
