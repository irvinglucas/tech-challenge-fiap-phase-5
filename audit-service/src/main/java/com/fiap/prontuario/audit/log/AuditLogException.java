package com.fiap.prontuario.audit.log;

/** Falha de infraestrutura ao ler/escrever a trilha de auditoria. */
public class AuditLogException extends RuntimeException {

    public AuditLogException(String message, Throwable cause) {
        super(message, cause);
    }
}
