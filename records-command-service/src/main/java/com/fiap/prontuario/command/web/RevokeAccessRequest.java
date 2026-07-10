package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

public record RevokeAccessRequest(
        @NotBlank String revokedBy,
        @NotBlank String professionalId) {
}
