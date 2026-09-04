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

Prerequisites: JDK 25, Maven 3.9+ (`./mvnw` wrapper included), Node.js 20+,
GNU Make.

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

## Configuration

All runtime knobs are plain environment variables:

| Variable | Purpose |
|----------|---------|
| `SPRINTJUDGE_DB_PATH` | SQLite database location (parent dirs are created automatically) |
| `SPRINTJUDGE_EXECUTOR_MODE` | Judge isolation strategy: `native`, `wsl`, or `nsjail` |
| `SPRINTJUDGE_PORT` | HTTP listen port (prod profile) |
| `SPRINTJUDGE_MS_CLIENT_ID` / `_SECRET` / `_TENANT_ID` | Microsoft Entra ID OAuth2 for admin sign-in |

A `.env` file sitting next to the jar (or at the project root under Maven) is loaded
automatically — copy [.env.example](.env.example) as a starting point. Real OS
environment variables always take precedence over `.env` values.

Admins authenticate through Microsoft Entra ID; players never create accounts.

## Security overview

- **Room authorization.** Host-only commands require an authenticated OAuth2 session
  bound at the WebSocket upgrade plus an explicit host-role join; one host per room.
- **Test-case secrecy.** Hidden test cases and answer keys live exclusively behind
  admin-authenticated endpoints — the public surface can never leak them.
- **Input hardening.** Bean validation on REST DTOs, schema-checked WebSocket messages,
  whitelisted player nicknames, source-size caps, and language allowlists.
- **Process isolation modes.**
  - `native` — direct toolchain execution; fastest setup for development and Windows hosts.
  - `wsl` — judge runs inside a separate Linux VM on Windows development machines.
  - `nsjail` — chroot + rlimit sandboxing; always use this on Linux production.

## Testing & documentation

```bash
mvn test                       # backend suite (unit + concurrency + security)
cd frontend
npx playwright install         # once; then:
npm run test:e2e               # browser end-to-end specs
```

Dual-bundle output via `@vitejs/plugin-legacy`: evergreen browsers get modern ESM while
older engines receive a SystemJS + core-js polyfilled path. Browser floor is Chrome 60+
/ Safari 11+ / Firefox 60+.

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

## License

Released under the [GNU GPL v3](LICENSE).
