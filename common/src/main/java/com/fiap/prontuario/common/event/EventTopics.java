package com.fiap.prontuario.common.event;

/** Nomes dos topicos Kafka/Redpanda usados para transportar os eventos de dominio. */
public final class EventTopics {

    public static final String PATIENT_RECORD_EVENTS = "patient-record-events";
    public static final String PATIENT_RECORD_EVENTS_DLQ = "patient-record-events-dlq";

    private EventTopics() {
    }
}
