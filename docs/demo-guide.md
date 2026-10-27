# Banker: three-minute interview demo

## Prepare

Use the portable release with Java 17+, or build with `mvn -Pweb clean verify` and run `java -jar target/banker-1.0.0.jar`. Open http://127.0.0.1:8080. The packaged launchers keep H2 demo data beside the launcher; a directly launched JAR uses its working directory.

For a repeatable walkthrough, use a **fresh disposable demo folder/database**, with an open branch and empty queue. Alex River / DEMO-1001 starts with $1,000; Jordan Vale / DEMO-1002 starts with $250; Casey Brook / DEMO-1003 starts with $0. Preserve any demonstration data you want before resetting it. Close Banker before removing a disposable `data` folder. Startup and refresh never reset data automatically.

## Walkthrough

| Time | Action | What it demonstrates |
|---|---|---|
| 0:00–0:25 | Introduce the Midnight Fintech dashboard and its three workspace views. | A React/TypeScript client backed by a Java/Spring Boot API; fictional data and a focused teller workflow. |
| 0:25–0:55 | Check in Alex River, then Jordan Vale. Leave teller name `teller-1` and select **Serve next**. | Identity/name validation and FIFO ordering: Alex is served while Jordan waits. |
| 0:55–1:25 | Deposit $25 into Alex's account; withdraw $10. | Exact decimal money, balance $1,015, and a matching ledger entry for each successful action. |
| 1:25–1:45 | Attempt a $5,000 withdrawal. Then try a fractional-cent amount such as `0.001`. | A business-rule rejection and client/server validation; the balance remains $1,015. |
| 1:45–2:10 | Complete Alex's session, serve Jordan, and complete that session. Open **Activity**, then Alex's account from the directory. | Queue progression, independent account balances, and retained transaction history. |
| 2:10–2:35 | Explain saved-action recovery. Point to the browser test that drops a deposit response **after the actual server commits**, reloads, and retries. | Persisted UUID-keyed receipts plus the UI's tab-scoped saved command prevent a second deposit. |
| 2:35–3:00 | Close the empty branch, reopen it, then explain one architecture tradeoff. | Explicit workflow state. A database row lock serializes this single demo branch's writes for correctness across processes. |

Optional: check in and serve Casey, then confirm zero-balance account closure. The account remains in the directory as closed with a closure history entry. This changes the seed scenario for subsequent demos; use a fresh disposable database to repeat it.

## Recovery and persistence

For a live recovery demonstration, keep the same browser tab open. A lost connection leaves an unconfirmed action banner and pauses new mutations. Restore connectivity and choose **Retry saved action**; a reload preserves the original key and payload in that tab. Do not clear session storage or close an unresolved tab. Simply taking the browser offline *before* clicking demonstrates an unsent request, not proof of a committed-but-unacknowledged transaction; the automated browser test exercises that harder case.

Stop and restart the application from the same folder, then show that account balances, active queue tickets, ledger entries, and retry receipts survive. The release smoke check separately runs an extracted launcher from another working directory and a path containing spaces, then verifies transaction and retry persistence after a complete server restart.

## Explain the engineering

- **Separation:** controller for HTTP, service for workflows/transactions, repository for SQL, independent decimal rules.
- **Atomicity:** balance change, ledger append, and successful command receipt commit together. Injected ledger/receipt write failures verify rollback.
- **Concurrency:** the branch row lock and READ_COMMITTED transactions prevent competing withdrawals from overdrawing and competing claims from serving one ticket twice.
- **Idempotency:** a UUID maps to an operation/payload fingerprint and original JSON response. Reuse with a different payload is rejected. The frontend refreshes current state after receiving a historical retry response.
- **Persistence:** Flyway migrations, file-backed H2 for easy local use, PostgreSQL 17 mode verified by CI.
- **Testing:** 31 Java tests, 21 frontend cases, and four Chromium workflows: **56 distinct cases**, not 56 different financial scenarios. Running Java tests on two databases does not create additional distinct tests.
- **Boundaries:** local, single-branch simulation; no authentication, real payment processing, double-entry accounting, or measured production throughput. Teller labels do not enforce ownership. Automated axe checks cover selected states, not full accessibility conformance.

## Resume wording

**Banker — Full-Stack Teller & Queue Simulation**

Java · Spring Boot · React · TypeScript · PostgreSQL · H2 · Flyway · Playwright

Choose two or three bullets to fit the resume:

- Built a full-stack bank teller simulation with a responsive React/TypeScript dashboard and Spring Boot API for FIFO client service, account transactions, and searchable transaction history.
- Implemented exact decimal money handling, atomic balance/ledger updates, and persisted idempotency keys to prevent duplicate deposits and protect concurrent transaction workflows.
- Validated application behavior with 56 automated test cases and CI across H2 and PostgreSQL, including browser recovery from a lost response after a committed transaction.

For a compact one-line description:

> Built a Java/Spring Boot and React/TypeScript teller simulation with PostgreSQL persistence, FIFO queue management, atomic transactions, durable retry recovery, and automated browser tests.

Do not describe the project as a production banking platform, authenticated financial system, or deployed service. Test counts and safeguards are supported by the repository; transaction volume, customer adoption, or performance improvements have not been measured.
