#!/usr/bin/env bash
# Consistent online SQLite backup. Safe while SprintJudge is running: the
# .backup command reads through WAL and produces a single complete file.
set -euo pipefail

DB="${1:-${SPRINTJUDGE_DB_PATH:-sprintjudge.db}}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${2:-backups/sprintjudge-${STAMP}.db}"

[ -f "$DB" ] || { echo "database not found: $DB" >&2; exit 1; }
command -v sqlite3 >/dev/null 2>&1 || {
  echo "sqlite3 CLI is required for a consistent backup" >&2
  exit 1
}

mkdir -p "$(dirname "$OUT")"
sqlite3 "$DB" ".backup '$OUT'"
echo "backup: $DB -> $OUT"
