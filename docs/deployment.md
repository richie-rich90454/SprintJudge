# Deployment

## Production topology

```mermaid
flowchart TB
    U["Players + Admins"] -->|"HTTPS / WSS"| NX["Nginx<br/>TLS · proxy headers · /ws upgrade"]
    NX -->|"HTTP :$PORT (SPRINTJUDGE_PORT, default 8080)"| APP["systemd unit sprintjudge.service<br/>Java 25 · ZGC · virtual threads"]
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

```bash
make prod          # builds target/sprintjudge.jar and starts it with ZGC
# optional environment:
#   SPRINTJUDGE_DB_PATH=D:/data/sprintjudge.db
#   SPRINTJUDGE_PORT=8080
#   SPRINTJUDGE_EXECUTOR_MODE=native
```

The parent directory of `SPRINTJUDGE_DB_PATH` is created automatically on first boot.
Without an explicit path the database is `./database/sprintjudge.db` relative to the
working directory. `.env` is read by `make`, not by a plain `java -jar`; pass values
as environment variables or CLI flags for direct launches. Verify any Windows
launch in one command:

```bash
make verify-prod SKIP_BUILD=1   # boots prod, checks the REST health endpoints
```

For hardened Linux hosts keep `SPRINTJUDGE_EXECUTOR_MODE=nsjail` (the systemd unit already
pins it); on Windows, prefer running the JVM under a dedicated service account and rely
on the executor's whitelist, source cap, timeout-kill, and stdout-cap controls.

## Production (Linux — 12 cores / 48GB RAM)

- Run as a systemd service (see `deploy/sprintjudge.service`) with `-XX:+UseZGC` and virtual threads.
- The unit pins `--sprintjudge.executor.mode=nsjail` and reads an optional
  `/opt/sprintjudge/.env` for PORT, DB path and admin credentials.
- Nginx reverse proxy terminates TLS and upgrades `/ws` to WebSocket (see `deploy/nginx-sprintjudge.conf`).
  Both locations forward `X-Forwarded-For`/`X-Real-IP` so rate limiting sees
  real client IPs. Upstream is `127.0.0.1:8080` — match it with SPRINTJUDGE_PORT.
- Set `SPRINTJUDGE_COOKIE_SECURE=true` behind TLS nginx.
- Use the `prod` profile: `SPRINTJUDGE_EXECUTOR_MODE=nsjail` with the binary at
  `/usr/bin/nsjail` (the systemd unit pins the same absolute path).
- Weekly SQLite WAL checkpoint:

```bash
sqlite3 /var/lib/sprintjudge/sprintjudge.db "PRAGMA wal_checkpoint(TRUNCATE);"
```

## Backup, restore, upgrade

`make backup-db` uses `sqlite3 .backup`, which produces a consistent single
file while the server is running (WAL included). `make restore-db` replaces the
database; stop the service first.

```bash
make backup-db DB=/var/lib/sprintjudge/sprintjudge.db OUT=backups/before-upgrade.db
sudo systemctl stop sprintjudge
make restore-db FILE=backups/before-upgrade.db DB=/var/lib/sprintjudge/sprintjudge.db
sudo systemctl start sprintjudge
```

Upgrade:

1. `make backup-db`
2. Replace `sprintjudge.jar`.
3. `sudo systemctl restart sprintjudge`
4. Probe `curl -fsS http://127.0.0.1:8080/actuator/health` and open one quiz.

Rollback: stop the service, restore the backup, put the previous jar back,
start, and probe health again. SQLite migrations are forward-only, so a jar
rollback without the matching database backup is not supported.

## Fresh Linux install

The host must have **JDK 25** (Ubuntu 24.04's archive only has JDK 21, so install
Temurin/Adoptium 25), `/usr/bin/nsjail`, nginx, curl, python3 and sqlite3. No WSL,
no global npm tools, and no global Maven are needed - `./mvnw` and local
`node_modules` cover the build.

```bash
make package                       # local SPA + fat jar, no global installs
sudo -E bash deploy/install.sh     # prompts for the admin password; -E keeps env
bash deploy/health-check.sh
```

`deploy/install.sh` creates the `sprintjudge` service user, `/opt/sprintjudge`
(jar + `executor/compile-scripts`), `/var/lib/sprintjudge` (database), the `.env`
with the DB path and admin password, and installs/enables the systemd unit,
nginx vhost and logrotate config. The bootstrap vhost is HTTP-only so it does
not require certificates. After certbot has issued certificates, replace
`/etc/nginx/sites-available/sprintjudge` with
`deploy/nginx-sprintjudge.conf` (adjusting the upstream port if you chose a
non-default `SPRINTJUDGE_PORT`) and reload nginx to enable TLS.

Edit `/opt/sprintjudge/.env` to set CORS or change the port. Keep values shell-safe
(no spaces, quotes or `$`) because the systemd unit and Makefile source it as a
shell file.

## Verification status

- `bash -n` passes for `scripts/backup-db.sh`, `scripts/restore-db.sh`,
  `deploy/health-check.sh`, and `deploy/install.sh`.
- The jar builds, boots and passes all health checks on Windows with the `prod`
  profile (`make verify-prod`). The native executor was verified with real
  C/C++/Java/Node/Python submissions and uses no WSL.
- The systemd unit and nginx config must be validated on the target host with
  `systemd-analyze verify` and `nginx -t`; `deploy/install.sh` runs `nginx -t`
  before enabling anything.
