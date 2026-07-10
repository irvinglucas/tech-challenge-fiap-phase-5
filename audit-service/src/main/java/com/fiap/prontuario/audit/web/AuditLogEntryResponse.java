package com.fiap.prontuario.audit.web;

import com.fiap.prontuario.audit.log.AuditLogEntry;

import java.time.Instant;

public record AuditLogEntryResponse(
        long id,
        String patientId,
        String eventType,
        Instant occurredAt,
        String professionalId,
        String unitId,
        String detail,
        String correlationId) {

    public static AuditLogEntryResponse from(AuditLogEntry entry) {
        return new AuditLogEntryResponse(
                entry.id(), entry.patientId(), entry.eventType(), entry.occurredAt(),
                entry.professionalId(), entry.unitId(), entry.detail(), entry.correlationId());
    }
}
