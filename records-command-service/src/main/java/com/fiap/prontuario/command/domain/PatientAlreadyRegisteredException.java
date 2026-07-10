package com.fiap.prontuario.command.domain;

/** Precondicao do comando RegisterPatient: o CPF ainda nao pode estar registrado. */
public class PatientAlreadyRegisteredException extends RuntimeException {

    public PatientAlreadyRegisteredException(String patientId) {
        super("Paciente " + patientId + " ja esta registrado");
    }
}
