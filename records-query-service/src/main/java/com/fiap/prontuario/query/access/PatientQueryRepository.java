package com.fiap.prontuario.query.access;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Consultas JDBC contra as projecoes de leitura mantidas pelo {@code
 * ReadModelProjector} (issue #8), usadas pelos endpoints de consulta
 * (issue #9).
 */
@ApplicationScoped
public class PatientQueryRepository {

    private final DataSource dataSource;

    @Inject
    public PatientQueryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** @return true se o profissional tem um acesso concedido ativo para este paciente. */
    public boolean isAuthorized(String patientId, String professionalId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT 1 FROM pacientes_por_unidade WHERE patient_id = ? AND granted_via = ?
                        """)) {
            statement.setString(1, patientId);
            statement.setString(2, professionalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new QueryException("Falha ao verificar autorizacao de acesso", e);
        }
    }

    public boolean exists(String patientId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM resumo_paciente WHERE patient_id = ?")) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new QueryException("Falha ao verificar existencia do paciente", e);
        }
    }

    public List<TimelineEntry> findTimeline(String patientId) {
        List<TimelineEntry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT event_type, occurred_at, professional_id, unit_id, payload::text
                        FROM prontuario_consolidado
                        WHERE patient_id = ?
                        ORDER BY occurred_at ASC, id ASC
                        """)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new TimelineEntry(
                            resultSet.getString("event_type"),
                            resultSet.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant(),
                            resultSet.getString("professional_id"),
                            resultSet.getString("unit_id"),
                            resultSet.getString("payload")));
                }
            }
        } catch (SQLException e) {
            throw new QueryException("Falha ao consultar prontuario_consolidado", e);
        }
        return entries;
    }

    public Optional<PatientSummary> findSummary(String patientId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT patient_id, full_name, cpf, unit_id,
                               allergies::text, active_diagnoses::text, last_prescriptions::text, updated_at
                        FROM resumo_paciente
                        WHERE patient_id = ?
                        """)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PatientSummary(
                        resultSet.getString("patient_id"),
                        resultSet.getString("full_name"),
                        resultSet.getString("cpf"),
                        resultSet.getString("unit_id"),
                        resultSet.getString("allergies"),
                        resultSet.getString("active_diagnoses"),
                        resultSet.getString("last_prescriptions"),
                        resultSet.getObject("updated_at", java.time.OffsetDateTime.class).toInstant()));
            }
        } catch (SQLException e) {
            throw new QueryException("Falha ao consultar resumo_paciente", e);
        }
    }

    public List<PatientByUnit> findPatientsByUnit(String unitId) {
        List<PatientByUnit> patients = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT DISTINCT patient_id, full_name FROM pacientes_por_unidade
                        WHERE unit_id = ?
                        ORDER BY full_name ASC
                        """)) {
            statement.setString(1, unitId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    patients.add(new PatientByUnit(resultSet.getString("patient_id"), resultSet.getString("full_name")));
                }
            }
        } catch (SQLException e) {
            throw new QueryException("Falha ao consultar pacientes_por_unidade", e);
        }
        return patients;
    }
}
