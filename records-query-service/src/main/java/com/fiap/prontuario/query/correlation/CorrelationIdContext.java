package com.fiap.prontuario.query.correlation;

import jakarta.enterprise.context.RequestScoped;

/** Carrega o correlation id da requisicao HTTP atual (ver issue #6/#9). */
@RequestScoped
public class CorrelationIdContext {

    private String correlationId;

    public String get() {
        return correlationId;
    }

    public void set(String correlationId) {
        this.correlationId = correlationId;
    }
}
