package com.fiap.prontuario.command.domain;

/** Precondicao do comando RevokeAccess: precisa existir um acesso previamente concedido. */
public class AccessNotGrantedException extends RuntimeException {

    public AccessNotGrantedException(String patientId, String professionalId) {
        super("Nao ha acesso concedido para o profissional " + professionalId
                + " no prontuario do paciente " + patientId + " para revogar");
    }
}
