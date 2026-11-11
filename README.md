# LedgerLine

LedgerLine is a financial-integrity prototype built with Java 21, Spring Boot, PostgreSQL, Flyway, and React. It is a demo system, not a bank or a production payment service.

## What is implemented

- BCrypt password hashing (cost 12), account-holder registration, demo-role login, generic login errors, escalating lockout, and per-IP/per-email in-process rate limits.
- 15-minute signed JWT access tokens and rotating, opaque refresh credentials in an HTTP-only, SameSite=Strict cookie. The React app keeps access tokens in memory, not browser storage.
- Server-enforced role checks, account ownership checks, a restricted admin user/account API, and an authoriser queue for transactions above the configured threshold.
- PostgreSQL schema managed by Flyway; account balances have a non-negative database check and an optimistic version column.
- Serialized account row locking in ascending account-id order; each completed movement updates balances and writes two balanced ledger entries in one database transaction.
- ADMIN/AUTHORISER compensating reversals create a new linked transaction and opposite ledger entries; original ledger rows are never edited.
- ADMIN account freeze/unfreeze controls require a reason and append an audit event; frozen accounts reject new movements and pending settlements.
- Required idempotency keys, request-body conflict detection, and stored responses written atomically with the transaction.
- Date-filterable, ownership-scoped account statement downloads in CSV format.
- SHA-256 hash chains for ledger and audit rows, a live ledger verification API, a periodic verifier, and an admin/auditor verification action.
- Request correlation UUIDs are validated or generated, included in response headers, error bodies, logs, audit/security records, and the audit-row hash.
- Docker deployment with a dedicated runtime database role. That role has SELECT/INSERT but no UPDATE/DELETE on ledger and audit tables; the migration owner and runtime credentials are separate.
- React pages for overview, accounts, transaction history/creation, ledger verification, authorisation, audit/security events, and admin user/account management.
- OpenAPI documentation at `/swagger-ui.html`.
- GitHub Actions CI runs backend tests, frontend build, and the production dependency advisory scan on pushes and pull requests.

The UI shows the latest 100 transactions and 200 ledger/audit rows exposed by the API. Search, date filters, sorting, and pagination are client-side over those returned rows. The overview chart is drawn from actual recent transaction amounts; it is not historical aggregate reporting.

## Start with Docker Compose

Requirements: Docker Desktop (or another Docker Compose v2 installation).

```powershell
Copy-Item .env.example .env
# Edit .env and replace all three password/secret placeholders with unique values.
docker compose up --build -d
docker compose ps
```

