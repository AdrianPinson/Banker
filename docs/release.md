# Build and validate a release

Requires Java 17+, Maven 3.9+, Node 24 LTS, and Python 3.12+ to build/test the distribution. Running the finished application requires only Java 17+.

```sh
mvn -Pweb clean verify
npm --prefix frontend test
cd frontend
npx playwright install chromium
npm run test:e2e
cd ..
python scripts/package_release.py
python scripts/smoke_release.py
```

Outputs: `target/releases/banker-1.0.0.zip` and an external `SHA256SUMS.txt`. The ZIP includes `banker.jar`, both launch scripts, documentation, per-file checksums, and `PROVENANCE.json`. Packaging rejects a corrupt or backend-only JAR that lacks the web app/fonts. It never copies a database, `.env`, dependency installation, or credentials into the archive. No Java runtime is bundled.

The smoke check verifies outer and inner checksums, extracts into a temporary path containing spaces, invokes the platform launcher from a different directory, and checks the served frontend/assets/fonts/licenses. It then checks FIFO service, a retried deposit, withdrawal and session completion; restarts the server; and verifies balances, ledger, queue state, and persisted retry receipts. It uses a temporary file-backed database and a dynamically allocated localhost port. On Windows the smoke test terminates its own launcher/Java process tree, so the restart also exercises H2 recovery after process termination; on macOS/Linux it requests graceful termination.

CI runs the application/backend/frontend/browser suite, validates PostgreSQL, packages the already-tested web JAR, and verifies the extracted ZIP on Linux, Windows, and macOS with Java 17. The same portable ZIP is used on each OS. Native desktop installers, bundled runtimes, and non-Chromium GUI behavior are not part of this release.

After merge to `main`, all required jobs must pass before the workflow creates a **draft v1.0.0** release containing the ZIP and checksum. It does not publish. If a release already exists for the version, the workflow leaves it unchanged. CI provenance uses `GITHUB_SHA`; local builds are labeled `local-build` rather than claiming a clean source revision.

SHA-256 verification (run beside the downloaded ZIP/checksum file):

```sh
sha256sum -c SHA256SUMS.txt
# macOS alternative
shasum -a 256 -c SHA256SUMS.txt
```

PowerShell:

```powershell
Get-FileHash .\banker-1.0.0.zip -Algorithm SHA256
Get-Content .\SHA256SUMS.txt
```

Compare the displayed hashes. SHA-256 detects an accidental mismatch; it is not a code signature.
