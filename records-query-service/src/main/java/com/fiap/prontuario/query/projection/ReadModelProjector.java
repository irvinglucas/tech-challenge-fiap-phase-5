package com.fiap.prontuario.query.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.prontuario.common.event.AccessDenied;
import com.fiap.prontuario.common.event.AccessGranted;
import com.fiap.prontuario.common.event.AccessRevoked;
import com.fiap.prontuario.common.event.AllergyRegistered;
import com.fiap.prontuario.common.event.ConsultationRecorded;
import com.fiap.prontuario.common.event.DiagnosisAdded;
import com.fiap.prontuario.common.event.EventSerializationException;
import com.fiap.prontuario.common.event.ExamResultAttached;
import com.fiap.prontuario.common.event.PatientRecordEvent;
import com.fiap.prontuario.common.event.PatientRegistered;
import com.fiap.prontuario.common.event.PrescriptionIssued;
import com.fiap.prontuario.common.event.RecordAccessed;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Mantem as 3 projecoes de leitura descritas em docs/event-storming.md a
 * partir dos eventos consumidos de {@code patient-record-events} (issue #8):
 * {@code prontuario_consolidado}, {@code resumo_paciente} e {@code
 * pacientes_por_unidade}.
 *
 * <p>Nenhuma projecao e fonte da verdade - o event store do
 * records-command-service e; estas tabelas podem ser recriadas do zero
 * reprocessando o topico desde o offset inicial.
 */
@ApplicationScoped
public class ReadModelProjector {

    private static final int MAX_RECENT_PRESCRIPTIONS = 5;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Inject
    public ReadModelProjector(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    public void project(PatientRecordEvent event) {
        switch (event) {
            case PatientRegistered e -> onPatientRegistered(e);
            case ConsultationRecorded e -> appendToTimeline(e.patientId(), "ConsultationRecorded", e.occurredAt(),
                    e.professionalId(), e.unitId(), e);
            case DiagnosisAdded e -> {
                appendToTimeline(e.patientId(), "DiagnosisAdded", e.occurredAt(), e.professionalId(), e.unitId(), e);
                addActiveDiagnosis(e);
            }
            case PrescriptionIssued e -> {
                appendToTimeline(e.patientId(), "PrescriptionIssued", e.occurredAt(), e.professionalId(), e.unitId(), e);
                addRecentPrescription(e);
            }
            case AllergyRegistered e -> {
                appendToTimeline(e.patientId(), "AllergyRegistered", e.occurredAt(), e.professionalId(), e.unitId(), e);
                addOrUpdateAllergy(e);
            }
            case ExamResultAttached e -> appendToTimeline(e.patientId(), "ExamResultAttached", e.occurredAt(),
                    e.professionalId(), e.unitId(), e);
            case AccessGranted e -> onAccessGranted(e);
            case AccessRevoked e -> onAccessRevoked(e);
            case RecordAccessed e -> {
                // Nao afeta as projecoes de leitura; consumido pelo audit-service (issue #10).
            }
            case AccessDenied e -> {
                // Idem.
            }
        }
    }

    private void onPatientRegistered(PatientRegistered event) {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO resumo_paciente (patient_id, full_name, cpf, unit_id, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (patient_id) DO NOTHING
                    """)) {
                statement.setString(1, event.patientId());
                statement.setString(2, event.fullName());
                statement.setString(3, event.cpf());
                statement.setString(4, event.unitId());
                statement.setObject(5, toOffsetDateTime(event.occurredAt()));
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO pacientes_por_unidade (unit_id, patient_id, full_name, granted_via)
                    VALUES (?, ?, ?, 'REGISTRATION')
                    ON CONFLICT DO NOTHING
                    """)) {
                statement.setString(1, event.unitId());
                statement.setString(2, event.patientId());
                statement.setString(3, event.fullName());
                statement.executeUpdate();
            }
        });
        appendToTimeline(event.patientId(), "PatientRegistered", event.occurredAt(), null, event.unitId(), event);
    }

    private void onAccessGranted(AccessGranted event) {
        String fullName = fullNameOf(event.patientId());
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO pacientes_por_unidade (unit_id, patient_id, full_name, granted_via)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """)) {
                statement.setString(1, event.unitId());
                statement.setString(2, event.patientId());
                statement.setString(3, fullName == null ? event.patientId() : fullName);
                statement.setString(4, event.professionalId());
                statement.executeUpdate();
            }
        });
    }

    private void onAccessRevoked(AccessRevoked event) {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM pacientes_por_unidade WHERE patient_id = ? AND granted_via = ?
                    """)) {
                statement.setString(1, event.patientId());
                statement.setString(2, event.professionalId());
                statement.executeUpdate();
            }
        });
    }

    private void addActiveDiagnosis(DiagnosisAdded event) {
        updateSummaryList(event.patientId(), "active_diagnoses", DiagnosisEntry.class, list -> {
            list.add(new DiagnosisEntry(event.description(), event.cid10()));
            return list;
        });
    }

    private void addOrUpdateAllergy(AllergyRegistered event) {
        updateSummaryList(event.patientId(), "allergies", AllergyEntry.class, list -> {
            list.removeIf(entry -> entry.substance().equalsIgnoreCase(event.substance()));
            list.add(new AllergyEntry(event.substance(), event.severity()));
            return list;
        });
    }

    private void addRecentPrescription(PrescriptionIssued event) {
        updateSummaryList(event.patientId(), "last_prescriptions", PrescriptionEntry.class, list -> {
            list.add(new PrescriptionEntry(event.medication(), event.dosage(), event.occurredAt()));
            list.sort((a, b) -> b.occurredAt().compareTo(a.occurredAt()));
            return list.size() > MAX_RECENT_PRESCRIPTIONS ? new ArrayList<>(list.subList(0, MAX_RECENT_PRESCRIPTIONS)) : list;
        });
    }

    private <T> void updateSummaryList(String patientId, String column, Class<T> type,
            java.util.function.UnaryOperator<List<T>> mutation) {
        withConnection(connection -> {
            List<T> current = readJsonList(connection, patientId, column, type);
            List<T> updated = mutation.apply(new ArrayList<>(current));
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE resumo_paciente SET " + column + " = ?::jsonb, updated_at = ? WHERE patient_id = ?")) {
                statement.setString(1, writeJson(updated));
                statement.setObject(2, OffsetDateTime.now(ZoneOffset.UTC));
                statement.setString(3, patientId);
                statement.executeUpdate();
            }
        });
    }

    private <T> List<T> readJsonList(Connection connection, String patientId, String column, Class<T> type) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM resumo_paciente WHERE patient_id = ?")) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new ArrayList<>();
                }
                String json = resultSet.getString(1);
                return json == null ? new ArrayList<>() : readJson(json, type);
            }
        } catch (SQLException e) {
            throw new ProjectionException("Falha ao ler " + column + " de resumo_paciente", e);
        }
    }

    private void appendToTimeline(String patientId, String eventType, java.time.Instant occurredAt,
            String professionalId, String unitId, PatientRecordEvent event) {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO prontuario_consolidado (patient_id, event_type, occurred_at, professional_id, unit_id, payload)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb)
                    """)) {
                statement.setString(1, patientId);
                statement.setString(2, eventType);
                statement.setObject(3, toOffsetDateTime(occurredAt));
                statement.setString(4, professionalId);
                statement.setString(5, unitId);
                statement.setString(6, writeJson(event));
                statement.executeUpdate();
            }
        });
    }

    private String fullNameOf(String patientId) {
        return withConnectionReturning(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT full_name FROM resumo_paciente WHERE patient_id = ?")) {
                statement.setString(1, patientId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getString(1) : null;
                }
            }
        });
    }

    private OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new EventSerializationException("Falha ao serializar projecao para JSON", e);
        }
    }

    private <T> List<T> readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            throw new EventSerializationException("Falha ao desserializar projecao JSON", e);
        }
    }

    private void withConnection(SqlAction action) {
        try (Connection connection = dataSource.getConnection()) {
            action.run(connection);
        } catch (SQLException e) {
            throw new ProjectionException("Falha ao atualizar projecao de leitura", e);
        }
    }

    private <T> T withConnectionReturning(SqlFunction<T> function) {
        try (Connection connection = dataSource.getConnection()) {
            return function.run(connection);
        } catch (SQLException e) {
            throw new ProjectionException("Falha ao consultar projecao de leitura", e);
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T run(Connection connection) throws SQLException;
    }
}
