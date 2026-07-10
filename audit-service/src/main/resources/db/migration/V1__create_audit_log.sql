-- Trilha de auditoria (issue #10): registro append-only de todo evento do
-- topico patient-record-events (acessos e alteracoes do prontuario). Nunca e
-- atualizado nem apagado, apenas inserido - e a fonte da verdade para "quem
-- acessou/alterou o que, quando e de qual unidade" (issue #11).
CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    patient_id      VARCHAR(64)   NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    professional_id VARCHAR(64),
    unit_id         VARCHAR(64),
    detail          VARCHAR(500),
    correlation_id  VARCHAR(64),
    recorded_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_patient_id ON audit_log (patient_id, occurred_at);
CREATE INDEX idx_audit_log_professional_id ON audit_log (professional_id, occurred_at);

-- Alertas de acesso anomalo (issue #11): um profissional acessando muitos
-- pacientes distintos, em mais de uma unidade, numa janela curta de tempo.
-- Sem um servico de cadastro de profissionais/unidades no MVP, "fora da sua
-- unidade" e aproximado por "unidades diferentes na mesma janela" (ver
-- SuspiciousAccessDetector).
CREATE TABLE suspicious_access_alert (
    id                    BIGSERIAL PRIMARY KEY,
    professional_id       VARCHAR(64)  NOT NULL,
    window_start          TIMESTAMPTZ  NOT NULL,
    window_end            TIMESTAMPTZ  NOT NULL,
    distinct_patients     INT          NOT NULL,
    distinct_units        INT          NOT NULL,
    patient_ids           JSONB        NOT NULL,
    unit_ids              JSONB        NOT NULL,
    detected_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_suspicious_access_alert_professional_id ON suspicious_access_alert (professional_id, detected_at);
