package com.fiap.prontuario.query.access;

/** O profissional autenticado nao tem acesso concedido ao prontuario deste paciente (ViewPatientRecord negado). */
public class UnauthorizedQueryException extends RuntimeException {

    public UnauthorizedQueryException(String patientId, String professionalId) {
        super("Profissional " + professionalId + " nao tem acesso concedido ao prontuario do paciente " + patientId);
    }
}
