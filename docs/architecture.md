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

The ledger tracks successful deposits, withdrawals, and closure events; command receipts also retain successful queue/branch responses. This is an audit trail for a simulation, not a double-entry ledger or a tamper-proof financial audit system. The demo has one branch, fixed fictional accounts, unlimited retained history, and no authentication. A teller label does not enforce access ownership. Those limits are deliberate for this milestone; the next portion adds the React/TypeScript workflow UI.

## Verification

- Decimal rules: cents, full withdrawals, overdrafts, precision and overflow limits.
- Real HTTP mapping/validation through MockMvc and structured business errors.
- FIFO, active ticket/teller rules, account closure and branch closure.
- Concurrent identical retries, competing withdrawals, duplicate check-ins, competing teller claims.
- Forced ledger and receipt write failures to prove balance/history rollback.
- Close the application context, reconnect to persistent storage, then verify balances, waiting/serving queue, history, and original retry responses.
- Same suite against H2 and PostgreSQL, rather than claiming PostgreSQL correctness from H2 alone.
