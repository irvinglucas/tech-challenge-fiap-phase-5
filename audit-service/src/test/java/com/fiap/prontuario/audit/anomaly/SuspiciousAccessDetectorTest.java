package com.fiap.prontuario.audit.anomaly;

import com.fiap.prontuario.audit.log.AuditLogWriter;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a regra de deteccao de acesso anomalo (issue #11): um profissional
 * acessando muitos pacientes distintos em mais de uma unidade numa janela
 * curta de tempo dispara um alerta; um padrao normal de acesso nao.
 */
@QuarkusTest
class SuspiciousAccessDetectorTest {

    @Inject
    AuditLogWriter auditLogWriter;

    @Inject
    SuspiciousAccessDetector detector;

    @Test
    void flags_a_professional_accessing_many_patients_across_multiple_units_in_a_short_window() {
        String professionalId = "prof-" + UUID.randomUUID();
        Instant now = Instant.now();

        recordAccess(professionalId, "patient-a-" + UUID.randomUUID(), "unit-1", now);
        recordAccess(professionalId, "patient-b-" + UUID.randomUUID(), "unit-2", now);
        recordAccess(professionalId, "patient-c-" + UUID.randomUUID(), "unit-3", now);

        detector.checkAfterAccess(professionalId, now);

        List<SuspiciousAccessAlert> alerts = detector.findAll();
        assertThat(alerts).anySatisfy(alert -> {
            assertThat(alert.professionalId()).isEqualTo(professionalId);
            assertThat(alert.distinctPatients()).isGreaterThanOrEqualTo(3);
            assertThat(alert.distinctUnits()).isGreaterThanOrEqualTo(2);
        });
    }

    @Test
    void does_not_flag_normal_access_to_a_single_unit() {
        String professionalId = "prof-" + UUID.randomUUID();
        Instant now = Instant.now();

        recordAccess(professionalId, "patient-a-" + UUID.randomUUID(), "unit-1", now);
        recordAccess(professionalId, "patient-b-" + UUID.randomUUID(), "unit-1", now);

        detector.checkAfterAccess(professionalId, now);

        List<SuspiciousAccessAlert> alerts = detector.findAll();
        assertThat(alerts).noneMatch(alert -> alert.professionalId().equals(professionalId));
    }

    private void recordAccess(String professionalId, String patientId, String unitId, Instant occurredAt) {
        auditLogWriter.append(patientId, "RecordAccessed", occurredAt, professionalId, unitId,
                "Prontuario consultado", "corr-" + UUID.randomUUID());
    }
}
