# SprintJudge

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

**SprintJudge** is a self-hosted, real-time quiz platform for classrooms and competitive
programming practice. A host opens a room, players join with a PIN and a nickname — no
accounts required — and everyone answers live while scores race up the leaderboard.
Beyond classic selection questions, players can write real code in C, C++, Java,
JavaScript, or Python that SprintJudge compiles, executes against hidden test cases,
and scores automatically.

## Core capabilities

- **Live rooms, real-time scoring.** Host-driven rounds with countdown timers, instant
  leaderboards, force-submit, timer extensions, and player kicks — all over WebSocket.
- **Built-in Online Judge.** Sandboxed compilation and automated test-case evaluation
  across multiple languages, with per-player best-score tracking.
- **Rich question formats.** From single-choice and multi-select to numeric tolerance,
  ordering puzzles, bug-spotting, code completion, and full-editor judged problems.
- **Bundled practice library.** Ships with a graded, multi-language question library that
  auto-seeds on first launch — ready to play out of the box.
- **Lightweight by design.** An embedded SQLite database (WAL mode) keeps deployment to
  a single portable file — no external database server required.

For architecture details, the full WebSocket protocol, database schema, and question
format specifications, see the [documentation site](#testing--documentation).

## Quick start

Prerequisites: JDK 25, Node.js 20+, GNU Make, and a POSIX shell (Git Bash or
MSYS2 on Windows). Maven is not installed globally: use the included `./mvnw`
wrapper. Frontend packages are local (`npm ci`), so no global npm installs are
needed. For the code judge, `gcc`/`g++`/`javac`/`java`/`node` and `python3` (or
`python`) must be on PATH; Windows native dev needs no WSL.

Production Linux needs JDK 25, nginx, nsjail at `/usr/bin/nsjail`, curl, python3
and sqlite3. See [docs/deployment.md](docs/deployment.md).

```bash
make check-env          # audit toolchain
make dev-backend        # API + WebSocket on :8080 (dev profile)

make dev-frontend       # UI on :5173 (proxies /api and /ws)
make dev                # both side by side (make -j2)
```

| Command | Purpose |
|---------|---------|
| `make test` | full backend test suite |
| `make test-frontend` | frontend typecheck |
| `make test-e2e` | browser end-to-end specs (needs `make dev-backend` running) |
| `make package` | SPA + single-jar production build (`SKIP_FRONTEND=1` skips the SPA) |
| `make prod` | single-jar production launch (`BUILD=1` rebuilds first) |
| `make verify-prod` | boot the prod jar on :8091 + HTTP health checks (`PORT=8091`) |
| `make format` | frontend formatter |
| `make clean` | remove `target/` + `frontend/dist/` |

The frontend dev server proxies `/api` and `/ws` to the backend, so both processes run
side by side during development.

## Run a CSA class

1. Start the packaged app with `make prod` or `java -jar target/sprintjudge.jar`.
2. Sign in at `/admin/login`.
3. On the dashboard, pick a CSA unit or **Mixed CSA** under **Start a CSA class**.
   Each set has sixteen questions and one judged Java problem.
4. Share the PIN or QR from the host screen. Students join at `/j/<PIN>` on the same
   network, with no account.
5. Use **Full screen** on the host screen when projecting. The stage shows the
   question, timer, answer distribution, the correct answer, the named
   misconception, and the explanation.
6. Open **Results** after the game for the podium, answer key, per-student review,
   class analysis, and CSV export.

Standard, Auto-pilot, Practice, Exam, Team, and Battle are supported. Team mode
scores every member's answers into a shared team total; students pick a team in
the lobby. Battle mode runs 1v1 rounds with byes, a full bracket, and a champion.
See `docs/teacher-runbook.md` for the classroom flow.

## Configuration

All runtime knobs are plain environment variables:

| Variable | Purpose |
|----------|---------|
| `SPRINTJUDGE_DB_PATH` | SQLite database location (parent dirs are created automatically) |
| `SPRINTJUDGE_EXECUTOR_MODE` | Judge strategy: `native` (local toolchains, no WSL) or `nsjail` (Linux sandbox) |
| `SPRINTJUDGE_PORT` | HTTP listen port (prod profile) |
| `SPRINTJUDGE_ADMIN_USERNAME` / `SPRINTJUDGE_ADMIN_PASSWORD` | Admin sign-in. Production refuses to boot with the default password. |
| `SPRINTJUDGE_COOKIE_SECURE` | Secure session cookies (default `true` in prod; set `false` only for plain-HTTP LAN use) |
| `SPRINTJUDGE_CORS_ALLOWED_ORIGINS` | Comma-separated origins when the UI is served from another host |
| `SPRINTJUDGE_AI_*` | Optional OpenAI-compatible AI grading (disabled by default) |

`.env` is loaded by `make` and by the systemd unit (`EnvironmentFile`), not by a
plain `java -jar`. Copy [.env.example](.env.example) and pass values as environment
variables or CLI flags when launching the jar directly. Real OS environment
variables always take precedence over `.env` values.

Admins sign in with a local account (form login on `/admin/login`); players never
create accounts. Set a strong `SPRINTJUDGE_ADMIN_PASSWORD` before deploying.

## Security overview

- **Room authorization.** Host-only commands require an authenticated admin session
  bound at the WebSocket upgrade plus an explicit host-role join; one host per room.
- **Test-case secrecy.** Hidden test cases and answer keys live exclusively behind
  admin-authenticated endpoints — the public surface can never leak them.
- **Input hardening.** Bean validation on REST DTOs, schema-checked WebSocket messages,
  whitelisted player nicknames, source-size caps, and language allowlists.
- **Process isolation modes.**
  - `native` — direct local toolchains (`gcc`, `g++`, `javac`, `java`, `node`, `python3` or `python`); no WSL, no extra runtime. Default for Windows dev.
  - `nsjail` — chroot + rlimit sandboxing; always use this on Linux production. Requires `/usr/bin/nsjail`.

## Testing & documentation

```bash
./mvnw test                    # backend suite (no global Maven install)
cd frontend
npx playwright install         # once; then:
npm run test:e2e               # browser end-to-end specs
```

The SPA is a single modern ESM bundle with a CSS target of Chrome 60+; Monaco and its
worker are loaded on demand for the code editor.

Full guides live in the VitePress documentation site ([source](docs), including
architecture deep-dives, the WebSocket protocol contract, database schema, deployment
playbooks, and the complete question-format reference):

| Guide | Contents |
|-------|----------|
| [Getting Started](docs/getting-started.md) | Setup paths, first run, admin sign-in |
| [Architecture](docs/architecture.md) | System overview, state machine, performance design |
| [Database Schema](docs/database.md) | Tables, indices, WAL operations |
| [API Reference](docs/api-reference.md) | REST surface, validation rules, CSRF & headers |
| [WebSocket Protocol](docs/websocket-protocol.md) | Message schemas, authorization, accuracy contract |
| [Deployment](docs/deployment.md) | Linux production topology and Windows production notes |
| [Contributing](docs/contributing.md) | Commit style, how to add a question type |

## Contributing

One logical change per commit; messages follow `feat(scope): description` /
`fix(scope): description`. Strict OOP on both sides of the wire, no raw SQL, no `any`
in TypeScript, flat UI only.

## Legal notice

This project is not affiliated with, endorsed by, or sponsored by any
external testing program. Official course frameworks may serve as
reference documents for scope and accuracy; no copyrighted materials
are reproduced. Every question is authored from first principles,
reviewed by a human, and attested as original. No model used here is
trained on protected testing-program content. See `docs/no-ap-branding.md`,
`docs/subject-rename-map.md`, and `docs/legal-safety.md`.

## License

Released under the [GNU GPL v3](LICENSE).
