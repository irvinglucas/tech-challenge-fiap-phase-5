package com.fiap.prontuario.query.web;

import com.fiap.prontuario.query.access.PatientNotFoundException;
import com.fiap.prontuario.query.access.UnauthorizedQueryException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Traduz as violacoes de precondicao da query ViewPatientRecord (issue #9) para respostas HTTP. */
@Provider
public class QueryExceptionMapper implements ExceptionMapper<RuntimeException> {

    @Override
    public Response toResponse(RuntimeException exception) {
        Response.Status status = switch (exception) {
            case PatientNotFoundException e -> Response.Status.NOT_FOUND;
            case UnauthorizedQueryException e -> Response.Status.FORBIDDEN;
            default -> Response.Status.INTERNAL_SERVER_ERROR;
        };
        String message = status == Response.Status.INTERNAL_SERVER_ERROR
                ? "Erro interno ao processar a consulta"
                : exception.getMessage();
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(message))
                .build();
    }

    public record ErrorResponse(String message) {
    }
}
