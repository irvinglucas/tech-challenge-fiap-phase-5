package com.fiap.prontuario.audit.log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Trilha de auditoria (issue #10): grava, de forma append-only, todo evento
 * consumido do topico patient-record-events, e permite consultar "quem
 * acessou/alterou o prontuario do paciente X" (issue #11).
 */
@ApplicationScoped
public class AuditLogWriter {

    private final DataSource dataSource;

    @Inject
    public AuditLogWriter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void append(String patientId, String eventType, Instant occurredAt,
            String professionalId, String unitId, String detail, String correlationId) {
        String sql = """
                INSERT INTO audit_log (patient_id, event_type, occurred_at, professional_id, unit_id, detail, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, eventType);
            statement.setTimestamp(3, Timestamp.from(occurredAt));
            statement.setString(4, professionalId);
            statement.setString(5, unitId);
            statement.setString(6, detail);
            statement.setString(7, correlationId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new AuditLogException("Falha ao gravar entrada de auditoria para o paciente " + patientId, e);
        }
    }

    /** Trilha completa (acessos e alteracoes) de um paciente, mais recente primeiro. */
    public List<AuditLogEntry> findByPatientId(String patientId) {
        String sql = """
                SELECT id, patient_id, event_type, occurred_at, professional_id, unit_id, detail, correlation_id
                FROM audit_log
                WHERE patient_id = ?
                ORDER BY occurred_at DESC, id DESC
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet rs = statement.executeQuery()) {
                List<AuditLogEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    entries.add(toEntry(rs));
                }
                return entries;
            }
        } catch (SQLException e) {
            throw new AuditLogException("Falha ao consultar auditoria do paciente " + patientId, e);
        }
    }

    /** Apenas os eventos de acesso (RecordAccessed/AccessDenied) de um paciente - "quem acessou X". */
    public List<AuditLogEntry> findAccessEventsByPatientId(String patientId) {
        String sql = """
                SELECT id, patient_id, event_type, occurred_at, professional_id, unit_id, detail, correlation_id
                FROM audit_log
                WHERE patient_id = ? AND event_type IN ('RecordAccessed', 'AccessDenied')
                ORDER BY occurred_at DESC, id DESC
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet rs = statement.executeQuery()) {
                List<AuditLogEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    entries.add(toEntry(rs));
                }
                return entries;
            }
        } catch (SQLException e) {
            throw new AuditLogException("Falha ao consultar acessos ao paciente " + patientId, e);
        }
    }

    private AuditLogEntry toEntry(ResultSet rs) throws SQLException {
        return new AuditLogEntry(
                rs.getLong("id"),
                rs.getString("patient_id"),
                rs.getString("event_type"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("professional_id"),
                rs.getString("unit_id"),
                rs.getString("detail"),
                rs.getString("correlation_id"));
    }
}
