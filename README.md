# Banker

A Java bank teller simulation built around a **check-in → FIFO queue → teller session** workflow. The backend persists accounts, queue tickets, exact decimal balances, transaction history, and retry receipts. It evolves the original linked-list coursework into a Spring Boot application; the original source remains in [`legacy/`](legacy/README.md).

**Current milestone:** working REST backend with H2/PostgreSQL persistence and automated workflow tests. The planned React/TypeScript teller dashboard is the next portion; it is not implemented here.

## Run locally

Requires **Java 17+ and Maven 3.9+**. Python 3 is optional for the demo script.

```sh
mvn clean verify
java -jar target/banker-0.1.0.jar
```

The service listens at `http://127.0.0.1:8080/api`. H2 writes to `./data/banker` relative to your working directory; use the same directory when restarting. Flyway applies versioned migrations automatically. There is no separate database installation for this default mode.

In another terminal:

```sh
curl http://127.0.0.1:8080/api/accounts
python3 scripts/demo.py
```

The script checks in Alex, claims the next ticket, deposits $25, retries that exact deposit, withdraws $10, and completes the session. It changes Alex's balance by $15 and expects an open branch with an empty queue. Repeated runs work after completion.

| Fictional account | Name | Starting balance |
|---|---|---:|
| DEMO-1001 | Alex River | $1,000.00 |
| DEMO-1002 | Jordan Vale | $250.00 |
| DEMO-1003 | Casey Brook | $0.00 |

These are explicitly fictional seeds. The legacy CSV files are never imported. To reset **only a disposable H2 demo**, stop the application and remove `data/`; the next startup recreates it.

## PostgreSQL mode

Start the local database using Docker Compose:

```sh
docker compose up -d --wait
```

Then run the backend with environment variables (Bash example):

```sh
export BANKER_DB_URL=jdbc:postgresql://localhost:5432/banker
export BANKER_DB_USER=banker
export BANKER_DB_PASSWORD=banker-local-demo
java -jar target/banker-0.1.0.jar
```

PowerShell equivalents:

```powershell
$env:BANKER_DB_URL = 'jdbc:postgresql://localhost:5432/banker'
$env:BANKER_DB_USER = 'banker'
$env:BANKER_DB_PASSWORD = 'banker-local-demo'
java -jar target/banker-0.1.0.jar
```

Compose's default password is for local demonstration. If changed in `.env`, set the same value for the application. The PostgreSQL named volume survives `docker compose down`; `docker compose down -v` deletes that disposable database.

## What the core handles

- Account/name verification before check-in; one active ticket per account.
- FIFO claims by ticket sequence, one active session per normalized teller name.
- Deposits, withdrawals, balance/history lookup, session completion, and account closure with zero balance.
- `BigDecimal` and `NUMERIC(15,2)` money; positive amounts, at most two decimal places, no overdrafts or balance overflow.
- Database transactions that commit balance, ledger entry, and idempotency receipt together.
- Durable queue and retry receipts across restarts. An identical UUID-keyed command returns the original response; conflicting reuse returns 409.
- Branch closure blocked while any ticket is waiting or serving; closed branches reject new work until reopened.
- Problem Details responses for invalid input and business conflicts.

## Test

```sh
mvn clean verify
```

The same suite can exercise a **disposable PostgreSQL test database**:

```sh
export BANKER_TEST_DB_URL=jdbc:postgresql://localhost:5432/banker_test
export BANKER_TEST_DB_USER=banker
export BANKER_TEST_DB_PASSWORD=your-local-test-password
mvn clean verify
```

Tests reset demo rows; never point them at data you want to keep. CI runs separate H2 and PostgreSQL 17 jobs. Tests cover concurrent withdrawals/retries/check-ins/claims, FIFO, identity validation, rollback on injected ledger/receipt failures, HTTP errors, exact money arithmetic, and a complete application-context restart against persistent storage.

## Design and scope

`BankController` handles HTTP input, `BankService` owns workflow and transaction boundaries, `BankRepository` owns SQL, and `MoneyRules` holds decimal invariants. Flyway is the only schema initializer. See [API contract](docs/api.md) and [architecture decisions](docs/architecture.md).

This is a **local, fictional bank simulation**, with no authentication or real banking integration. Name matching demonstrates the original workflow; it is not identity security. The backend binds to localhost by default and has no CORS allowance. Public deployment, authentication, and a user interface are outside this portion.

## Project history

The coursework version used CSV storage and intrusive linked-list nodes. It had compilation errors, a list insertion bug, and shared account/queue pointers. The current backend replaces those structures with independent persisted accounts and queue tickets while retaining the original teller workflow. The archived source and CSVs are preserved for reference and excluded from the Maven build.
