# Chubb APAC Claims Platform

Motor and property claims platform for claimants, claims officers and managers.

**Stack:** Java 21, Spring Boot 3.3, PostgreSQL 16, Kafka (KRaft), Angular 21, PrimeNG 21, Chart.js 4.

**Time used:** 1st prompt - 6.43 pm, final prompt - 9.50 pm

## Run locally

```bash
docker compose up -d                 # postgres on :5432, kafka on :9092
cd claims-service
mvn spring-boot:run                  # starts on :8080, Flyway migrates + seeds the DB
curl http://localhost:8080/actuator/health

# in a second terminal
cd insights-service
mvn spring-boot:run                  # starts on :8081, consumes the claim topics from earliest
curl http://localhost:8081/actuator/health

# in a third terminal
cd frontend
npm install
npm start                            # Angular dev server on http://localhost:4200
```

Run tests (needs Docker for Testcontainers):
`cd claims-service && mvn test` and `cd insights-service && mvn test`.
See [Frontend](#frontend-phase-7) for the browser demo and proxy setup.

## Seeded users (password for all: `Password123!`)

| Email | Role | Market |
|---|---|---|
| manager.my@example.com | MANAGER | MY |
| officer.my1@example.com | OFFICER | MY |
| officer.my2@example.com | OFFICER | MY |
| officer.sg1@example.com | OFFICER | SG |
| claimant.my@example.com | CLAIMANT | MY |
| claimant.sg@example.com | CLAIMANT | SG |

## Auth and claimant API (Phase 3)

Login returns an HS256 JWT (60 minute TTL). The secret is a dev default; override it with `JWT_SECRET`.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"claimant.my@example.com","password":"Password123!"}' | jq -r .token)

curl -X POST http://localhost:8080/claims \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"policyId":"c1000000-0000-0000-0000-000000000001","incidentDate":"2026-11-01",
       "description":"Rear-ended at traffic light","claimedAmount":4500.00,
       "incidentDetails":{"vehiclePlate":"WXY1234"}}'

curl http://localhost:8080/claims -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/claims/{claimId} -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/claims/{claimId}/documents \
  -H "Authorization: Bearer $TOKEN" -F 'file=@repair-quote.pdf'
```

Claim references look like `CLM-MY-000001`. Claim detail includes the event timeline, information requests,
documents and the decision when one exists. Uploaded files are stored under `./uploads/` (gitignored).

## Officer API (Phase 4)

All officer endpoints require the `OFFICER` role and are scoped to the officer's market. The queue uses the
same paged envelope as other list endpoints: `{ items, page, size, totalElements, totalPages }`.

```bash
OTOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"officer.my1@example.com","password":"Password123!"}' | jq -r .token)

curl "http://localhost:8080/officer/queue?status=SUBMITTED&page=0&size=20" -H "Authorization: Bearer $OTOKEN"
curl http://localhost:8080/officer/me/workload -H "Authorization: Bearer $OTOKEN"
curl http://localhost:8080/officer/claims/{claimId} -H "Authorization: Bearer $OTOKEN"

curl -X POST http://localhost:8080/claims/{claimId}/assign -H "Authorization: Bearer $OTOKEN"
curl -X POST http://localhost:8080/claims/{claimId}/info-requests -H "Authorization: Bearer $OTOKEN" \
  -H 'Content-Type: application/json' -d '{"question":"Please upload the repair estimate"}'
curl -X POST http://localhost:8080/claims/{claimId}/assessment -H "Authorization: Bearer $OTOKEN" \
  -H 'Content-Type: application/json' -d '{"notes":"Damage consistent","assessedAmount":4200.00}'
curl -X POST http://localhost:8080/claims/{claimId}/decision -H "Authorization: Bearer $OTOKEN" \
  -H 'Content-Type: application/json' -d '{"outcome":"APPROVED","reason":"Covered by policy"}'
curl -X POST http://localhost:8080/claims/{claimId}/settle -H "Authorization: Bearer $OTOKEN"
```

Assignment is an atomic pickup: the claim's `@Version` means two officers cannot both succeed. Information
request responses share one shape for claimant and officer views, including `claimId`, `requestedBy` and
status. An approved decision defaults the settlement amount to the latest assessment when none is supplied.

## Events and outbox (Phase 5)

Every state change writes a versioned event to `outbox_events` in the same PostgreSQL transaction as the claim
update. A scheduled poller publishes unpublished rows to Kafka, keyed by claim id, then marks them published.
If Kafka is down the rows stay unpublished and are retried; per-claim ordering is preserved by stopping a batch
at the first failure. The result is at-least-once delivery, so consumers must deduplicate on `eventId`.

| Event type | Kafka topic |
|---|---|
| CLAIM_SUBMITTED | `claim.submitted` |
| CLAIM_ASSIGNED | `claim.assigned` |
| INFO_REQUESTED | `claim.info-requested` |
| INFO_PROVIDED | `claim.info-provided` |
| CLAIM_ASSESSED | `claim.assessed` |
| CLAIM_DECIDED | `claim.decided` |
| CLAIM_SETTLED | `claim.settled` |

The envelope carries `eventId`, `eventType`, `schemaVersion`, `occurredAt`, claim snapshot fields, from/to
status, actor and an event-specific `data` map. Configure the broker with `KAFKA_BOOTSTRAP_SERVERS`
(default `localhost:9092`). Inspect a topic with:

```bash
docker exec chubb-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic claim.decided --from-beginning
```

## Manager dashboard (Phase 6)

`insights-service` consumes every claim topic, deduplicates on `eventId` and folds the stream into an
`insights` schema. The read model is rebuildable from Kafka. The manager endpoint answers the allocation
question: where are the people, money and attention needed?

```bash
MANAGER_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"manager.my@example.com","password":"Password123!"}' | jq -r .token)

