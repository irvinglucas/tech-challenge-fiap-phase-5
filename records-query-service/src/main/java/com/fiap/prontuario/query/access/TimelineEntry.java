package com.fiap.prontuario.query.access;

import java.time.Instant;

public record TimelineEntry(
        String eventType,
        Instant occurredAt,
        String professionalId,
        String unitId,
        String payload) {
}
