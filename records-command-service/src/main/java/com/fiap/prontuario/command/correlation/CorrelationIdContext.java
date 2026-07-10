package com.fiap.prontuario.command.correlation;

import jakarta.enterprise.context.RequestScoped;

/**
 * Carrega o correlation id da requisicao HTTP atual, propagado para os
 * eventos publicados no broker (issue #6) e usado nos logs correlacionados
 * (issue #13).
 */
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