Open [http://localhost](http://localhost). The frontend proxies API and Swagger requests to the backend. Swagger UI is at [http://localhost/swagger-ui.html](http://localhost/swagger-ui.html).

PostgreSQL initialization creates `ledgerline_owner` (schema/migration owner) and `ledgerline_runtime` (application role). Flyway connects as the owner; Hibernate connects as the restricted runtime role. The initialisation script only runs on a fresh PostgreSQL data volume. If you change database passwords after the volume has been created, recreate that volume intentionally before restarting; this removes its database contents.

The local Compose stack is marked `DEMO` in the UI and keeps Swagger available for exploration. For a TLS deployment, set `SPRING_PROFILES_ACTIVE=production`, terminate HTTPS at a trusted reverse proxy, set `FRONTEND_ORIGIN` to the exact HTTPS origin, use unique long database passwords and a random JWT secret, and do not expose PostgreSQL or the backend port publicly. The production profile disables Swagger/OpenAPI, suppresses verbose server errors, disables SQL logging, and enables secure refresh cookies. Do not override `SECURE_COOKIES=true` in production. The included Compose file is a local demo deployment, not a hardened public-internet deployment.

## Deploy to Cloud with Render

A complete Infrastructure-as-Code Blueprint is included in `render.yaml`. To deploy:

1. Push your repository to GitHub.
2. Log into [Render Dashboard](https://dashboard.render.com) and click **New +** $\rightarrow$ **Blueprint**.
3. Connect your repository. Render automatically reads `render.yaml` and sets up:
   - `ledgerline-db`: Managed PostgreSQL database.
   - `ledgerline-backend`: Spring Boot Docker service with automated JDBC URL normalization, database connection, secure JWT secret generation, and production profile.
   - `ledgerline-frontend`: Nginx + React Docker service with dynamic reverse proxy to the backend.
4. Click **Apply**. Render handles building, HTTPS certificates, and deployment automatically.

## Demo credentials

These are intentionally public, seeded demo credentials. Change/remove them before any non-demo deployment.

| Role | Email | Password |
|---|---|---|
| ADMIN | `admin@ledgerline.demo` | `LedgerLine-Admin-2026!` |
| AUTHORISER | `authoriser@ledgerline.demo` | `LedgerLine-Approve-2026!` |
| ACCOUNT_HOLDER | `holder@ledgerline.demo` | `LedgerLine-Holder-2026!` |
| ACCOUNT_HOLDER | `holder2@ledgerline.demo` | `LedgerLine-Holder2-2026!` |
| AUDITOR | `auditor@ledgerline.demo` | `LedgerLine-Audit-2026!` |

`POST /api/auth/register` creates an ACCOUNT_HOLDER. New users need an admin-created account before making a movement. Demo accounts are funded by recorded opening transactions against a demo bank-float account.

## Local development

Install Java 21, Maven 3.9+, Node 22+, and PostgreSQL 16. Configure a PostgreSQL database and set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `FLYWAY_USERNAME`, `FLYWAY_PASSWORD`, and `JWT_SECRET` in the shell. Flyway's user must own the schema; for append-only enforcement, use the same owner/runtime split as Compose. A direct local database user with schema-owner privileges does **not** get the Compose append-only guarantee.

```powershell
mvn spring-boot:run
```

In another terminal:

```powershell
npm ci
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). Vite proxies `/api` to `http://localhost:8080`. Set `FRONTEND_ORIGIN=http://localhost:5173`. For integration tests, run `mvn test`; the Testcontainers suite needs a running Docker engine. Unit tests can run without Docker.

## Architecture

```text
React SPA (Vite / Nginx)
  ├─ Authorization header: short-lived signed access JWT
  └─ SameSite=Strict, HttpOnly cookie: rotating refresh credential
       ↓
Spring Boot API (method RBAC → ownership checks → transactional services)
       ↓
PostgreSQL
  ├─ Flyway migrations: ledgerline_owner
  └─ Runtime: ledgerline_runtime (no UPDATE/DELETE on audit_log or ledger_entries)
```

Each money movement has a transaction row and exactly two ledger entries: one debit and one credit. The system float represents demo cash flows; deposits and withdrawals do not connect to a real bank. Ledger rows are globally ordered into a SHA-256 chain; audit rows use a separate chain. The verifier reports the first broken ledger row. A hash chain is tamper-evident, not a blockchain, and cannot protect against an attacker who can rewrite the database and recompute every later hash.

## Checks and limitations

- Backend unit/integration tests: `mvn test`.
- Frontend production build: `npm run build`.
- Database-level append-only check against a running Compose stack: `.\scripts\check-append-only.ps1`.
- The integration suite includes ownership/role denial, JWT tampering/expiry, validation failures, idempotency/body conflict (including concurrent replay), a 50-request withdrawal race, lockout, ledger invariants, direct ledger-row tampering, and database append-only privilege checks. It requires Testcontainers/Docker to execute.
- Additional integration tests cover compensating reversals, account freeze enforcement, CSV ownership boundaries, correlation ID persistence, and mass-assignment attempts.
- CI is configured in `.github/workflows/ci.yml`; frontend supply-chain checks currently run `npm audit --omit=dev`.
- Local verification for the expanded features: the live Compose API successfully reversed and replayed a transfer, rejected a withdrawal from a frozen account, enforced statement IDOR with 403, returned a CSV statement, persisted the request correlation ID, and verified the resulting ledger. A separate production-profile probe reported `PRODUCTION`, returned 404 for Swagger, and set `Secure`, `HttpOnly`, `SameSite=Strict` refresh-cookie attributes.
- On this Windows setup, `mvn test` passes its 2 unit tests but skips all 13 Testcontainers integration tests because Testcontainers' named-pipe Docker discovery fails, even though Compose is running. The CI workflow is configured but was not run on GitHub in this session.
- Rate limiting and account lockout counters are process-local. They are suitable for a single-instance prototype, not a horizontally scaled service.
- The runtime DB restrictions are configured by the Compose PostgreSQL init script and Flyway migration. They are not applied automatically to an arbitrary pre-existing database or a local schema-owner connection.
- Audit-chain verification and exhaustive SQL injection/XSS/CSRF browser tests are not yet implemented. `THREATS.md` identifies those gaps. The DB-level integration test checks runtime-role grants; the separate Compose script attempts actual UPDATE/DELETE statements.
- A new account starts at zero; there is no real payment rail, email verification, password reset, MFA, or production secrets-management integration.
- Refresh cookies use `SECURE_COOKIES=false` in local Compose so HTTP localhost works. HTTPS deployments must set it to `true`.
- Access-token role claims remain valid until token expiry after an admin changes a user's role (maximum 15 minutes).
- Security-event rows carry correlation IDs but are not part of the protected, hash-chained `audit_log`; audit-chain verification is also not implemented. See `THREATS.md`.
