# Chubb APAC Claims Platform

Motor and property claims platform for claimants, claims officers and managers.

**Stack:** Java 21, Spring Boot 3.3, PostgreSQL 16, Kafka (KRaft), Angular (later phase).

## Run locally

```bash
docker compose up -d                 # postgres on :5432, kafka on :9092
cd claims-service
mvn spring-boot:run                  # starts on :8080, Flyway migrates + seeds the DB
curl http://localhost:8080/actuator/health
```

Run tests (needs Docker for Testcontainers): `cd claims-service && mvn test`

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

## Structure

- `claims-service/` core domain: claims, workflow, REST API, event publishing
- `insights-service/` (Phase 6) Kafka consumer: workload and exposure read models, notifications
- `docs/ARCHITECTURE.md` design decisions
- `docs/ai-journal.md` AI working journal

## Progress

- [x] Phase 0: scaffold and local infra
- [x] Phase 1: schema, seed data, entities
- [x] Phase 2: claim state machine
- [x] Phase 3: auth and claimant APIs
- [ ] Phase 4: officer workflow
- [ ] Phase 5: outbox and Kafka
- [ ] Phase 6: insights service
- [ ] Phase 7+: integration tests, docs, Angular
