# Architecture decisions

## ADR-001: Two services, not one or six
`claims-service` is the command side (strict consistency, state machine, REST). `insights-service` is the
read side (workload and exposure projections, notifications, audit), fed by Kafka. They have different
consistency and scaling needs, and the split gives Kafka a real job. A modular monolith was a valid
alternative; six services would cost time for no gain at this scale.

## ADR-002: REST vs Kafka
If the caller needs the result now or needs to be told "no", use REST. If something happened that other
parts of the system react to, use a Kafka event. All state-changing commands are REST (they validate the
transition and respond immediately). Notifications, dashboards and audit are Kafka-driven.

## ADR-003: One PostgreSQL instance, one schema per service
`claims` schema now, `insights` schema later. Services never read each other's tables.

## ADR-004: Aggregates reference each other by ID
Entities hold foreign keys as plain UUIDs rather than JPA associations. Avoids lazy-loading surprises and
N+1 queries, and keeps aggregate boundaries explicit.

## ADR-005: Type-specific incident data in JSONB
Motor (vehicle, plate, third party) and property (address, damage type) details differ. A single JSONB
`incident_details` column avoids two near-empty tables. Trade-off: weaker DB-level constraints; validate in the API layer.

## ADR-006: Money
`NUMERIC(15,2)` plus an explicit currency per claim and policy. No silent FX conversion; exposure is reported
per currency. FX conversion is out of scope.

## ADR-007: Transactional outbox (Phase 5)
Events are written to `outbox_events` in the same transaction as the state change, then published to Kafka by
a poller. Avoids the dual-write problem when Kafka is unavailable.

## ADR-008: Migrations are immutable
Flyway migrations are never edited after commit; changes go in a new `V<n>` file. Entities are validated against
the schema at startup (`ddl-auto: validate`).
