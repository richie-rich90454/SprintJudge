-- OpenQuiz initial schema (SQLite)
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
    id          TEXT PRIMARY KEY,
    email       TEXT UNIQUE,
    name        TEXT,
    avatar_url  TEXT,
    role        TEXT DEFAULT 'ADMIN',
    created_at  INTEGER
);

CREATE TABLE IF NOT EXISTS quizzes (
    id          TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    description TEXT,
    created_by  TEXT REFERENCES users(id),
    created_at  INTEGER,
    is_template BOOLEAN DEFAULT 0
);

CREATE TABLE IF NOT EXISTS questions (
    id                TEXT PRIMARY KEY,
    quiz_id           TEXT REFERENCES quizzes(id) ON DELETE CASCADE,
    title             TEXT NOT NULL,
    description       TEXT,
    question_type     TEXT CHECK(question_type IN (
        'MCQ','TRUE_FALSE','MULTIPLE_SELECT','NUMERIC','OUTPUT_PRED','FILL_BLANK',
        'DRAG_SORT','CLICK_BUG','CODE_COMPLETION','COMPLEXITY','OJ_FULL','OJ_PATCH')),
    languages_allowed TEXT,
    time_limit_sec    INTEGER,
    points_base       INTEGER,
    config            TEXT,
    order_index       INTEGER,
    created_at        INTEGER
);

CREATE TABLE IF NOT EXISTS game_sessions (
    id                   TEXT PRIMARY KEY,
    quiz_id              TEXT REFERENCES quizzes(id),
    pin_code             TEXT UNIQUE,
    host_user_id         TEXT REFERENCES users(id),
    status               TEXT CHECK(status IN ('LOBBY','ACTIVE','REVIEW','ENDED')),
    current_question_index INTEGER DEFAULT 0,
    started_at          INTEGER,
    ended_at            INTEGER,
    settings_override    TEXT,
    created_at          INTEGER
);

CREATE TABLE IF NOT EXISTS submissions (
    id               TEXT PRIMARY KEY,
    game_session_id  TEXT REFERENCES game_sessions(id) ON DELETE CASCADE,
    question_id      TEXT REFERENCES questions(id),
    player_name      TEXT,
    player_uuid      TEXT,
    response_data    TEXT,
    score_earned     INTEGER DEFAULT 0,
    is_correct       BOOLEAN DEFAULT 0,
    judge_log        TEXT,
    attempt_count    INTEGER DEFAULT 0,
    submitted_at     INTEGER
);

CREATE TABLE IF NOT EXISTS admin_settings (
    key         TEXT PRIMARY KEY,
    value       TEXT,
    updated_at  INTEGER
);

CREATE INDEX IF NOT EXISTS idx_game_sessions_pin ON game_sessions(pin_code);
CREATE INDEX IF NOT EXISTS idx_questions_quiz_order ON questions(quiz_id, order_index);
CREATE INDEX IF NOT EXISTS idx_submissions_session_question ON submissions(game_session_id, question_id);
CREATE INDEX IF NOT EXISTS idx_submissions_session_player ON submissions(game_session_id, player_name);
