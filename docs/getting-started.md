# Getting Started

SprintJudge runs as a Spring Boot backend (port `8080`) and a Vite/React frontend (port `5173`).

## Pick your setup path

```mermaid
flowchart TD
    A["Fresh machine"] --> B{"Windows?"}
    B -->|"yes"| C["scripts/check-env.ps1<br/>audits JDK, Node, toolchains"]
    B -->|"no"| D["Verify Java 25 + Node 20+"]
    C --> E{"Compilers on PATH?<br/>gcc · g++ · javac · python"}
    E -->|"yes"| F["native executor — done"]
    E -->|"no"| G["Install toolchains, or set<br/>SPRINTJUDGE_EXECUTOR_MODE=wsl"]
    D --> F
    G --> F
    F --> H["scripts/dev-backend.ps1"]
    F --> I["scripts/dev-frontend.ps1"]
    H --> J["Open http://localhost:5173"]
    I --> J
```

## Prerequisites

- Java 25 (virtual threads + ZGC enabled by default)
- Maven 3.9+ (or nothing at all — the bundled `mvnw.cmd` wrapper downloads it)
- Node.js 20+
- Linux production: `nsjail` on `PATH`
- Windows development: native compilers (`gcc`, `g++`, `javac`, `python`) for the default
  `native` mode, or WSL2 Ubuntu with the same packages for the `wsl` mode

## Run the backend

```bash
mvn spring-boot:run
```

Profiles: `dev` (default; `native` or `wsl` executor) and `prod` (`nsjail`). Configure the
executor and database in `src/main/resources/application.yml`.

## Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` and `/ws` to `http://localhost:8080`.

## Windows quick start

No WSL required. From PowerShell at the repo root:

```powershell
.\scripts\check-env.ps1      # one-time environment audit with guided fixes
.\scripts\run-tests.ps1      # prove the backend suite green on your box
.\scripts\dev-backend.ps1    # terminal 1 — API + WebSocket on :8080
.\scripts\dev-frontend.ps1   # terminal 2 — UI on :5173 (proxies /api and /ws)
.\scripts\run-prod.ps1       # single-jar production: UI + API + bundled library
.\scripts\verify-prod.ps1    # boot prod and probe REST + SPA + auth + WebSocket
```

The dev profile defaults to the `native` executor: toolchains are invoked directly, so a
fresh machine only needs its compilers on PATH. Set `OPENQUIZ_EXECUTOR_MODE=wsl` to run
the same judge inside Ubuntu instead. The production build bundles the compiled frontend
and the graded starter library into one fat jar — first launch creates and seeds the
SQLite database automatically (only when the bank is empty).

## Admin authentication

Admins authenticate via Microsoft Entra ID OAuth2. Provide credentials either as OS
environment variables or in a `.env` file (see `.env.example`):

```dotenv
SPRINTJUDGE_MS_CLIENT_ID=...
SPRINTJUDGE_MS_CLIENT_SECRET=...
SPRINTJUDGE_MS_TENANT_ID=common
```

The loader checks `<jar-folder>/.env` first, then `./env/.env`, then `./.env`.
OS environment variables always win over `.env`.

## First run

1. Sign in as admin at `/login`.
2. Create a quiz, add questions via the 12-step wizard.
3. Click **Host** to generate a 6-digit PIN.
4. Players join at the join screen with the PIN and a nickname.
