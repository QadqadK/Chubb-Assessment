-- Claims service schema. Flyway creates and targets the "claims" schema (see application.yml).
-- Aggregates reference each other by ID; FKs are enforced here at the DB level.

CREATE TABLE markets (
    code      VARCHAR(2)   PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    currency  VARCHAR(3)   NOT NULL
);

CREATE TABLE teams (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    market_code VARCHAR(2)   NOT NULL REFERENCES markets (code)
);

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CLAIMANT', 'OFFICER', 'MANAGER')),
    market_code   VARCHAR(2)   NOT NULL REFERENCES markets (code),
    team_id       UUID         REFERENCES teams (id),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE policies (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_number  VARCHAR(30)   NOT NULL UNIQUE,
    holder_id      UUID          NOT NULL REFERENCES users (id),
    policy_type    VARCHAR(20)   NOT NULL CHECK (policy_type IN ('MOTOR', 'PROPERTY')),
    market_code    VARCHAR(2)    NOT NULL REFERENCES markets (code),
    currency       VARCHAR(3)    NOT NULL,
    coverage_limit NUMERIC(15,2) NOT NULL CHECK (coverage_limit > 0),
    start_date     DATE          NOT NULL,
    end_date       DATE          NOT NULL,
    CHECK (end_date > start_date)
);
CREATE INDEX idx_policies_holder ON policies (holder_id);

CREATE SEQUENCE claim_reference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE claims (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    reference           VARCHAR(30)   NOT NULL UNIQUE,
    policy_id           UUID          NOT NULL REFERENCES policies (id),
    claimant_id         UUID          NOT NULL REFERENCES users (id),
    market_code         VARCHAR(2)    NOT NULL REFERENCES markets (code),
    claim_type          VARCHAR(20)   NOT NULL CHECK (claim_type IN ('MOTOR', 'PROPERTY')),
    status              VARCHAR(30)   NOT NULL CHECK (status IN
                          ('SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'ASSESSED',
                           'APPROVED', 'REJECTED', 'SETTLED')),
    incident_date       DATE          NOT NULL,
    incident_location   VARCHAR(255),
    description         VARCHAR(4000) NOT NULL,
    incident_details    JSONB         NOT NULL DEFAULT '{}'::jsonb,
    currency            VARCHAR(3)    NOT NULL,
    claimed_amount      NUMERIC(15,2) NOT NULL CHECK (claimed_amount >= 0),
    reserve_amount      NUMERIC(15,2) CHECK (reserve_amount >= 0),
    assigned_officer_id UUID          REFERENCES users (id),
    submitted_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    closed_at           TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX idx_claims_status_market ON claims (status, market_code);
CREATE INDEX idx_claims_claimant      ON claims (claimant_id);
CREATE INDEX idx_claims_officer       ON claims (assigned_officer_id, status);

CREATE TABLE info_requests (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id     UUID          NOT NULL REFERENCES claims (id),
    requested_by UUID          NOT NULL REFERENCES users (id),
    question     VARCHAR(2000) NOT NULL,
    response     VARCHAR(4000),
    status       VARCHAR(20)   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ANSWERED')),
    requested_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ
);
CREATE INDEX idx_info_requests_claim ON info_requests (claim_id);

CREATE TABLE assessments (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID          NOT NULL REFERENCES claims (id),
    officer_id      UUID          NOT NULL REFERENCES users (id),
    notes           VARCHAR(4000) NOT NULL,
    assessed_amount NUMERIC(15,2) NOT NULL CHECK (assessed_amount >= 0),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_assessments_claim ON assessments (claim_id);

CREATE TABLE decisions (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id          UUID          NOT NULL UNIQUE REFERENCES claims (id),  -- one decision per claim
    officer_id        UUID          NOT NULL REFERENCES users (id),
    outcome           VARCHAR(20)   NOT NULL CHECK (outcome IN ('APPROVED', 'REJECTED')),
    reason            VARCHAR(2000) NOT NULL,
    settlement_amount NUMERIC(15,2) CHECK (settlement_amount >= 0),
    decided_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE documents (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id     UUID         NOT NULL REFERENCES claims (id),
    uploaded_by  UUID         NOT NULL REFERENCES users (id),
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL CHECK (size_bytes >= 0),
    storage_path VARCHAR(500) NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_claim ON documents (claim_id);

-- Claimant-visible timeline and audit trail of every claim change.
CREATE TABLE claim_events (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id    UUID         NOT NULL REFERENCES claims (id),
    event_type  VARCHAR(50)  NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30),
    actor_id    UUID         REFERENCES users (id),
    note        VARCHAR(2000),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_claim_events_claim ON claim_events (claim_id, occurred_at);

-- Transactional outbox (Phase 5): rows are written with the state change and published to Kafka by a poller.
CREATE TABLE outbox_events (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID        NOT NULL,
    event_type   VARCHAR(60) NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
