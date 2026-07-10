package com.fiap.prontuario.command.eventstore;

/**
 * Lancada quando um comando tenta gravar um evento a partir de uma versao
 * do agregado {@code PatientRecord} que ja foi superada por outra gravacao
 * concorrente (controle de concorrencia otimista).
 */
public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String patientId, int expectedVersion) {
        super("Conflito de concorrencia no paciente " + patientId
                + ": versao esperada " + expectedVersion + " ja foi superada por outra gravacao");
    }
}
