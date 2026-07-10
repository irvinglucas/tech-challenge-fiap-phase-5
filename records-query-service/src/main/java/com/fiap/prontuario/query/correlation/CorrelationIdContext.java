package com.fiap.prontuario.query.correlation;

import jakarta.enterprise.context.RequestScoped;

import org.jboss.logging.MDC;

/**
 * Carrega o correlation id da requisicao HTTP atual (ver issue #6/#9): ao
 * ser definido, e colocado no MDC do JBoss Logging, que o
 * quarkus-logging-json inclui automaticamente em todo log JSON emitido
 * durante a requisicao (campo {@code mdc.correlationId}) (issue #13).
 */
@RequestScoped
public class CorrelationIdContext {

    public static final String MDC_KEY = "correlationId";

    private String correlationId;

    public String get() {
        return correlationId;
    }

    public void set(String correlationId) {
        this.correlationId = correlationId;
        MDC.put(MDC_KEY, correlationId);
    }

    public void clear() {
        MDC.remove(MDC_KEY);
    }
}
