package com.fiap.prontuario.query.web;

import java.time.Instant;

public record TimelineEntryResponse(
        String eventType,
        Instant occurredAt,
        String professionalId,
        String unitId,
        String payload) {
}
