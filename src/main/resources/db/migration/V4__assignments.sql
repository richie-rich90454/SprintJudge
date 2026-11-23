-- Async assignments and LMS integration schema (SQLite).
-- Idempotent: applied at startup by AssignmentService until wired into DatabaseConfig.
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS assignments (
    id                TEXT PRIMARY KEY,
    quiz_id           TEXT NOT NULL REFERENCES quizzes(id),
    title             TEXT NOT NULL,
    description       TEXT,
    release_at        INTEGER,
    due_at            INTEGER,
    created_by        TEXT,
    created_at        INTEGER,
    lti_deployment_id TEXT
);

CREATE TABLE IF NOT EXISTS assignment_roster (
    id            TEXT PRIMARY KEY,
    assignment_id TEXT NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    student_id    TEXT NOT NULL,
    name          TEXT,
    email         TEXT,
    UNIQUE(assignment_id, student_id)
);

CREATE TABLE IF NOT EXISTS assignment_progress (
    id            TEXT PRIMARY KEY,
    assignment_id TEXT NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    student_id    TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'NOT_STARTED',
    started_at    INTEGER,
    submitted_at  INTEGER,
    score         INTEGER,
    max_score     INTEGER,
    graded_at     INTEGER,
    UNIQUE(assignment_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_assignment_roster_assignment ON assignment_roster(assignment_id);
CREATE INDEX IF NOT EXISTS idx_assignment_progress_assignment ON assignment_progress(assignment_id);
