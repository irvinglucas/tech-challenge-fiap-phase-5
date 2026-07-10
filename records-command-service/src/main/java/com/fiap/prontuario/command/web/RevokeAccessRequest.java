package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

/** O gestor que revoga o acesso (revokedBy) e obtido do JWT autenticado, nao deste corpo. */
public record RevokeAccessRequest(
        @NotBlank String professionalId) {
}
