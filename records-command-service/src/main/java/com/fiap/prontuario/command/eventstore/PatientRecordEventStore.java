package com.fiap.prontuario.command.eventstore;

import com.fiap.prontuario.common.event.PatientRecordEvent;
import com.fiap.prontuario.common.event.PatientRecordEventCodec;

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
 * Event store do agregado PatientRecord: tabela append-only {@code
 * event_store} (ver db/migration/V1__create_event_store.sql), com uma
 * constraint UNIQUE (patient_id, version) que e a base do controle de
 * concorrencia otimista - duas gravacoes concorrentes para a mesma versao
 * do mesmo paciente resultam em uma violacao de unicidade, traduzida aqui
 * para {@link ConcurrencyConflictException}.
 */
@ApplicationScoped
public class PatientRecordEventStore {

    private static final String INSERT_SQL = """
            INSERT INTO event_store (event_id, patient_id, version, event_type, payload, occurred_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT event_type, payload
            FROM event_store
            WHERE patient_id = ?
            ORDER BY version ASC
            """;

    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

    private final DataSource dataSource;
    private final PatientRecordEventCodec codec;

    @Inject
    public PatientRecordEventStore(DataSource dataSource, PatientRecordEventCodec codec) {
        this.dataSource = dataSource;
        this.codec = codec;
    }

    /**
     * Grava um novo evento para o paciente, assumindo que {@code
     * expectedVersion} e a versao do agregado no momento em que o comando
     * foi validado (tipicamente {@code PatientRecord#version()}).
     *
     * @return a nova versao do agregado ({@code expectedVersion + 1}) apos a gravacao.
     * @throws ConcurrencyConflictException se outra gravacao concorrente ja tiver avancado a versao.
     */
    public int append(String patientId, int expectedVersion, PatientRecordEvent event) {
        int newVersion = expectedVersion + 1;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setObject(1, event.eventId());
            statement.setString(2, patientId);
            statement.setInt(3, newVersion);
            statement.setString(4, codec.typeOf(event));
            statement.setString(5, codec.toJson(event));
            statement.setObject(6, OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC));
            statement.executeUpdate();
            return newVersion;
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
                throw new ConcurrencyConflictException(patientId, expectedVersion);
            }
            throw new EventStoreException("Falha ao gravar evento no event store", e);
        }
    }

    /** Carrega todos os eventos do paciente, em ordem de versao, para reconstrucao do agregado. */
    public List<PatientRecordEvent> loadEvents(String patientId) {
        List<PatientRecordEvent> events = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(codec.fromJson(resultSet.getString("event_type"), resultSet.getString("payload")));
                }
            }
        } catch (SQLException e) {
            throw new EventStoreException("Falha ao carregar eventos do event store", e);
        }
        return events;
    }
}
