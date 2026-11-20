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
