# AI-assisted development record

## Session: LedgerLine project build

- **Tool:** AI coding assistant using Copilot SDK in VS Code.
- **Input:** The user supplied the LedgerLine master build prompt and the empty GitHub repository link `bhanu4125/ledder`.
- **Work requested:** Build a financial-integrity prototype with a Spring Boot/PostgreSQL backend, React frontend, security controls, tests, deployment configuration, and honest threat/setup documentation.
- **Review and corrections made during implementation:**
  - The workspace and linked GitHub repository were empty, despite the prompt describing an existing Spring/React scaffold. The implementation therefore starts from a new project rather than claiming to have extended a pre-existing scaffold.
  - The first backend compile found ambiguous `JdbcTemplate.execute` callback overloads and a missing transaction timestamp accessor. Those were corrected and the backend subsequently compiled.
  - The initial seed-account approach would have created balances without ledger history. It was changed so opening balances are recorded as balanced transactions against the demo bank-float account.
  - Review of `npm audit` identified React Router advisories in the initial major version. The dependency was moved to the patched React Router 7 release; the final production build and production dependency audit both pass.
  - Starting Docker Desktop allowed the Compose stack to run. Startup exposed and fixed a PostgreSQL hash-column type mismatch, seed inserts that JPA incorrectly treated as updates, JDBC timestamp parameter typing, and transaction/ledger insert ordering.
  - A live 50-request withdrawal race exposed stale system-account state being loaded before pessimistic locking. The system account is now looked up as a scalar id before all participating rows are locked in sorted order. A fresh-database rerun completed with five successful withdrawals, 45 insufficient-funds conflicts, no server errors, and a zero balance.
  - Testcontainers still cannot discover the Docker Desktop daemon through its Windows named pipe, so the 10 integration tests are skipped by Maven. This is reported separately from the successful live Compose checks.
- **Final validation evidence:** `mvn test` succeeded (2 unit tests passed; 10 Testcontainers tests skipped because Docker discovery returns an invalid daemon response). `npm run build` succeeded, `npm audit --omit=dev` reported zero vulnerabilities, the live API health check returned `UP`, an above-threshold transfer moved from `PENDING` to `COMPLETED` after authoriser approval, and ledger verification returned `VERIFIED` for 18 rows. `scripts/check-append-only.ps1` confirmed the runtime role cannot update or delete either append-only table.

This file records this build session only; no external AI tool or human review is claimed.
