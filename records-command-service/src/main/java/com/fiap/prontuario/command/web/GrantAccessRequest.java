package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

public record GrantAccessRequest(
        @NotBlank String grantedBy,
        @NotBlank String professionalId,
        @NotBlank String unitId) {
}
