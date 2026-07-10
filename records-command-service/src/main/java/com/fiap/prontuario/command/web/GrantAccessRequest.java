package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

/** O gestor que concede o acesso (grantedBy) e obtido do JWT autenticado, nao deste corpo. */
public record GrantAccessRequest(
        @NotBlank String professionalId,
        @NotBlank String unitId) {
}
