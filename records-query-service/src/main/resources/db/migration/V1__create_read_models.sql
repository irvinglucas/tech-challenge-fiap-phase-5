-- Projecoes de leitura (CQRS) mantidas pelo consumer de
-- patient-record-events (issue #8). Nenhuma delas e a fonte da verdade:
-- podem ser recriadas do zero reprocessando o topico desde o inicio.

-- Timeline clinica completa por paciente (um registro por evento clinico).
CREATE TABLE prontuario_consolidado (
    id              BIGSERIAL PRIMARY KEY,
    patient_id      VARCHAR(64)   NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    professional_id VARCHAR(64),
    unit_id         VARCHAR(64),
    payload         JSONB         NOT NULL
);

CREATE INDEX idx_prontuario_consolidado_patient_id ON prontuario_consolidado (patient_id, occurred_at);

-- Resumo rapido por paciente: alergias, diagnosticos ativos e ultimas prescricoes.
CREATE TABLE resumo_paciente (
    patient_id        VARCHAR(64)  PRIMARY KEY,
    full_name         VARCHAR(200) NOT NULL,
    cpf               VARCHAR(64)  NOT NULL,
    unit_id           VARCHAR(64)  NOT NULL,
    allergies         JSONB        NOT NULL DEFAULT '[]',
    active_diagnoses  JSONB        NOT NULL DEFAULT '[]',
    last_prescriptions JSONB       NOT NULL DEFAULT '[]',
    updated_at        TIMESTAMPTZ  NOT NULL
);

-- Quais pacientes aparecem para cada unidade: pelo registro original ou por
-- um acesso concedido a um profissional daquela unidade (ver docs/event-storming.md).
CREATE TABLE pacientes_por_unidade (
    unit_id     VARCHAR(64)  NOT NULL,
    patient_id  VARCHAR(64)  NOT NULL,
    full_name   VARCHAR(200) NOT NULL,
    granted_via VARCHAR(64)  NOT NULL, -- 'REGISTRATION' ou o professionalId que recebeu o acesso

    PRIMARY KEY (unit_id, patient_id, granted_via)
);

CREATE INDEX idx_pacientes_por_unidade_unit_id ON pacientes_por_unidade (unit_id);
