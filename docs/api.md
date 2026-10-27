# API contract

Base URL: `http://127.0.0.1:8080/api`. JSON input and output. All successful operations return HTTP 200, including replayed commands. Reads require no request body.

Every POST requires an `Idempotency-Key` header containing a UUID. Generate a **new UUID for each intended action**, retain it while the action is pending, and reuse it on network retries. Reusing a key for a different operation or normalized payload returns 409 `KEY_REUSED`. Successful receipts persist without expiry in this demo. Failed commands roll back and leave no receipt, so retrying after a conflict is resolved can succeed. A replay returns its original snapshot, which may be older than current state; GET the resource to refresh it.

| Method | Path | Body / behavior |
|---|---|---|
| GET | `/accounts` | All demo accounts, including closed accounts |
| GET | `/accounts/{id}` | Account and current balance |
| GET | `/accounts/{id}/transactions` | Ledger in ascending entry order |
| GET | `/queue` | Waiting and serving tickets, ascending ticket ID |
| GET | `/tickets/{id}` | Ticket, including completed tickets |
| GET | `/branch` | `{"open":true}` |
| POST | `/queue/check-in` | `{"accountId":"DEMO-1001","firstName":"Alex","lastName":"River"}` |
| POST | `/queue/claim-next` | `{"teller":"teller-1"}`; claims oldest waiting ticket |
| POST | `/tickets/{id}/deposits` | `{"amount":"25.00"}`; requires serving ticket |
| POST | `/tickets/{id}/withdrawals` | `{"amount":"10.00"}`; requires serving ticket |
| POST | `/tickets/{id}/complete` | No body; requires serving ticket |
| POST | `/tickets/{id}/close-account` | No body; requires serving ticket with zero balance, records closure and completes ticket |
| POST | `/branch/close` | No body; requires no active tickets |
| POST | `/branch/open` | No body |

Money is accepted as a decimal JSON string or number; clients should use strings when submitting values to preserve decimal intent. Responses contain decimal JSON numbers. Display with two places and avoid floating point for arithmetic. Dates are ISO 8601 timestamps with offsets. IDs are positive database-generated ticket/ledger numbers; gaps after rolled-back inserts are normal and do not affect FIFO.

Account IDs are trimmed/uppercased at check-in; names are trimmed and matched case-insensitively. Teller names are trimmed/lowercased. Other account lookup paths use the exact account ID returned by the API. Amounts `10` and `10.00` normalize to the same command; more than two fractional digits are rejected. Unknown body fields, missing fields, malformed JSON, invalid UUIDs, and invalid amounts return 400.

Example check-in:

```sh
curl -X POST http://127.0.0.1:8080/api/queue/check-in \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 96c09510-45cd-44e1-bdf2-44b69bd5a743' \
  -d '{"accountId":"DEMO-1001","firstName":"Alex","lastName":"River"}'
```

Business errors use `application/problem+json`, with `status`, `detail`, and stable `code` fields. Common codes: 404 `ACCOUNT_NOT_FOUND`/`TICKET_NOT_FOUND`; 409 `ALREADY_CHECKED_IN`, `TELLER_BUSY`, `QUEUE_EMPTY`, `TICKET_NOT_SERVING`, `INSUFFICIENT_FUNDS`, `BALANCE_LIMIT`, `BALANCE_NOT_ZERO`, `ACCOUNT_CLOSED`, `ACTIVE_TICKETS`, `BRANCH_CLOSED`, and `KEY_REUSED`. Identity mismatch is 400 `IDENTITY_MISMATCH`. Framework-level syntax/header errors need not have a `code`; use HTTP status and detail. Database errors return 503 `DATABASE_UNAVAILABLE`; retry with the original key because a lost connection can leave commit outcome uncertain.

The forthcoming UI should keep the idempotency key until it has a definite result, disable duplicate submissions while pending, show structured errors, and refresh account/queue/history after mutations. Teller names are session labels, not authenticated users.
