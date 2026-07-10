package com.fiap.prontuario.query.access;

/** Nenhuma projecao encontrada para o patientId informado (paciente nunca registrado). */
public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException(String patientId) {
        super("Paciente " + patientId + " nao encontrado");
    }
}
