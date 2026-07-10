package com.fiap.prontuario.audit.web;

import com.fiap.prontuario.audit.anomaly.SuspiciousAccessAlert;

import java.time.Instant;
import java.util.List;

public record SuspiciousAccessAlertResponse(
        long id,
        String professionalId,
        Instant windowStart,
        Instant windowEnd,
        int distinctPatients,
        int distinctUnits,
        List<String> patientIds,
        List<String> unitIds,
        Instant detectedAt) {

    public static SuspiciousAccessAlertResponse from(SuspiciousAccessAlert alert) {
        return new SuspiciousAccessAlertResponse(
                alert.id(), alert.professionalId(), alert.windowStart(), alert.windowEnd(),
                alert.distinctPatients(), alert.distinctUnits(), alert.patientIds(), alert.unitIds(),
                alert.detectedAt());
    }
}
