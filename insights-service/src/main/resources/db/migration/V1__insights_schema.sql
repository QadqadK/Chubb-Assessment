-- Insights read side. Flyway creates and targets the "insights" schema.
-- These tables are projections: they can always be rebuilt from Kafka/outbox history.

CREATE TABLE processed_events (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(60)  NOT NULL,
    claim_id     UUID         NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    payload      JSONB        NOT NULL
);
CREATE INDEX idx_processed_events_claim ON processed_events (claim_id);

CREATE TABLE claim_read_model (
    claim_id              UUID          PRIMARY KEY,
    reference             VARCHAR(30)   NOT NULL,
    market_code           VARCHAR(2)    NOT NULL,
    claim_type            VARCHAR(20)   NOT NULL,
    status                VARCHAR(30)   NOT NULL,
    currency              VARCHAR(3)    NOT NULL,
    claimed_amount        NUMERIC(15,2) NOT NULL,
    reserve_amount        NUMERIC(15,2),
    settlement_amount     NUMERIC(15,2),
    decision_outcome      VARCHAR(20),
    claimant_name         VARCHAR(150),
    assigned_officer_id   UUID,
    assigned_officer_name VARCHAR(150),
    submitted_at          TIMESTAMPTZ   NOT NULL,
    last_event_at         TIMESTAMPTZ   NOT NULL,
    decided_at            TIMESTAMPTZ,
    settled_at            TIMESTAMPTZ,
    closed_at             TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_claim_rm_market_status ON claim_read_model (market_code, status);
CREATE INDEX idx_claim_rm_officer ON claim_read_model (assigned_officer_id, status);
CREATE INDEX idx_claim_rm_open ON claim_read_model (submitted_at) WHERE closed_at IS NULL;

CREATE TABLE notification_log (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id       UUID         NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    recipient_role VARCHAR(20)  NOT NULL,
    message        VARCHAR(500) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_claim ON notification_log (claim_id);
