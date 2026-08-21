# Getting Started

OpenQuiz runs as a Spring Boot backend (port `8080`) and a Vite/React frontend (port `5173`).

## Prerequisites

- Java 25 (virtual threads + ZGC enabled by default)
- Maven 3.9+
- Node.js 20+
- Linux production: `nsjail` on `PATH`
- Windows development: WSL2 (Ubuntu) with `gcc`/`g++`/`javac`/`node`/`python3`

## Run the backend

```bash
mvn spring-boot:run
```

Profiles: `dev` (default, WSL2 executor) and `prod` (nsjail). Configure the executor and
database in `src/main/resources/application.yml`.

## Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` and `/ws` to `http://localhost:8080`.

## Admin authentication

Admins authenticate via Microsoft Entra ID OAuth2. Provide the following environment
variables before using the admin panel:

```bash
export OPENQUIZ_MS_CLIENT_ID=...
export OPENQUIZ_MS_CLIENT_SECRET=...
export OPENQUIZ_MS_TENANT_ID=common
```

## First run

1. Sign in as admin at `/login`.
2. Create a quiz, add questions via the 12-step wizard.
3. Click **Host** to generate a 6-digit PIN.
4. Players join at the join screen with the PIN and a nickname.
