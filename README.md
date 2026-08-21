# OpenQuiz

The open-source, real-time coding quiz platform with a built-in **Online Judge (OJ)** engine.
Combines the live-host adrenaline of Kahoot with a multi-language code executor
(C, C++, Java, Node.js, Python) and **12 question formats**.

- **Backend:** Spring Boot 4.1.0, Java 25 (virtual threads + ZGC), JOOQ, Flyway, SQLite (WAL).
- **Realtime:** vanilla Jakarta `@ServerEndpoint` WebSocket, OOP session management.
- **Executor:** `nsjail` on Linux / WSL2 on Windows, throttled to 100 concurrent via `Semaphore`.
- **Frontend:** React 19 + TypeScript (OOP services), Zustand, RxJS, Tailwind, Monaco/CodeMirror.
- **Auth:** anonymous guest join for players; Microsoft Entra ID OAuth2 for admins.
- **License:** MIT.

## Quick start (development, Windows + WSL2)

```bash
# 1. Backend (Java 25, Maven)
./mvnw spring-boot:run            # or: mvn spring-boot:run

# 2. Frontend (Node 20+)
cd frontend
npm install
npm run dev                       # http://localhost:5173 -> proxies /api and /ws to :8080
```

The dev executor runs compile scripts inside WSL2 (`openquiz.executor.mode=wsl`).
Point `OPENQUIZ_EXECUTOR_MODE=nsjail` on Linux production.

## Project layout

```
OpenQuiz/
├── pom.xml
├── executor/compile-scripts/      # c.sh cpp.sh java.sh node.sh python.sh
├── src/main/java/com/openquiz/
│   ├── config/                    # App, Database, Security, WebSocket
│   ├── domain/                    # enums, models (records), dto
│   ├── repository/               # JOOQ DAOs (type-safe, no raw SQL)
│   ├── service/                   # GameRoomManager, ScoringEngine,
│   │                             #   CodeExecutor (+NsJail/Wsl), SubmissionProcessor,
│   │                             #   ImportExport, AdminSettings, Evaluation
│   ├── websocket/                # GameWebSocket, WebSocketSessionManager
│   ├── controller/               # AdminController, PublicController
│   └── exception/                # GlobalExceptionHandler
├── src/main/resources/
│   ├── db/migration/             # Flyway V1__init.sql
│   └── application*.yml
└── frontend/
    ├── src/services/             # WebSocketService, GameStateManager, AdminApiService,
    │                             #   ScoringService, QuestionRendererFactory
    ├── src/services/renderers/   # Abstract base + 12 concrete renderers
    ├── src/stores/               # useGameStore, useAdminStore, useUIStore (Zustand)
    └── src/views/                # Join, Question, Result, Host*, AdminDashboard, Wizard
```

## The 12 question formats

`MCQ`, `TRUE_FALSE`, `MULTIPLE_SELECT`, `NUMERIC`, `OUTPUT_PRED`, `FILL_BLANK`,
`DRAG_SORT`, `CLICK_BUG`, `CODE_COMPLETION`, `COMPLEXITY`, `OJ_FULL`, `OJ_PATCH`.

Adding a 13th format is a single edit to `QuestionRendererFactory.REGISTRY`
(frontend) and the `QuestionType` enum + `EvaluationService` (backend).

## Scoring

- **Selection types:** correct → linear speed-decay bonus; wrong → hard 0.
- **Coding types:** `(passed/total) * base * speedDecay(if fully solved)`; only the
  highest-scoring submission per player is kept; unlimited attempts until the timer ends.
- **Multiple attempts:** 2nd = 50% base, 3rd = 25% (admin-configurable, `mcq_max_attempts`).

## Import / Export

`GET /api/admin/export` returns the entire question bank as one JSON document.
`POST /api/admin/import` validates and (optionally) replaces the bank.

## Security model

- **Host authorization:** WebSocket host commands require an authenticated OAuth2 session
  bound at the upgrade handshake plus a host-role join; one host per room.
- **No answer leakage:** question configs (answer keys, hidden tests) are admin-only; the
  public API never returns them.
- **Input hardening:** Jakarta Bean Validation on REST DTOs, WebSocket message schema
  checks, player-name whitelist sanitization (20 chars), 64KB source cap, language
  whitelist (no script-path traversal), CSRF tokens on cookie sessions.
- **Abuse bounds:** per-IP JOIN rate limiting (10 failures/min), 500-player room cap,
  50 attempts per player per question, Semaphore(100) judge concurrency, 1MB stdout cap
  with process kill.
- **Headers:** HSTS, nosniff, deny-frames, strict CSP, same-origin referrer policy;
  CORS defaults to same-origin dev and is configurable in production.

## Deployment (Linux, 12 cores / 48GB)

- `systemd` service running the fat JAR with `-XX:+UseZGC` and virtual threads.
- Nginx reverse proxy (TLS) in front of `:8080`; `/ws` upgraded to WebSocket.
- Weekly SQLite WAL checkpoint.