curl "http://localhost:8081/manager/dashboard?market=MY" -H "Authorization: Bearer $MANAGER_TOKEN"
```

The response is grouped around four decisions:

- **Portfolio and profitability** — open claims, unassigned work, awaiting information, approved but not
  settled, reserve exposure by currency, settled amount vs claimed (settlement ratio), approval/rejection
  rates and average cycle time.
- **Where to allocate people** — officer workload with open counts by status, aging over 7 days, recent
  settlements and cycle time; unassigned depth by market and type.
- **Customer experience / SLA** — oldest open claims, claims waiting on the claimant, approved claims waiting
  to be paid, and aging buckets by market and type.
- **Business attention** — the oldest unassigned claims and the largest open reserves, ready to drill into.

A manager sees all markets by default and can filter with `?market=SG`. Currency is never silently converted;
exposure and settlement figures are reported per currency. Notifications are written to
`insights.notification_log` as an email stub. Aggregation is intentionally in-memory at demo scale; at volume
these become SQL/OLAP projections.

## Frontend (Phase 7)

The Angular app is in `frontend/` and runs at `http://localhost:4200`. The dev server proxies
`/claims-api` to `:8080` and `/insights-api` to `:8081` (`proxy.conf.json`), so no CORS setup is needed locally.

Use the demo account cards on the sign-in page, or sign in directly:

| Workspace | Account | What it shows |
|---|---|---|
| Claimant | `claimant.my@example.com` | Claim list, report dialog, timeline, information responses, document upload |
| Claims officer | `officer.my1@example.com` | Unassigned queue, personal workload, pickup, info request, assessment, decision, settlement |
| Manager | `manager.my@example.com` | KPI cards, PrimeNG/Chart.js portfolio charts and ranked attention lists |

**Stack note:** PrimeNG 22 requires a PrimeUI license key and shows a license notice without one. This demo uses
PrimeNG **21.1.10**, which is MIT-licensed, with Angular 21 and `@primeuix/themes` 2. Angular 21 officially
supports Node 20.19+, 22.12+ and 24.x; the existing Node 22.22.1 already met that range. Node 26 is listed for
Angular 22, not Angular 21, so it is not required here. The UI uses standalone components, signals, a role guard,
an HTTP bearer-token interceptor and PrimeNG's Aura theme customised with a Chubb navy/teal palette. Charts are
PrimeNG `p-chart` backed by Chart.js: open claims by status, reserve by market, officer workload by status, claim
type mix and settlement ratio.

## End-to-end verification

A full browser-driven run against the local stack exercised every role:

1. **Claimant** signed in, reported `CLM-MY-000012`, and saw it as **Submitted** in history.
2. **Officer** found it in the queue, picked it up (**Under Review**) and requested more information.
3. **Claimant** answered the information request; the claim returned to **Under Review**.
4. **Officer** recorded an assessment of 4,500, approved and settled the claim.
5. **Officer** assessed and rejected `CLM-MY-000011` through the separate reject path.
6. **Claimant** saw **Settled** and **Rejected** in history, with every transition on the timeline.
7. **Manager** saw the updated portfolio: 6 open claims, 3 unassigned, 1 awaiting information,
   MYR 6,000 open reserve, 10 submitted / 4 decided / 3 settled in the last 7 days, 75% approval,
   25% rejection, 92.1% settled-vs-claimed, 5 charts and 4 attention cards.

There were **zero browser console errors and zero failed requests**. The outbox had **35 events, 0
unpublished**; the insights projection processed all 35 into 10 claims and 30 notification records.

## Known gaps / next steps

- Notifications are a stubbed log; a production system would hand them to email/SMS.
- Manager aggregation is in-memory over the read model; move to SQL/OLAP for volume.
- No FX conversion: exposure and settlement are always reported per currency.
- SLA thresholds, notification preferences and user administration are not configurable.
- Seeded JWT users are development-only; replace with an identity provider.
- Document bytes are stored on local disk; object storage and virus scanning are out of scope.
- Manager UI shows ranked attention lists; claim-level drill-through and export are next.
- Local Kafka and PostgreSQL are single-node; no replication, schema-registry contract or committed
  browser E2E suite yet.

## Structure

- `claims-service/` core domain: claims, workflow, REST API, transactional outbox
- `insights-service/` Kafka consumer: manager/officer read models, exposure, notifications
- `frontend/` Angular 21 + PrimeNG 21 role-based web app
- `docs/ARCHITECTURE.md` design decisions
- `docs/ai-journal.md` AI working journal

## Progress

- [x] Phase 0: scaffold and local infra
- [x] Phase 1: schema, seed data, entities
- [x] Phase 2: claim state machine
- [x] Phase 3: auth and claimant APIs
- [x] Phase 4: officer queue and workflow
- [x] Phase 5: transactional outbox and Kafka
- [x] Phase 6: insights service and manager dashboard
- [x] Phase 7: Angular frontend with PrimeNG charts
