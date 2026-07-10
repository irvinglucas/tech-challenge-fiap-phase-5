package com.fiap.prontuario.command.web;

import com.fiap.prontuario.command.domain.AccessNotGrantedException;
import com.fiap.prontuario.command.domain.PatientAlreadyRegisteredException;
import com.fiap.prontuario.command.domain.PatientNotRegisteredException;
import com.fiap.prontuario.command.domain.UnauthorizedAccessException;
import com.fiap.prontuario.command.eventstore.ConcurrencyConflictException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduz as violacoes de precondicao dos comandos (ver docs/event-storming.md)
 * e o conflito de concorrencia otimista do event store para respostas HTTP.
 */
@Provider
public class CommandExceptionMapper implements ExceptionMapper<RuntimeException> {

    @Override
    public Response toResponse(RuntimeException exception) {
        Response.Status status = switch (exception) {
            case PatientAlreadyRegisteredException e -> Response.Status.CONFLICT;
            case PatientNotRegisteredException e -> Response.Status.NOT_FOUND;
            case UnauthorizedAccessException e -> Response.Status.FORBIDDEN;
            case AccessNotGrantedException e -> Response.Status.CONFLICT;
            case ConcurrencyConflictException e -> Response.Status.CONFLICT;
            default -> Response.Status.INTERNAL_SERVER_ERROR;
        };
        String message = status == Response.Status.INTERNAL_SERVER_ERROR
                ? "Erro interno ao processar o comando"
                : exception.getMessage();
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(message))
                .build();
    }

    public record ErrorResponse(String message) {
    }
}
