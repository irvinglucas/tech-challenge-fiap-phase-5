package com.fiap.prontuario.command.domain;

/**
 * Precondicao dos comandos clinicos: o profissional precisa ter acesso
 * concedido (evento {@code AccessGranted}) ao prontuario do paciente.
 */
public class UnauthorizedAccessException extends RuntimeException {

    public UnauthorizedAccessException(String patientId, String professionalId) {
        super("Profissional " + professionalId + " nao tem acesso concedido ao prontuario do paciente " + patientId);
    }
}
