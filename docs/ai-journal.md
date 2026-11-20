# AI Working Journal

Format per entry: **Asked** → **AI produced** → **Accepted / Challenged / Overrode** → **Why**.
Entries marked `TODO(you)` need your own verdict before submission: the panel will probe these.

---

## 1. Problem analysis and plan (pre-code)
- **Asked:** How to approach the brief; what assessors want; phased build plan; sync (REST) vs async (Kafka) split.
- **AI produced:** Two-service design (claims-service + insights-service), outbox pattern, state machine,
  REST/Kafka rule of thumb, 9-phase plan with time budget and a cut order.
- **Accepted:** TODO(you)
- **Challenged / overrode:** TODO(you). Candidate points: is two services justified at this scale? Is the
  outbox worth the time vs. publishing directly after commit?

## 2. Phase 0: repo scaffold and local infra
- **Asked:** Start building phases 0 and 1; list git commands first; PostgreSQL as DB.
- **AI produced:** docker-compose (Postgres 16 + single-node Kafka in KRaft mode), README, ADRs, .gitignore.
- **Accepted:** Kafka KRaft (no ZooKeeper) for a lighter local setup. TODO(you): confirm.
- **Verified:** Not run end to end. `docker compose up` still needs a check on your machine.

## 3. Phase 1: schema, seed data, entities
- **AI produced:** Flyway V1 (11 tables) and V2 (6 markets, 2 teams, 6 users, 3 policies); JPA entities and
  repositories; a Testcontainers test.
- **Verified by me (the AI):** ran V1 + V2 against a real PostgreSQL 16: all tables created, seed counts correct,
  status CHECK and foreign-key constraints reject bad rows.
