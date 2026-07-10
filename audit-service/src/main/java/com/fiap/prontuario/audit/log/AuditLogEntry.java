package com.fiap.prontuario.audit.log;

import java.time.Instant;

/** Uma linha da trilha de auditoria (tabela {@code audit_log}). */
public record AuditLogEntry(
        long id,
        String patientId,
        String eventType,
        Instant occurredAt,
        String professionalId,
        String unitId,
        String detail,
        String correlationId) {
}
