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