- Boot the jar on the target Linux host and run `deploy/health-check.sh` before
  opening access to students.

## Public runner policy

`POST /api/public/run` is available in every profile. The native executor
enforces compile/run timeouts, output caps, and a per-IP rate limit. On Linux
production, prefer `SPRINTJUDGE_EXECUTOR_MODE=nsjail` for full sandboxing.

## Load probe

With a seeded backend running, probe 30 players and broadcast latency:

```bash
node scripts/load-test.mjs 30
```

The script joins a real practice room, submits one answer, and prints join,
question-start, and reveal percentiles plus any dropped clients. It exits
non-zero on a drop, so it can gate a classroom pilot.

## Security notes

- CSRF is enforced on `/api/admin/**`; the SPA echoes `XSRF-TOKEN` automatically.
  Public endpoints and the WebSocket handshake stay exempt.
- The CSP no longer allows inline or `data:` scripts.
- The service refuses to boot in `prod` with the default admin password.
- Set `SPRINTJUDGE_COOKIE_SECURE=true` behind TLS and keep HSTS enabled only
  when nginx terminates TLS.

Go-live checklist:

```mermaid
flowchart TB
    A["/usr/bin/nsjail + JDK 25"] --> B[".env set + perms"]
    B --> C["nginx up, certs live"]
    C --> D["make prod smoke test"]
    D --> E["QR join from a phone"]
    E --> F["submit + console run"]
    F --> G["check /actuator/prometheus"]
```

## Development (Windows)

- Use the `dev` profile; the executor defaults to `native` toolchains with no WSL.
  Toolchains must be on PATH (`gcc`, `g++`, `javac`, `java`, `node`, `python3`/`python`).
- `spring.profiles.active=dev`.

## Concurrency

Executions are throttled by an auto-sized semaphore (`cores ×
sprintjudge.executor.concurrency-factor`, floor 8, cap 512; override with
`sprintjudge.executor.max-concurrent`).
Stdout is capped at 1MB per test case; processes exceeding it are killed. Source is capped
at 64KB and attempts at 50 per player per question.

## Proxy headers

The WebSocket rate limiter keys on `X-Forwarded-For` (falling back to `X-Real-IP`).
Configure nginx to set both so per-client limiting is accurate:

```
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Real-IP $remote_addr;
```

## Upgrade and rollback (production)

### Pre-upgrade

1. Verify the toolchain on the host:
   ```bash
   java -version          # JDK 25 LTS
   /usr/bin/nsjail --version
   systemctl is-active sprintjudge
   ```
2. Take a WAL-safe backup:
   ```bash
   make backup-db DB=/var/lib/sprintjudge/sprintjudge.db OUT=backups/pre-upgrade-$(date +%Y%m%d-%H%M%S).db
   ```
3. Record the current jar checksum for rollback:
   ```bash
   sha256sum /opt/sprintjudge/sprintjudge.jar > /opt/sprintjudge/sprintjudge.jar.sha256
   ```

### Upgrade

1. Stop the service: `sudo systemctl stop sprintjudge`.
2. Replace `sprintjudge.jar` (and the SPA assets inside it).
3. Start: `sudo systemctl start sprintjudge`.
4. Probe: `curl -fsS http://127.0.0.1:8080/actuator/health` and run `deploy/health-check.sh`.
5. Open one quiz end to end (join, submit, console run).

### Rollback

SQLite migrations are forward-only, so a jar rollback requires the matching
database backup.

1. `sudo systemctl stop sprintjudge`.
2. Restore the database: `make restore-db FILE=backups/pre-upgrade-*.db DB=/var/lib/sprintjudge/sprintjudge.db`.
3. Put the previous jar back (use the recorded checksum / a saved copy).
4. `sudo systemctl start sprintjudge`.
5. Probe health and run `deploy/health-check.sh`.

### Health checks

- HTTP: `curl -fsS http://127.0.0.1:8080/actuator/health` (expect `{"status":"UP"}`).
- Database: `sqlite3 /var/lib/sprintjudge/sprintjudge.db 'PRAGMA integrity_check;'` (expect `ok`).
- Executor: `/usr/bin/nsjail --version`.
- Combined: `deploy/health-check.sh` (exits non-zero on any failure).

### Log rotation

- App logs go to journald by default (`journalctl -u sprintjudge -f`).
- File-based logs and nginx vhost logs rotate via `deploy/sprintjudge.logrotate`
  (install to `/etc/logrotate.d/sprintjudge`).

### Firewall (LAN port)

The app listens on `:8080` locally and nginx on `:80`/`:443`. Only expose what
the clients need. Example with ufw:

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp          # SSH (restrict to admin subnet if possible)
sudo ufw allow 80/tcp          # HTTP (redirect to HTTPS)
sudo ufw allow 443/tcp         # HTTPS / WSS
# Do NOT expose 8080 publicly; nginx is the only client on localhost.
sudo ufw deny 8080/tcp
sudo ufw enable
sudo ufw status verbose
```

For an intranet deployment without TLS, use the plain-HTTP server block in
`deploy/nginx-sprintjudge.conf` (commented) and set
`SPRINTJUDGE_COOKIE_SECURE=false`. Do not enable HSTS in that case.

### TLS policy

- Production: TLS terminates in nginx (Let's Encrypt). Set
  `SPRINTJUDGE_COOKIE_SECURE=true`.
- Intranet: plain HTTP on a trusted LAN is acceptable; keep HSTS disabled and
  cookies non-secure. Document the choice in the host's runbook.
