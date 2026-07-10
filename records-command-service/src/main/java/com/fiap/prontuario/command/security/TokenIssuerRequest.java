package com.fiap.prontuario.command.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record TokenIssuerRequest(
        @NotBlank String professionalId,
        @NotEmpty Set<String> roles) {
}
