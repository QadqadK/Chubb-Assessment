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

## ADR-020: Insights is a rebuildable projection
`insights-service` owns the `insights` schema and never reads the claims tables. It consumes all seven topics into
`claim_read_model`, keyed by claim id, and records every consumed event in `processed_events` with a unique
`eventId`. That makes replay safe (deduplicate) and lets the read side be dropped and rebuilt from Kafka.
Because the events are on separate topics, cross-topic delivery can reorder; each event carries `occurredAt`, so
the projector ignores an event older than the claim's `last_event_at` while still filling immutable gaps
(submission time, claimant name, officer). A single topic with an event-type header would remove this
complication; the projector is the cheaper alternative that keeps the explicit topic subscriptions.

## ADR-021: Manager dashboard asks allocation questions
`GET /manager/dashboard` is served entirely from the read model. It answers four questions: where is the
backlog and unassigned work, where is the reserve exposure by currency, who is overloaded and aging, and what
needs attention first (oldest unassigned, waiting on claimant, approved but unsettled, largest reserves). It
also reports throughput and outcome signals: submission/decision/settlement counts in the last 7 days, average
cycle time, approval/rejection rate and settled-vs-claimed ratio per currency. Aggregation is in-memory over the
projection at demo scale; the DTO is designed so it can later be backed by SQL/OLAP without a UI contract change.

## ADR-022: Shared JWT contract, manager-only insights API
Insights does not have a login endpoint and does not copy the users table. It validates the same HS256 JWT
issued by claims-service (same `JWT_SECRET` and issuer) and requires `ROLE_MANAGER` for `/manager/**`.
Managers see all markets by default and can filter to one; this matches the brief's cross-market view.

## ADR-023: Stubbed notifications and a dead-letter topic
The insights consumer records prospective claimant/officer messages in `notification_log`; a production
deployment would hand these to email/SMS. Poison or processing failures are published to `claim.dead-letter`
keyed by claim id, so the consumer keeps making progress while failures remain inspectable.

## ADR-024: Angular 21 + PrimeNG 21, one role-aware app
The frontend is a single Angular standalone/signals application. Login returns the seeded JWT; a route guard
routes each role to its workspace (`/claimant`, `/officer`, `/manager`), and an HTTP interceptor attaches the
bearer token. The shell provides the role navigation and user context. PrimeNG 22 requires a PrimeUI license
key, so the app pins PrimeNG 21.1.10 (MIT) with Angular 21 and `@primeuix/themes` 2; the Aura preset is
customised with a navy/teal palette.

## ADR-025: Frontend talks through a dev proxy
The dev server proxies `/claims-api` to `:8080` and `/insights-api` to `:8081`, keeping the browser on one
origin and avoiding per-service CORS configuration for the demo. A production deployment would use one gateway
or explicit CORS and environment-based API URLs.

## ADR-026: Manager dashboard is chart-first
The manager workspace is built from PrimeNG `p-chart` (Chart.js): status mix, reserve by market, stacked officer
workload, claim type mix and settlement ratio, with KPI cards and ranked attention lists. It consumes only the
insights projection, so the dashboard's view is independent of the command-side write model. The claimant and
officer workspaces use PrimeNG tables, dialogs, forms, tags and timelines.
