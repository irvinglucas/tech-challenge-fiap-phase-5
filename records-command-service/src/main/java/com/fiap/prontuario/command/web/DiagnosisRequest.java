package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

/** O profissional (professionalId) e obtido do JWT autenticado, nao deste corpo. */
public record DiagnosisRequest(
        @NotBlank String unitId,
        @NotBlank String description,
        @NotBlank String cid10) {
}
