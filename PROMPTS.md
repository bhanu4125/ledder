# AI-assisted development record

## Session: LedgerLine project build

- **Tool:** AI coding assistant using Copilot SDK in VS Code.
- **Input:** The user supplied the LedgerLine master build prompt and the empty GitHub repository link `bhanu4125/ledder`.
- **Work requested:** Build a financial-integrity prototype with a Spring Boot/PostgreSQL backend, React frontend, security controls, tests, deployment configuration, and honest threat/setup documentation.
- **Review and corrections made during implementation:**
  - The workspace and linked GitHub repository were empty, despite the prompt describing an existing Spring/React scaffold. The implementation therefore starts from a new project rather than claiming to have extended a pre-existing scaffold.
  - The first backend compile found ambiguous `JdbcTemplate.execute` callback overloads and a missing transaction timestamp accessor. Those were corrected and the backend subsequently compiled.
  - The initial seed-account approach would have created balances without ledger history. It was changed so opening balances are recorded as balanced transactions against the demo bank-float account.
  - Review of `npm audit` identified moderate React Router advisories in the initial major version. The dependency was moved to the patched React Router 7 release and the build is being rechecked.
  - Docker is installed but its engine is stopped in this environment. Testcontainers integration tests and Compose-level DB privilege checks must not be reported as passed until run with a working Docker engine.
- **Current validation evidence:** Java backend package and React production build succeeded before the most recent UI/security edits. Maven tests then exposed a missing `Map` test import; that correction was applied. Final test/build results are recorded in the handoff, not assumed here.

This file records this build session only; no external AI tool or human review is claimed.
