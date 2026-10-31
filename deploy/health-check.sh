#!/usr/bin/env bash
# SprintJudge production health check.
# Verifies the HTTP health endpoint, SQLite database integrity, and the
# nsjail-based executor toolchain. Exits non-zero on any failure.
set -euo pipefail

BASE_URL="${SPRINTJUDGE_BASE_URL:-http://127.0.0.1:${SPRINTJUDGE_PORT:-8080}}"
DB="${SPRINTJUDGE_DB_PATH:-/var/lib/sprintjudge/sprintjudge.db}"
NSJAIL="${SPRINTJUDGE_NSJAIL_BINARY:-/usr/bin/nsjail}"

fail() { echo "FAIL: $*" >&2; exit 1; }

echo "== SprintJudge health check =="

# 1. HTTP health endpoint
if ! curl -fsS "${BASE_URL}/actuator/health" >/tmp/sprintjudge-health.json 2>/dev/null; then
    fail "HTTP health endpoint unreachable: ${BASE_URL}/actuator/health"
fi
STATUS="$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("status","UNKNOWN"))' </tmp/sprintjudge-health.json 2>/dev/null || echo UNKNOWN)"
echo "http: ${STATUS}"
[ "$STATUS" = "UP" ] || fail "health status is ${STATUS}, expected UP"

# 2. Database integrity (SQLite WAL-safe: integrity_check reads through WAL)
if [ ! -f "$DB" ]; then
    fail "database file not found: $DB"
fi
command -v sqlite3 >/dev/null 2>&1 || fail "sqlite3 CLI is required"
INTEGRITY="$(sqlite3 "$DB" 'PRAGMA integrity_check;' 2>/dev/null || true)"
[ "$INTEGRITY" = "ok" ] || fail "database integrity check returned: ${INTEGRITY:-<error>}"
echo "db: ok (${DB})"

# WAL checkpoint sanity: warn (not fail) if the WAL is unusually large.
if [ -f "${DB}-wal" ]; then
    WAL_BYTES="$(stat -c %s "${DB}-wal" 2>/dev/null || stat -f %z "${DB}-wal" 2>/dev/null || echo 0)"
    echo "db-wal: ${WAL_BYTES} bytes"
fi

# 3. Executor toolchain (nsjail)
if [ ! -x "$NSJAIL" ]; then
    fail "nsjail binary not executable: $NSJAIL"
fi
if ! "$NSJAIL" --version >/dev/null 2>&1; then
    fail "nsjail --version failed: $NSJAIL"
fi
echo "executor: nsjail ok ($NSJAIL)"

echo "RESULT: all checks passed"
EOF
echo done