package com.fiap.prontuario.query.projection;

import java.time.Instant;

public record PrescriptionEntry(String medication, String dosage, Instant occurredAt) {
}
