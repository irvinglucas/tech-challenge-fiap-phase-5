package com.fiap.prontuario.common.event;

/**
 * Nomes dos headers Kafka usados nas mensagens do topico
 * {@link EventTopics#PATIENT_RECORD_EVENTS} e da sua DLQ
 * ({@link EventTopics#PATIENT_RECORD_EVENTS_DLQ}, issue #14).
 */
public final class EventHeaders {

    public static final String EVENT_TYPE = "event_type";
    public static final String CORRELATION_ID = "correlation_id";

    /** Apenas nas mensagens da DLQ: motivo pelo qual o consumo falhou apos os retries/circuit breaker. */
    public static final String FAILURE_REASON = "failure_reason";

    private EventHeaders() {
    }
}
