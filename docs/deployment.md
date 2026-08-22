# Deployment

## Production topology

```mermaid
flowchart TB
    U["Players + Admins"] -->|"HTTPS / WSS"| NX["Nginx<br/>TLS · proxy headers · /ws upgrade"]
    NX -->|"HTTP :8080"| APP["systemd unit sprintjudge.service<br/>Java 25 · ZGC · virtual threads"]
    subgraph Host["Linux server - 12 cores · 48 GB RAM"]
        APP
        NJ["nsjail sandboxes<br/>chroot · rlimits · Semaphore(100)"]
        DB[("SQLite WAL<br/>/var/lib/sprintjudge/sprintjudge.db")]
    end
    APP --> NJ
    APP --> DB
```

## Windows production

`prod` runs on Windows too. The `native` executor drives gcc/g++/javac/node/python
directly (no nsjail on Windows), and the database defaults to a portable relative path.

```powershell
scripts\run-prod.ps1          # builds target\sprintjudge.jar and starts it with ZGC
# optional environment:
#   SPRINTJUDGE_DB_PATH=D:\data\sprintjudge.db
#   SPRINTJUDGE_PORT=8080
#   SPRINTJUDGE_EXECUTOR_MODE=native
```

The parent directory of `SPRINTJUDGE_DB_PATH` is created automatically on first boot.
Without an explicit path, the database (and a `.env` file if present) lives next to
the running jar, so the whole deployment is one folder you can copy around.
Verify any Windows launch in one command:

```powershell
scripts\verify-prod.ps1 -SkipBuild   # boots prod, checks REST + SPA + auth wall + WebSocket
```

For hardened Linux hosts keep `OPENQUIZ_EXECUTOR_MODE=nsjail` (the systemd unit already
pins it); on Windows, prefer running the JVM under a dedicated service account and rely
on the executor's whitelist, source cap, timeout-kill, and stdout-cap controls.

## Production (Linux — 12 cores / 48GB RAM)

- Run as a systemd service (see `deploy/sprintjudge.service`) with `-XX:+UseZGC` and virtual threads.
- Nginx reverse proxy terminates TLS and upgrades `/ws` to WebSocket (see `deploy/nginx-sprintjudge.conf`).
- Use the `prod` profile: `SPRINTJUDGE_EXECUTOR_MODE=nsjail`, executor `nsjail` binary on `PATH`.
- Weekly SQLite WAL checkpoint:

```bash
sqlite3 /var/lib/sprintjudge/sprintjudge.db "PRAGMA wal_checkpoint(TRUNCATE);"
```

## Development (Windows)

- Use the `dev` profile; the executor runs compile scripts inside WSL2 (Ubuntu).
- `spring.profiles.active=dev`.

## Concurrency

Executions are throttled by a `Semaphore(100)` (configurable via `sprintjudge.executor.max-concurrent`).
Stdout is capped at 1MB per test case; processes exceeding it are killed. Source is capped
at 64KB and attempts at 50 per player per question.

## Proxy headers

The WebSocket rate limiter keys on `X-Forwarded-For` (falling back to `X-Real-IP`).
Configure nginx to set both so per-client limiting is accurate:

```
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Real-IP $remote_addr;
```
