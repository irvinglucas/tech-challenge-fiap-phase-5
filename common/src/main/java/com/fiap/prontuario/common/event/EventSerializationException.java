package com.fiap.prontuario.common.event;

/** Falha ao serializar/desserializar um {@link PatientRecordEvent}. */
public class EventSerializationException extends RuntimeException {

    public EventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventSerializationException(String message) {
        super(message);
    }
}
