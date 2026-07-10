package com.fiap.prontuario.audit.anomaly;

import java.time.Instant;
import java.util.List;

/** Um alerta de acesso anomalo (tabela {@code suspicious_access_alert}). */
public record SuspiciousAccessAlert(
        long id,
        String professionalId,
        Instant windowStart,
        Instant windowEnd,
        int distinctPatients,
        int distinctUnits,
        List<String> patientIds,
        List<String> unitIds,
        Instant detectedAt) {
}
