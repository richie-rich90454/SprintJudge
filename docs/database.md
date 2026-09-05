# Database Schema

SprintJudge uses a single portable SQLite file with WAL journaling. All access goes through
parameterized JOOQ queries; the schema is applied at startup by an idempotent DDL runner
(`SqlScriptRunner`), since Flyway offers no SQLite support.

```mermaid
erDiagram
    USERS ||--o{ QUIZZES : "creates"
    QUIZZES ||--o{ QUESTIONS : "contains"
    QUIZZES ||--o{ GAME_SESSIONS : "instantiates"
    USERS ||--o{ GAME_SESSIONS : "hosts"
    GAME_SESSIONS ||--o{ SUBMISSIONS : "receives"
    QUESTIONS ||--o{ SUBMISSIONS : "answered by"

    USERS {
        TEXT id PK
        TEXT email UK
        TEXT name
        TEXT avatar_url
        TEXT role "default ADMIN"
        INTEGER created_at
    }
    QUIZZES {
        TEXT id PK
        TEXT title
        TEXT description
        TEXT created_by FK
        INTEGER created_at
        BOOLEAN is_template
    }
    QUESTIONS {
        TEXT id PK
        TEXT quiz_id FK
        TEXT title
        TEXT description
        TEXT question_type "CHECK 12 types"
        TEXT languages_allowed "JSON array"
        INTEGER time_limit_sec
        INTEGER points_base
        TEXT config "JSON payload with answer key - admin only"
        INTEGER order_index
        INTEGER created_at
    }
    GAME_SESSIONS {
        TEXT id PK
        TEXT quiz_id FK
        TEXT pin_code UK "6 digits"
        TEXT host_user_id FK
        TEXT status "LOBBY ACTIVE REVIEW ENDED"
        INTEGER current_question_index
        INTEGER started_at
        INTEGER ended_at
        TEXT settings_override
        INTEGER created_at
    }
    SUBMISSIONS {
        TEXT id PK
        TEXT game_session_id FK
        TEXT question_id FK
        TEXT player_name
        TEXT player_uuid
        TEXT response_data "JSON"
        INTEGER score_earned
        BOOLEAN is_correct
        TEXT judge_log
        INTEGER attempt_count
        INTEGER submitted_at
    }
    ADMIN_SETTINGS {
        TEXT key PK
        TEXT value
        INTEGER updated_at
    }
```

## Indices

| Table | Index | Purpose |
|-------|-------|---------|
| `game_sessions` | `pin_code` | Fast lobby join lookups |
| `questions` | `(quiz_id, order_index)` | Ordered question fetch per quiz |
| `submissions` | `(game_session_id, question_id)` | Round result aggregation |
| `submissions` | `(game_session_id, player_name)` | Per-player attempt history |
| `submissions` | `(game_session_id, player_uuid)` | Rejoin-safe history |

## Delete cascades (read before deleting)

```mermaid
flowchart LR
    QZ["delete quiz"] --> QD["questions cascade"]
    GS["delete session"] --> SD["submissions cascade"]
    Q["delete question"] --> SB["submissions BLOCK<br/>FK restrict"]
```

- Deleting a **quiz** cascades to its questions.
- Deleting a **game session** cascades to its submissions.
- Deleting a **question** is RESTRICTED while submissions reference it — the
  audit trail is never orphaned, and the judge never persists rows for
  questions that no longer exist (the pipeline drops those outcomes
  before the write buffer).

## Operational notes

- **WAL mode** is enabled in the JDBC URL, along with a 5-second busy timeout and
  foreign-key enforcement, so readers never block the single writer for long.
- SQLite returns Integer for BIGINT columns — every read goes through
  `RepoUtil.asLong()`, never a direct cast.
- The database is one portable file: back it up with any file copy while idle.
- An unreachable `SPRINTJUDGE_DB_PATH` fails fast at boot instead of silently
  running on `./sprintjudge.db`.
- Production should run a weekly checkpoint:
  `sqlite3 /var/lib/sprintjudge/sprintjudge.db "PRAGMA wal_checkpoint(TRUNCATE);"`
- Question `config` payloads embed answer keys and hidden test cases — they are exposed
  exclusively through admin-authenticated endpoints.
- `admin_settings.value` is nullable in DDL: imports reject null values up
  front so scoring never NPEs on a poisoned setting.
