package com.fiap.prontuario.command.domain;

/** Precondicao dos comandos clinicos e de acesso: o paciente precisa estar registrado. */
public class PatientNotRegisteredException extends RuntimeException {

    public PatientNotRegisteredException(String patientId) {
        super("Paciente " + patientId + " nao esta registrado");
    }
}
