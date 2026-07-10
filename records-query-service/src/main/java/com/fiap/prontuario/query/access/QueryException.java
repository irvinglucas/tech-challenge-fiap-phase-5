package com.fiap.prontuario.query.access;

/** Falha de infraestrutura ao consultar as projecoes de leitura. */
public class QueryException extends RuntimeException {

    public QueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
