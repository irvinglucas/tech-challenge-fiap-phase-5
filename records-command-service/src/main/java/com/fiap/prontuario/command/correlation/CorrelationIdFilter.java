package com.fiap.prontuario.command.correlation;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;

/**
 * Le o correlation id do header {@code X-Correlation-Id} (gerando um novo se
 * ausente), disponibilizando-o via {@link CorrelationIdContext} para ser
 * propagado nos eventos publicados no broker, e o devolve no header de
 * resposta para facilitar o rastreio ponta a ponta.
 */
@Provider
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";

    @Inject
    CorrelationIdContext correlationIdContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String correlationId = requestContext.getHeaderString(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        correlationIdContext.set(correlationId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        responseContext.getHeaders().putSingle(HEADER_NAME, correlationIdContext.get());
    }
}
