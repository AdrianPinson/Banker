# Banker 1.0.0 — Midnight Fintech

Banker evolves a CSV/linked-list coursework project into a local full-stack teller and queue simulation.

- React/TypeScript Midnight Fintech workspace with horizontal navigation, live queue, teller sessions, account directory, transaction history, responsive layouts, and local fonts.
- Java/Spring Boot API with exact decimal deposits/withdrawals, no overdrafts, zero-balance closure, and retained history.
- Atomic balance, ledger, and command-receipt persistence; UUID-keyed retry recovery survives server restart, and tab-scoped UI recovery survives reload.
- Immediate log flushing in default file-backed H2, with forced-process restart checks for balances, ledger, and retry receipts.
- Flyway-managed fictional seeds, file-backed H2 by default, optional PostgreSQL 17 configuration and CI coverage.
- 31 Java tests, 21 frontend cases, four actual-JAR Chromium workflows, and selected automated desktop/mobile/dialog accessibility checks.
- Portable ZIP with the complete UI/API JAR, Windows and macOS/Linux launchers, checksums, build provenance, and interview demo documentation.

Requires **Java 17+**; Java is not bundled. The normal release demo requires no Node, Maven, Docker, Python, API key, or external service. Keep the application terminal open and visit http://127.0.0.1:8080. Default data is stored in the launcher's `data` folder. Existing databases are not reset on startup.

This is a local fictional simulation without authentication or real banking integration. Automatic CI prepares a **draft** release after a main-branch merge and successful validation; publication remains a separate action. Existing releases for this version are left unchanged.
