package com.fiap.prontuario.query.projection;

/** Falha de infraestrutura ao ler/atualizar as projecoes de leitura. */
public class ProjectionException extends RuntimeException {

    public ProjectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