- **Not verified:** Java was never compiled and the Maven test never ran (no Maven Central access in the AI's sandbox).
  TODO(you): run `mvn test` and record any failures here; that is a genuine "AI output needed fixing" entry.

### Decisions the AI made that you should be able to defend (challenge any you disagree with)
| Decision | Reasoning | Risk / alternative |
|---|---|---|
| FKs as plain UUID fields, not JPA associations | Clear aggregate boundaries, no lazy-load or N+1 surprises | Less convenient joins; use projections or queries |
| `incident_details` as JSONB | Motor and property fields differ; avoids two sparse tables | Weaker DB constraints; validate in API layer |
| `VARCHAR(3)` for currency, not `CHAR(3)` | Hibernate schema validation maps String to varchar and rejects bpchar | Slightly less strict; enforce with a CHECK or enum later |
| `claimed_amount` and `reserve_amount` separate | Exposure uses reserve (officer's estimate), not the claimant's claim | Reserve rules are undefined in the brief; this is an assumption |
| One decision per claim (`UNIQUE claim_id`) | Matches the simple workflow | Reopening or appeals would need a schema change |
| Seeded users with a shared BCrypt password | Fast demo and test setup | Dev-only; never ship seed credentials |
| Six markets with fixed currencies | Brief says "six markets"; the exact list was my assumption | TODO(you): confirm the markets |
| `ddl-auto: validate` | Flyway owns the schema; Hibernate only verifies it | Startup fails loudly on drift, which is intended |

### Overrides so far
- TODO(you): add at least one real override, e.g. something you changed in the AI's output after reading it.

## 4. Phase 2: claim state machine
- **Asked:** Implement the claim lifecycle rules with unit tests, no database.
- **AI produced:** Transition table in `ClaimStatus`; a guarded `Claim.transitionTo(...)` that also stamps
  `closedAt` on terminal states; `IllegalClaimTransitionException`; removal of the public status setter so
  callers cannot bypass the rules. Tests assert all 49 from/to pairs against an independently written matrix,
  plus info-request loop, terminal states, null target and exception details.
- **Verified:** `mvn -Dtest=ClaimStateMachineTest test` — 56 tests, 0 failures (JDK 21, Maven 3.8.7 in WSL).
- **Accepted / Challenged:** TODO(you). Be ready to explain: why remove the setter (state guards must be
  unbypassable), and why `closedAt` lives on the aggregate rather than only in the event timeline.

## 5. Phase 3: JWT auth and claimant APIs
- **Asked:** Add login for the seeded users and the claimant journey: submit, list, track, upload documents
  and answer information requests.
- **AI produced:** Spring Security resource-server setup with HS256 JWTs (`sub` = user id, `roles` claim,
  market, name); `POST /auth/login`; claimant-only matchers; ownership checks on every claim read/write;
  reference generation from the existing sequence; a claim event on every action.
- **Decisions worth defending:**
  - Another claimant's claim ID returns **404, not 403**, so IDs cannot be probed for existence.
  - Answering an information request only moves the claim back to `UNDER_REVIEW` once **all** open requests
    on that claim are answered, not on the first one.
  - Incident date must be inside the policy period; a claim may still exceed the coverage limit (that is an
    assessment outcome, not a submission error).
  - Document bytes go to local disk and only metadata goes in PostgreSQL; the stored name is a UUID plus a
    sanitised original filename.
- **Override / real fix:** Spring Boot 3.3.5 pins Testcontainers 1.19.8. That version cannot talk to the local
  Docker Engine 29.8 (API 1.56) and every container test failed with "Could not find a valid Docker
  environment". I overrode `testcontainers.version` to 1.21.4 in the POM; the suite then ran.
- **Verified:**
  - `mvn test` — 70 tests, 0 failures (56 state-machine, 3 schema, 9 API integration, 2 security).
  - Live smoke test on port 8081 against the docker-compose Postgres: health UP; unauthenticated `/claims`
    401; claimant login 200; submit claim `CLM-MY-000001` 201; detail shows `CLAIM_SUBMITTED`; bad password
    401; officer on `/claims` 403.
  - The live run left one sample MY motor claim in the dev database, useful for Phase 4 demos.
- **Accepted / Challenged:** TODO(you).

## 6. Phase 4: officer queue and decision workflow
- **Asked:** Build the officer queue and the pickup → review → info request → assessment → decision → settlement
  workflow. Standardise queue and information-request shapes.
- **AI produced:** `GET /officer/queue` (status filter, officer's market, paged), `GET /officer/me/workload`,
  `GET /officer/claims/{id}`, then `assign`, `info-requests`, `assessment`, `decision`, `settle`. Every command
  checks that the claim is in the officer's market and assigned to that officer.
- **Standardisation decisions:**
  - All list endpoints now use `PageResponse` (`items`, `page`, `size`, `totalElements`, `totalPages`).
  - `InfoRequestView` is identical for claimant and officer and now includes `claimId` and `requestedBy`.
  - The officer claim view is separate from the claimant view: it adds claimant identity, reserve amount,
    latest assessment and assignment. Reserve is not exposed to claimants.
  - Workload always returns every open status, including zero counts, so the UI does not have to guess keys.
- **Workflow decisions:**
  - Pickup requires status `SUBMITTED` and no existing assignee; `@Version` closes the race, and a lost update
    is mapped to HTTP 409.
  - Assessment sets `reserveAmount` to the assessed amount. Exposure is therefore the sum of reserves on open
    claims.
  - An approved decision defaults `settlementAmount` to the latest assessment; a rejection clears it.
  - Claimant detail still returns 404 for someone else's claim; officer actions return 403 for an unassigned or
    other officer's claim.
- **Verified:**
  - `mvn test` — 76 tests, 0 failures (56 state-machine, 9 claimant API, 6 officer workflow, 3 schema, 2 security).
  - Live run on :8080: queue showed 3 submitted claims; officer 1 picked one up; officer 2 got 409; info request
    went OPEN → claimant ANSWERED → claim back to review; assessment 4200; approval defaulted settlement to 4200;
    settle produced `SETTLED` with a 7-event timeline. Workload counts returned to zero for open claims.
  - Dev database now has two submitted claims for queue demos (`CLM-MY-000001`, `CLM-MY-000002`) and one settled
    claim (`CLM-MY-000003`).
- **Accepted / Challenged:** TODO(you).

## 7. Phase 5: transactional outbox and Kafka
- **Asked:** Implement Phase 5: write events with each transition and publish them to Kafka without the
  dual-write problem.
- **AI produced:** Spring Kafka producer config, seven topics (3 partitions, replication factor 1 for the local
  single broker), a versioned `ClaimEventEnvelope`, and a `ClaimEventRecorder` that writes the timeline plus an
  outbox row in the same transaction. A scheduled `OutboxPublisher` batches unpublished rows, sends them keyed
  by claim id, waits for the broker ack and then marks them published.
- **Decisions worth defending:**
  - One topic per event type, rather than one `claims.events` topic. More explicit subscriptions; the envelope
    still carries `eventType` and `schemaVersion`.
  - At-least-once, not exactly-once. A crash between send and mark produces a duplicate, so consumers should
    dedupe on `eventId`. This is cheaper than Kafka transactions and matches the read-model use case.
  - The publisher stops a batch at the first failure. Continuing would let a later event for the same claim
    overtake an earlier unpublished one.
  - The outbox is written even when Kafka is down; the poller retries. The API therefore never waits on Kafka.
  - Document uploads stay timeline-only: no consumer reacts to them and they do not change exposure.
- **Test setup decision:** the integration tests disable the publisher and topic admin so they stay independent
  of a running broker. The outbox tests assert the row payload, and the officer happy path asserts all seven
  event types in order with no `publishedAt`.
- **Verified:**
  - `mvn test` — 77 tests, 0 failures (56 state-machine, 10 claimant API, 6 officer workflow, 3 schema, 2 security).
  - Live on :8080 with the docker-compose broker: all seven topics existed; a full workflow produced 8 outbox rows
    (two submissions plus one of each transition), all with `unpublished = 0`; consuming each topic showed the
    claim id as key and correct from/to status, amounts, actor and event-specific `data`.
- **Accepted / Challenged:** TODO(you).

## 8. Phase 6: insights service and manager dashboard
- **Asked:** Build the Kafka read side and make the manager view answer: where should I put people, resources
  and business attention to improve profitability, customer experience and operational efficiency?
- **AI produced:** a second Spring Boot service, `insights-service` on :8081, owning an `insights` schema. It
  consumes all seven topics, deduplicates on `eventId`, and folds `claim_read_model`, `processed_events` and
  `notification_log`. `GET /manager/dashboard` returns portfolio, market/type/status breakdowns, officer
  workload and ranked attention lists. Security validates the same JWT and requires `ROLE_MANAGER`.
- **Decisions worth defending:**
  - Insights never reads the claims schema. The projection is disposable and rebuildable from Kafka.
  - `processed_events.event_id` is the idempotency key, matching the producer's at-least-once delivery.
  - Events are on separate topics, so the projector uses `occurredAt` and `last_event_at` to tolerate
    cross-topic reordering; a late event can fill missing facts but cannot move status backwards.
  - I enriched the producer's `data` maps with claimant/officer names so the read model can show allocation by
    person without calling back to claims-service. This is additive and old events still fold with null names.
  - Dashboard grouping mirrors the manager's questions: open/unassigned, reserve by currency,
    awaiting-info and approved-not-settled, officer age/volume, 7-day throughput, cycle time, approval rate,
    settled-vs-claimed ratio, oldest unassigned and largest exposure.
  - Currency is reported per currency; no FX conversion. Aggregation is in-memory at demo scale and documented
    as the next thing to move to SQL/OLAP at volume.
  - A `claim.dead-letter` topic keeps poison messages inspectable without blocking the consumer.
- **Verified:**
  - `insights-service`: 4 tests, 0 failures (full lifecycle fold, duplicate/stale handling, late submission,
    dashboard metrics and market filtering).
  - `claims-service`: 77 tests, 0 failures after adding names to event payloads.
  - Live both services: insights consumed the 8 historical events (2 claims) from the `earliest` offset; a new
    workflow produced named events. The dashboard then showed 5 open claims, 3 unassigned, 1 awaiting info,
    MYR 6,000 open reserve, officer Omar with 2 open claims (1 info-requested, 1 assessed), 2 settlements in the
    last 7 days, 100% approval rate and a 93.3% settled-vs-claimed ratio. A claimant token got 403 on
    `/manager/dashboard`; `?market=SG` scoped the response.
- **Accepted / Challenged:** TODO(you).
