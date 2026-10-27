# Banker 1.0 — Midnight Fintech

A local bank teller simulation with fictional clients, a FIFO queue, exact decimal transactions, and durable retry recovery.

## Start

1. Install **Java 17 or newer**. This ZIP does not include Java; Maven, Node, Python, and Docker are not needed for the default demo.
2. Extract the **whole** ZIP into a writable folder.
3. Windows: double-click `start-banker.cmd` or run it from a terminal.
   macOS/Linux: open a terminal in this folder and run `sh start-banker.sh`.
4. Wait for the Spring Boot server to report that it started, then open **http://127.0.0.1:8080/**.
5. Keep the terminal open. Press **Ctrl+C** to stop the app; your demo data remains in the `data` folder beside the launcher.

The launchers always change into their own folder. Running them from another working directory still uses the same local data. Move the whole folder, including `data`, to keep that demo database. Do not run two copies against the same data folder. To upgrade, stop the app first and back up your demo data before replacing application files.

If port 8080 is busy, run `sh start-banker.sh --server.port=8081` or `start-banker.cmd --server.port=8081`, then open http://127.0.0.1:8081. If JAVA_HOME is set, the launchers use it; otherwise they use Java on PATH. A failed start leaves a diagnostic in the terminal. The app starts a local server; the launcher does not open your browser automatically.

## Try it

- Check in **Alex River**, account **DEMO-1001**; its starting balance is $1,000.
- Choose **Serve next**, deposit $25, withdraw $10, and complete the session.
- Search the client directory and view Alex's transaction history. The balance is $1,015.
- **Jordan Vale / DEMO-1002** starts with $250; **Casey Brook / DEMO-1003** starts with $0.
- Closing an account requires a zero balance; closing the branch requires no active tickets.

See [the three-minute demo guide](docs/demo-guide.md), [API contract](docs/api.md), and [architecture decisions](docs/architecture.md). Fonts and dependency license notices are included in the JAR and served at `/licenses/`.

If a transaction result is unconfirmed, use **Retry saved action** in the same browser tab. The original key survives a reload in tab-scoped session storage. Keep the tab open and do not clear its storage while a command is unresolved.

## Data and configuration

The default is file-backed H2. The fictional seed accounts are created only when the database is first initialized; startup does not reset an existing database. To reset a disposable demo, stop the app and remove only that demo's `data` folder. That permanently deletes its account changes and transaction history.

Optional PostgreSQL mode uses `BANKER_DB_URL`, `BANKER_DB_USER`, and `BANKER_DB_PASSWORD`; examples are in the source repository README. The server binds to localhost by default. This is a fictional simulation without authentication or real banking integrations. Teller names are workflow labels, not user identities.

## Verify files

`SHA256SUMS.txt` inside this folder lists checksums for its packaged files. The separate release `SHA256SUMS.txt` lists the ZIP checksum. `PROVENANCE.json` records the version and source commit for CI builds; local builds are explicitly labeled `local-build`.
