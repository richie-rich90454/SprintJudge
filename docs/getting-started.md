# Getting Started

SprintJudge runs as a Spring Boot backend and a Vite/React frontend. In dev the
backend listens on `:8080` and the UI on `:5173`; the production fat jar listens
on `$SPRINTJUDGE_PORT` (default `:8080`, which is also what `.env.example` uses).

## Pick your setup path

```mermaid
flowchart TD
    A["Fresh machine"] --> B{"Windows?"}
    B -->|"yes"| C["make check-env<br/>audits JDK, Node, toolchains"]
    B -->|"no"| D["Verify Java 25 + Node 20+"]
    C --> E{"Compilers on PATH?<br/>gcc · g++ · javac · python"}
    E -->|"yes"| F["native executor — done"]
    E -->|"no"| G["Install compilers locally<br/>(no WSL, no global npm tools)"]
    D --> F
    G --> F
    F --> H["make dev-backend"]
    F --> I["make dev-frontend"]
    H --> J["Open http://localhost:5173"]
    I --> J
```

## Prerequisites

- Java 25 (the jar targets Java 25; JDK 21 will not run it)
- No global Maven: use the bundled `./mvnw` / `mvnw.cmd` wrapper
- Node.js 20+; frontend dependencies install locally with `npm ci`
- GNU Make plus a POSIX shell (Git Bash/MSYS2 on Windows; no WSL needed)
- Code judge toolchains on PATH: `gcc`, `g++`, `javac`, `java`, `node`, and
  `python3` or `python`
- Linux production: `/usr/bin/nsjail`, nginx, curl, python3 and sqlite3

## Run the backend

```bash
./mvnw spring-boot:run
```

Profiles: `dev` (default; `native` executor) and `prod` (`nsjail` on Linux).
Configure the executor and database in `src/main/resources/application.yml`.

## Port map

| Surface | Default | Override |
|---------|---------|----------|
| Backend dev (`./mvnw spring-boot:run`) | :8080 | application.yml |
| Frontend dev (`npm run dev`) | :5173 | vite.config.ts |
| Fat jar prod | :8080 | SPRINTJUDGE_PORT (default 8080) |
| Prod verifier | :8091 | make verify-prod PORT=... |

The Vite dev server proxies `/api` and `/ws` to `http://localhost:8080`.

## Run the frontend

```bash
cd frontend
npm install
npm run dev
```

```mermaid
flowchart LR
    B["Browser :5173"] --> V["Vite dev server"]
    V -->|"/api, /ws"| J["Backend :8080"]
    V -->|all other| S["SPA bundle"]
```

## Environment variables (all optional)

| Variable | Default | Effect |
|----------|---------|--------|
| SPRINTJUDGE_PORT | 8080 | Prod jar listen port |
| SPRINTJUDGE_DB_PATH | ./database/sprintjudge.db (prod override: /var/lib/sprintjudge/sprintjudge.db) | SQLite file (forward slashes) |
| SPRINTJUDGE_EXECUTOR_MODE | native dev, nsjail prod | native or nsjail |
| SPRINTJUDGE_CORS_ALLOWED_ORIGINS | empty (same-origin) | Extra browser origins |
| SPRINTJUDGE_ADMIN_USERNAME | admin | Form-login username |
| SPRINTJUDGE_ADMIN_PASSWORD | none in prod | Required; prod refuses `changeme` |
| SPRINTJUDGE_COOKIE_SECURE | false | true behind TLS nginx |
| SPRINTJUDGE_AI_ENABLED | false | AI feedback on failed cases |
| SPRINTJUDGE_AI_PROVIDER | openai | openai or llamacpp |
| SPRINTJUDGE_AI_ENDPOINT | — | e.g. `http://localhost:11434/v1` |
| SPRINTJUDGE_AI_MODEL | gpt-3.5-turbo | Model name |
| SPRINTJUDGE_AI_API_KEY | — | Empty for local llama.cpp |
| SPRINTJUDGE_AI_TIMEOUT_SEC | 30 | AI call budget |

## Quick start (GNU Make)

No WSL required. From a shell with GNU Make at the repo root:

```bash
make check-env          # one-time environment audit
make test               # prove the backend suite green on your box
make dev-backend        # terminal 1 — API + WebSocket on :8080
make dev-frontend       # terminal 2 — UI on :5173 (proxies /api and /ws)
make prod               # single-jar production: UI + API + bundled library
make verify-prod        # boot prod and probe the REST health endpoints
```

The dev profile defaults to the `native` executor: toolchains are invoked directly on
PATH, with no WSL and no global npm/Maven tools. The production build bundles the
compiled frontend and the graded library into one fat jar — first launch creates and
seeds the SQLite database automatically (only when the bank is empty). Run
`make seed-banks` once to add playable quiz sets for every subject.

## Admin authentication

Admins sign in with a username and password via form login at `/admin/login`
(session cookie, same-origin). Set the credentials as OS environment variables
or in a `.env` file (see `.env.example`):

```dotenv
SPRINTJUDGE_ADMIN_USERNAME=admin
SPRINTJUDGE_ADMIN_PASSWORD=change-me-please
```

OS environment variables always win over `.env`. (OAuth2/Entra ID support exists
in the codebase but is unwired in the MVP chain.)

## First run

1. Sign in as admin at `/admin/login`.
2. Create a quiz, add questions via the 4-step wizard.
3. Click **Host** to generate a 6-digit PIN.
4. Players join with the PIN and a nickname — either at the join screen or
   directly via the invite link `/j/<PIN>` (the same URL the host's QR code
   encodes). No account needed.
