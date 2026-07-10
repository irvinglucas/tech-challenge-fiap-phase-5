package com.fiap.prontuario.audit.anomaly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.prontuario.audit.log.AuditLogException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

/**
 * Deteccao de acesso anomalo (issue #11, evento derivado SuspiciousAccessDetected
 * do event-storming): dispara um alerta quando um mesmo profissional acessa
 * muitos pacientes distintos, em mais de uma unidade, numa janela curta de
 * tempo.
 *
 * <p>Simplificacao de MVP: o dominio nao possui um cadastro de
 * profissionais/unidades, entao nao ha como saber a "unidade de origem" de um
 * profissional para comparar com a unidade de acesso. Por isso "fora da sua
 * unidade" e aproximado por "mais de uma unidade distinta na mesma janela" -
 * um profissional acessando varios pacientes de varias unidades diferentes em
 * poucos minutos e um forte indicio de uso indevido de credenciais ou de
 * acesso fora do fluxo normal de trabalho.
 */
@ApplicationScoped
public class SuspiciousAccessDetector {

    private static final Logger LOG = Logger.getLogger(SuspiciousAccessDetector.class);

    private static final int WINDOW_MINUTES = 5;
    private static final int MIN_DISTINCT_PATIENTS = 3;
    private static final int MIN_DISTINCT_UNITS = 2;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Inject
    public SuspiciousAccessDetector(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    /** Chamado apos cada RecordAccessed gravado na trilha de auditoria. */
    public void checkAfterAccess(String professionalId, Instant occurredAt) {
        Instant windowStart = occurredAt.minus(WINDOW_MINUTES, ChronoUnit.MINUTES);

        Set<String> patientIds = new LinkedHashSet<>();
        Set<String> unitIds = new LinkedHashSet<>();
        loadRecentAccesses(professionalId, windowStart, occurredAt, patientIds, unitIds);

        if (patientIds.size() < MIN_DISTINCT_PATIENTS || unitIds.size() < MIN_DISTINCT_UNITS) {
            return;
        }
        if (alreadyAlertedRecently(professionalId, windowStart)) {
            return;
        }

        LOG.warnf("Acesso anomalo detectado: profissional=%s acessou %d pacientes em %d unidades nos ultimos %d minutos",
                professionalId, patientIds.size(), unitIds.size(), WINDOW_MINUTES);
        insertAlert(professionalId, windowStart, occurredAt, patientIds, unitIds);
    }

    private void loadRecentAccesses(String professionalId, Instant windowStart, Instant windowEnd,
            Set<String> patientIds, Set<String> unitIds) {
        String sql = """
                SELECT DISTINCT patient_id, unit_id
                FROM audit_log
                WHERE professional_id = ? AND event_type = 'RecordAccessed'
                  AND occurred_at >= ? AND occurred_at <= ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, professionalId);
            statement.setTimestamp(2, Timestamp.from(windowStart));
            statement.setTimestamp(3, Timestamp.from(windowEnd));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    patientIds.add(rs.getString("patient_id"));
                    String unitId = rs.getString("unit_id");
                    if (unitId != null) {
                        unitIds.add(unitId);
                    }
                }
            }
        } catch (SQLException e) {
            throw new AuditLogException("Falha ao verificar acesso anomalo para o profissional " + professionalId, e);
        }
    }

    private boolean alreadyAlertedRecently(String professionalId, Instant windowStart) {
        String sql = """
                SELECT COUNT(*) FROM suspicious_access_alert
                WHERE professional_id = ? AND detected_at >= ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, professionalId);
            statement.setTimestamp(2, Timestamp.from(windowStart));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new AuditLogException("Falha ao verificar alertas existentes para o profissional " + professionalId, e);
        }
    }

    private void insertAlert(String professionalId, Instant windowStart, Instant windowEnd,
            Set<String> patientIds, Set<String> unitIds) {
        String sql = """
                INSERT INTO suspicious_access_alert
                    (professional_id, window_start, window_end, distinct_patients, distinct_units, patient_ids, unit_ids)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, professionalId);
            statement.setTimestamp(2, Timestamp.from(windowStart));
            statement.setTimestamp(3, Timestamp.from(windowEnd));
            statement.setInt(4, patientIds.size());
            statement.setInt(5, unitIds.size());
            statement.setString(6, toJsonArray(patientIds));
            statement.setString(7, toJsonArray(unitIds));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new AuditLogException("Falha ao gravar alerta de acesso anomalo para o profissional " + professionalId, e);
        }
    }

    /** Todos os alertas registrados, mais recente primeiro - "consulta de auditoria/alertas" (issue #11). */
    public List<SuspiciousAccessAlert> findAll() {
        String sql = """
                SELECT id, professional_id, window_start, window_end, distinct_patients, distinct_units,
                       patient_ids, unit_ids, detected_at
                FROM suspicious_access_alert
                ORDER BY detected_at DESC, id DESC
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            List<SuspiciousAccessAlert> alerts = new ArrayList<>();
            while (rs.next()) {
                alerts.add(new SuspiciousAccessAlert(
                        rs.getLong("id"),
                        rs.getString("professional_id"),
                        rs.getTimestamp("window_start").toInstant(),
                        rs.getTimestamp("window_end").toInstant(),
                        rs.getInt("distinct_patients"),
                        rs.getInt("distinct_units"),
                        fromJsonArray(rs.getString("patient_ids")),
                        fromJsonArray(rs.getString("unit_ids")),
                        rs.getTimestamp("detected_at").toInstant()));
            }
            return alerts;
        } catch (SQLException e) {
            throw new AuditLogException("Falha ao consultar alertas de acesso anomalo", e);
        }
    }

    private String toJsonArray(Set<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new AuditLogException("Falha ao serializar lista para JSON", e);
        }
    }

    private List<String> fromJsonArray(String json) {
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (Exception e) {
            throw new AuditLogException("Falha ao desserializar lista JSON", e);
        }
    }
}
