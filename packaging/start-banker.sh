#!/usr/bin/env sh
set -eu
BANKER_APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$BANKER_APP_DIR"
if [ -n "${JAVA_HOME:-}" ]; then
  BANKER_JAVA="$JAVA_HOME/bin/java"
else
  BANKER_JAVA=java
fi
if ! command -v "$BANKER_JAVA" >/dev/null 2>&1; then
  echo 'Java 17 or newer is required. Install Java or set JAVA_HOME, then run this launcher again.' >&2
  exit 1
fi
echo 'Banker: open http://127.0.0.1:8080 after the server starts (unless you changed the port).'
echo "Local demo data: $BANKER_APP_DIR/data (unless BANKER_DB_URL overrides it)."
echo 'Keep this terminal open. Press Ctrl+C to stop Banker.'
exec "$BANKER_JAVA" -jar "$BANKER_APP_DIR/banker.jar" "$@"
