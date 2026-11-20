# Chubb claims web app

Angular 21 standalone app for the claimant, officer and manager workspaces. UI components and charts come from
PrimeNG 21 (MIT) with Chart.js.

## Run

Start `claims-service` on `:8080` and `insights-service` on `:8081` first (see the repository README). Then:

```bash
npm install
npm start
```

Open `http://localhost:4200`. The dev server proxies `/claims-api` to `http://localhost:8080` and
`/insights-api` to `http://localhost:8081`; see `proxy.conf.json`.

## Demo logins

All seeded users use `Password123!`:

- `claimant.my@example.com` — claimant workspace
- `officer.my1@example.com` — officer workspace
- `manager.my@example.com` — manager dashboard

The sign-in page also has one-click demo account cards.

## Checks

```bash
npm run build
ng test
```

The manager dashboard uses `p-chart`: open claims by status, reserve by market, officer workload by status,
claim type mix and settlement ratio. The claimant and officer views use PrimeNG tables, dialogs, tags, forms and
timelines. See `src/app/core/theme.ts` for the customised Aura preset.
