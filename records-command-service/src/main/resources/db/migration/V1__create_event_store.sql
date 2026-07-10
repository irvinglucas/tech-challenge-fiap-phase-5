-- Event store do agregado PatientRecord (append-only).
--
-- A constraint UNIQUE (patient_id, version) e a base do controle de
-- concorrencia otimista: duas gravacoes concorrentes tentando criar a
-- mesma versao para o mesmo paciente resultam em violacao de unicidade
-- (SQLState 23505), traduzida pela aplicacao em ConcurrencyConflictException.
CREATE TABLE event_store (
    event_id    UUID PRIMARY KEY,
    patient_id  VARCHAR(64)   NOT NULL,
    version     INT           NOT NULL,
    event_type  VARCHAR(100)  NOT NULL,
    payload     JSONB         NOT NULL,
    occurred_at TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uq_event_store_patient_version UNIQUE (patient_id, version)
);

CREATE INDEX idx_event_store_patient_id ON event_store (patient_id);
