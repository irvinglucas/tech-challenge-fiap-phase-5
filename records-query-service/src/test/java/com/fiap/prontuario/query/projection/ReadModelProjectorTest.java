package com.fiap.prontuario.query.projection;

import com.fiap.prontuario.common.event.AccessGranted;
import com.fiap.prontuario.common.event.AccessRevoked;
import com.fiap.prontuario.common.event.AllergyRegistered;
import com.fiap.prontuario.common.event.DiagnosisAdded;
import com.fiap.prontuario.common.event.PatientRegistered;
import com.fiap.prontuario.common.event.PrescriptionIssued;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa as 3 projecoes de leitura mantidas pelo {@link ReadModelProjector}
 * (issue #8) contra um Postgres real (Quarkus Dev Services).
 */
@QuarkusTest
class ReadModelProjectorTest {

    @Inject
    ReadModelProjector projector;

    @Inject
    DataSource dataSource;

    private static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    @Test
    void registering_a_patient_creates_the_summary_and_the_unit_listing() throws Exception {
        String patientId = "patient-" + UUID.randomUUID();

        projector.project(new PatientRegistered(UUID.randomUUID(), patientId, NOW, "Maria Silva", patientId, "unit-1"));

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT full_name, unit_id FROM resumo_paciente WHERE patient_id = ?")) {
                statement.setString(1, patientId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("full_name")).isEqualTo("Maria Silva");
                    assertThat(resultSet.getString("unit_id")).isEqualTo("unit-1");
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT granted_via FROM pacientes_por_unidade WHERE unit_id = ? AND patient_id = ?")) {
                statement.setString(1, "unit-1");
                statement.setString(2, patientId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("granted_via")).isEqualTo("REGISTRATION");
                }
            }

            assertThat(countTimelineRows(connection, patientId)).isEqualTo(1);
        }
    }

    @Test
    void clinical_events_update_the_timeline_and_the_summary() throws Exception {
        String patientId = "patient-" + UUID.randomUUID();
        projector.project(new PatientRegistered(UUID.randomUUID(), patientId, NOW, "Joao Souza", patientId, "unit-1"));

        projector.project(new DiagnosisAdded(UUID.randomUUID(), patientId, NOW, "prof-1", "unit-1", "Diabetes", "E11"));
        projector.project(new AllergyRegistered(UUID.randomUUID(), patientId, NOW, "prof-1", "unit-1", "Dipirona", "ALTA"));
        projector.project(new PrescriptionIssued(UUID.randomUUID(), patientId, NOW, "prof-1", "unit-1", "Metformina", "850mg"));

        try (Connection connection = dataSource.getConnection()) {
            assertThat(countTimelineRows(connection, patientId)).isEqualTo(4);

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT active_diagnoses, allergies, last_prescriptions FROM resumo_paciente WHERE patient_id = ?")) {
                statement.setString(1, patientId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("active_diagnoses")).contains("Diabetes", "E11");
                    assertThat(resultSet.getString("allergies")).contains("Dipirona", "ALTA");
                    assertThat(resultSet.getString("last_prescriptions")).contains("Metformina", "850mg");
                }
            }
        }
    }

    @Test
    void access_granted_then_revoked_updates_the_unit_listing() throws Exception {
        String patientId = "patient-" + UUID.randomUUID();
        projector.project(new PatientRegistered(UUID.randomUUID(), patientId, NOW, "Ana Costa", patientId, "unit-1"));
        projector.project(new AccessGranted(UUID.randomUUID(), patientId, NOW, "gestor-1", "prof-2", "unit-2"));

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM pacientes_por_unidade WHERE unit_id = ? AND patient_id = ? AND granted_via = ?")) {
                statement.setString(1, "unit-2");
                statement.setString(2, patientId);
                statement.setString(3, "prof-2");
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                }
            }
        }

        projector.project(new AccessRevoked(UUID.randomUUID(), patientId, NOW, "gestor-1", "prof-2"));

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM pacientes_por_unidade WHERE unit_id = ? AND patient_id = ? AND granted_via = ?")) {
                statement.setString(1, "unit-2");
                statement.setString(2, patientId);
                statement.setString(3, "prof-2");
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isFalse();
                }
            }
        }
    }

    private int countTimelineRows(Connection connection, String patientId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM prontuario_consolidado WHERE patient_id = ?")) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
