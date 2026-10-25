#!/usr/bin/env bash
# Restore a SprintJudge SQLite backup. Stop the server first: replacing the
# file under a live process leaves the old WAL pointing at the new database.
set -euo pipefail

SRC="${1:?usage: restore-db.sh <backup.db> [target.db]}"
DB="${2:-${SPRINTJUDGE_DB_PATH:-sprintjudge.db}}"

[ -f "$SRC" ] || { echo "backup not found: $SRC" >&2; exit 1; }
echo "Stop SprintJudge before restoring."
cp "$SRC" "$DB"
rm -f "$DB-wal" "$DB-shm"
echo "restored: $SRC -> $DB"
