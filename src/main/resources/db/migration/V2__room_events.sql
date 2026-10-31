-- SprintJudge event log (additive, idempotent)
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS room_events (
    room_id TEXT NOT NULL,
    seq INTEGER NOT NULL,
    type TEXT NOT NULL,
    actor_id TEXT,
    payload_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (room_id, seq)
);

CREATE INDEX IF NOT EXISTS idx_room_events_created ON room_events(room_id, created_at);
CREATE INDEX IF NOT EXISTS idx_room_events_actor ON room_events(actor_id, created_at);

CREATE TABLE IF NOT EXISTS room_snapshots (
    room_id TEXT PRIMARY KEY,
    seq INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS player_sessions (
    player_id TEXT PRIMARY KEY,
    room_id TEXT NOT NULL,
    rejoin_token TEXT NOT NULL,
    expires_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_player_sessions_room ON player_sessions(room_id);

CREATE TABLE IF NOT EXISTS question_calibration (
    question_id TEXT PRIMARY KEY,
    p_value REAL NOT NULL,
    discrimination REAL NOT NULL,
    p25_sec REAL NOT NULL,
    median_sec REAL NOT NULL,
    p75_sec REAL NOT NULL,
    attempts INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
