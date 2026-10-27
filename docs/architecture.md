# Core design decisions

## Preserve the workflow

The original queue and account list stored the same mutable `Client.next` pointer. Independent SQL rows remove that coupling. FIFO uses the monotonically increasing ticket ID assigned under the branch lock; timestamps are descriptive, not the ordering mechanism. Completed tickets and closed accounts remain available so history is not orphaned. A zero balance is required before account closure, replacing silent deletion of a funded account.

## Transaction and concurrency boundary

Every mutation uses Spring's `TransactionTemplate` at READ_COMMITTED isolation. It locks the singleton branch row with `SELECT ... FOR UPDATE`, checks a persisted command receipt, validates current workflow state, changes records, and stores the response before commit. A database lock works across application instances using the same database; a JVM `synchronized` block would not.

This intentionally serializes **all writes for one small demonstration branch**, including claims, check-ins, and balance updates. It is a correctness-oriented tradeoff, not a high-throughput banking architecture. Reads do not take this lock. In a larger system, independent per-account locks plus a queue-claim protocol would allow more concurrency, with dedicated stress tests and measured contention. Transactions have a ten-second timeout.

The ledger append, account balance change, and retry receipt share the same transaction. Injected write failures verify rollback even after a preceding balance update. Commands use a SHA-256 fingerprint of operation plus normalized payload and store the original JSON response. Replays return that original snapshot even after later state changes. Receipt persistence is unbounded here; a production retention/reconciliation policy would need explicit semantics rather than silently expiring retry protection.

## Database constraints and migrations

Foreign keys preserve account/ticket history. Numeric constraints reject negative balances; an active-slot uniqueness constraint backs up one-active-ticket-per-account validation. Closed accounts must have zero balance. SQL uses portable identity, decimal, and timestamp constructs supported by H2's PostgreSQL mode and PostgreSQL 17. H2 is convenient local storage; the PostgreSQL test job verifies the actual target dialect and locking behavior. Flyway owns schema and fictional seed migrations; no runtime CSV import or second SQL initializer runs.

## Boundaries

The ledger tracks successful deposits, withdrawals, and closure events; command receipts also retain successful queue/branch responses. This is an audit trail for a simulation, not a double-entry ledger or a tamper-proof financial audit system. The demo has one branch, fixed fictional accounts, unlimited retained history, and no authentication. A teller label does not enforce access ownership. Those limits are deliberate for this milestone; the React/TypeScript UI presents the workflow without changing those security limits.

## Verification

- Decimal rules: cents, full withdrawals, overdrafts, precision and overflow limits.
- Real HTTP mapping/validation through MockMvc and structured business errors.
- FIFO, active ticket/teller rules, account closure and branch closure.
- Concurrent identical retries, competing withdrawals, duplicate check-ins, competing teller claims.
- Forced ledger and receipt write failures to prove balance/history rollback.
- Close the application context, reconnect to persistent storage, then verify balances, waiting/serving queue, history, and original retry responses.
- Same suite against H2 and PostgreSQL, rather than claiming PostgreSQL correctness from H2 alone.

## Frontend and packaging

The React app uses typed API models and local state for three workspace views. Vite proxies `/api` during development; the Maven `web` profile packages its output into Spring Boot's static resources, so the normal JAR serves both the UI and API. Backend-only builds remain available. Local variable fonts are bundled with their license files under `/licenses/`; font/icon/React attribution is retained with the distribution.

An explicit command client saves each UUID, route, normalized body, and user-facing action summary in tab-scoped session storage **before** sending a mutation. Only one command may be pending per tab. A network error, timeout, or server failure retains it; a reload restores the same command. The retry button resends exactly that saved command. Definitive input/business rejections clear it; confirmed actions clear it and trigger fresh reads. Mutations are paused when reads fail, and recovery commands remain available. Server-side persisted idempotency is the final duplicate protection across process restarts; UI disabling alone would not be sufficient.

The frontend never optimistically changes balances. It refreshes account/queue/history data after commands, every ten seconds while visible, and on window focus. Failed reads preserve the previous display but pause new actions. Reads span several endpoints, so concurrent changes can briefly be seen across different snapshots; this is not an atomic dashboard snapshot. Database constraints and transaction boundaries remain authoritative.

Closure confirmations retain their original ticket/account or branch target if background refreshes change the selected session. Switching sessions clears the amount field. Native dialogs provide focus trapping, Escape dismissal, and focus restoration; responsive layouts keep tables horizontally scrollable within their panels.

A browser test deliberately forwards a deposit to the actual backend, discards its response, reloads the page, retries the stored command, and checks both the unchanged final balance and a single ledger entry. This tests a committed-but-unacknowledged command rather than only mocking a pre-request failure. The same suite also verifies mobile overflow, connection recovery, and selected automated accessibility rules.
