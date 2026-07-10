package com.fiap.prontuario.command.eventstore;

/** Falha de infraestrutura ao ler/gravar no event store. */
public class EventStoreException extends RuntimeException {

    public EventStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
