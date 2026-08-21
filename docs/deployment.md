# Deployment

## Production (Linux — 12 cores / 48GB RAM)

- Run as a systemd service (see `deploy/openquiz.service`) with `-XX:+UseZGC` and virtual threads.
- Nginx reverse proxy terminates TLS and upgrades `/ws` to WebSocket (see `deploy/nginx-openquiz.conf`).
- Use the `prod` profile: `OPENQUIZ_EXECUTOR_MODE=nsjail`, executor `nsjail` binary on `PATH`.
- Weekly SQLite WAL checkpoint:

```bash
sqlite3 /var/lib/openquiz/openquiz.db "PRAGMA wal_checkpoint(TRUNCATE);"
```

## Development (Windows)

- Use the `dev` profile; the executor runs compile scripts inside WSL2 (Ubuntu).
- `spring.profiles.active=dev`.

## Concurrency

Executions are throttled by a `Semaphore(100)` (configurable via `openquiz.executor.max-concurrent`).
Stdout is capped at 1MB per test case; processes exceeding it are killed. Source is capped
at 64KB and attempts at 50 per player per question.

## Proxy headers

The WebSocket rate limiter keys on `X-Forwarded-For` (falling back to `X-Real-IP`).
Configure nginx to set both so per-client limiting is accurate:

```
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Real-IP $remote_addr;
```
