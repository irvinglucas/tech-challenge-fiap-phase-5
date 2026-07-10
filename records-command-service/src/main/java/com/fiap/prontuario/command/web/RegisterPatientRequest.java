package com.fiap.prontuario.command.web;

import jakarta.validation.constraints.NotBlank;

public record RegisterPatientRequest(
        @NotBlank String fullName,
        @NotBlank String cpf,
        @NotBlank String unitId) {
}
