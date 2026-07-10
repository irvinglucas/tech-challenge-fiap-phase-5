package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

/** O profissional (professionalId) e obtido do JWT autenticado, nao deste corpo. */
public record ConsultationRequest(
        @NotBlank String unitId,
        @NotBlank String notes) {
}
