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

## ADR-009: Stateless JWT auth with seeded users
`POST /auth/login` verifies a BCrypt password and issues an HS256 JWT. The token carries the user id (`sub`),
role, market and name. Spring Security's resource-server filter validates it; roles become `ROLE_*` authorities.
A real identity provider is out of scope, but replacing the token issuer does not change the API contract.

## ADR-010: Ownership violations return 404
Claimant endpoints load the claim and compare `claimantId` with the token subject. A mismatch returns 404, not
403, so a claimant cannot use responses to discover whether another person's claim ID exists.

## ADR-011: Information request completion
A claim can have several open information requests. Answering one records the answer but keeps the claim in
`INFO_REQUESTED` until the last open request is answered, then transitions it back to `UNDER_REVIEW`.

## ADR-012: Testcontainers version override
Spring Boot 3.3.5 manages Testcontainers 1.19.8, which is incompatible with Docker Engine 29 (API 1.56).
The POM overrides `testcontainers.version` to 1.21.4 so container-backed tests run locally.

## ADR-013: Officer queue is market-scoped and paged
The queue only returns unassigned claims in the officer's market. It accepts an optional status filter and uses
the shared `PageResponse` envelope, ordered oldest first. Officers cannot query another market through the
`market` parameter; that is reserved for a future manager view.

## ADR-014: Pickup is atomic, commands are assignment-scoped
Assignment requires `SUBMITTED` with no assignee. The claim's `@Version` column makes the pickup a lost-update
guard: if two officers race, one commit wins and the other receives 409. Every later command requires the
calling officer to be the assignee; viewing a same-market claim is allowed before pickup.

## ADR-015: Assessment drives exposure; decision defaults settlement
Recording an assessment copies the assessed amount into `reserveAmount`, so exposure is the sum of reserves on
open claims. An approval defaults its settlement amount to the latest assessment when the officer does not supply
one; a rejection has no settlement amount. Settlement closes the claim.

## ADR-016: Transactional outbox, one topic per event type
The claim update and the `outbox_events` insert share one transaction, so there is no dual-write window. A poller
publishes to seven event topics (`claim.submitted`, `claim.assigned`, `claim.info-requested`,
`claim.info-provided`, `claim.assessed`, `claim.decided`, `claim.settled`). One topic per event type keeps
subscriptions explicit; a single `claims.events` topic with an event-type header was the alternative.

## ADR-017: At-least-once delivery with per-claim ordering
The poller waits for the broker acknowledgement before marking a row published and stops a batch at the first
failure, so later events for the same claim cannot overtake an earlier one. A crash between publish and mark
re-sends the event; consumers must deduplicate on `eventId`. The Kafka message key is the claim id, which also
pins a claim's events to one partition.

## ADR-018: Versioned event envelope
Each payload has `eventId`, `eventType`, `schemaVersion: 1`, `occurredAt`, a claim snapshot (reference, market,
type, status, currency, claimed/reserve amounts, assignee) and an event-specific `data` map. Consumers can build
read models without calling back, and the version field gives a migration path.

## ADR-019: Document uploads are timeline-only
A document upload records a claim event but has no Kafka topic, because no other component reacts to it and
insights does not need it for workload or exposure.
