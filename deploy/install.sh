#!/usr/bin/env bash
# Provision SprintJudge on a Linux host with systemd and nginx.
# Prerequisites (install with your package manager, no WSL, no npm/npm-global):
#   JDK 25, nsjail at /usr/bin/nsjail, nginx, curl, python3, sqlite3, bash
# Usage: sudo bash deploy/install.sh   (prompts for the admin password)
#   or:  printf '%s' 'strong-secret' | sudo SPRINTJUDGE_ADMIN_PASSWORD_STDIN=1 bash deploy/install.sh
set -euo pipefail

INSTALL_DIR="${SPRINTJUDGE_INSTALL_DIR:-/opt/sprintjudge}"
DATA_DIR="${SPRINTJUDGE_DATA_DIR:-/var/lib/sprintjudge}"
LOG_DIR="${SPRINTJUDGE_LOG_DIR:-/var/log/sprintjudge}"
JAR="${SPRINTJUDGE_JAR:-target/sprintjudge.jar}"
PORT="${SPRINTJUDGE_PORT:-8080}"

fail() { echo "install: $*" >&2; exit 1; }

[ -f "$JAR" ] || fail "jar not found: $JAR (run make package first)"
[ "$(id -u)" -eq 0 ] || fail "run as root (sudo bash deploy/install.sh)"
if [ -z "${SPRINTJUDGE_ADMIN_PASSWORD:-}" ]; then
    if [ "${SPRINTJUDGE_ADMIN_PASSWORD_STDIN:-}" = "1" ]; then
        IFS= read -r SPRINTJUDGE_ADMIN_PASSWORD || true
    else
        read -r -s -p "Admin password: " SPRINTJUDGE_ADMIN_PASSWORD
        echo
    fi
fi
[ -n "$SPRINTJUDGE_ADMIN_PASSWORD" ] || fail "admin password must not be empty"
command -v nginx >/dev/null 2>&1 || fail "nginx is required"
command -v systemctl >/dev/null 2>&1 || fail "systemd is required"
[ -x /usr/bin/nsjail ] || fail "nsjail must be executable at /usr/bin/nsjail"

# JDK 25 is mandatory: the jar is compiled for Java 25.
if ! /usr/bin/java -version 2>&1 | head -1 | grep -Eq 'version .(2[5-9]|[3-9][0-9])'; then
    fail "JDK 25+ is required at /usr/bin/java"
fi

id -u sprintjudge >/dev/null 2>&1 || useradd --system --home "$INSTALL_DIR" --shell /usr/sbin/nologin sprintjudge
mkdir -p "$INSTALL_DIR/executor/tmp" "$INSTALL_DIR/executor/cache" "$DATA_DIR" "$LOG_DIR"

install -m 0644 "$JAR" "$INSTALL_DIR/sprintjudge.jar"
cp -R executor/compile-scripts "$INSTALL_DIR/executor/compile-scripts"

if [ ! -f "$INSTALL_DIR/.env" ]; then
    umask 077
    cat > "$INSTALL_DIR/.env" <<ENVFILE
SPRINTJUDGE_DB_PATH=${DATA_DIR}/sprintjudge.db
SPRINTJUDGE_PORT=${PORT}
SPRINTJUDGE_ADMIN_USERNAME=${SPRINTJUDGE_ADMIN_USERNAME:-admin}
SPRINTJUDGE_ADMIN_PASSWORD=${SPRINTJUDGE_ADMIN_PASSWORD}
ENVFILE
fi

chown -R sprintjudge:sprintjudge "$INSTALL_DIR" "$DATA_DIR" "$LOG_DIR"
# systemd reads EnvironmentFile as root before dropping to User=sprintjudge.
# Keep the secret root-only so jailed submissions running as sprintjudge cannot
# read it from disk.
chown root:root "$INSTALL_DIR/.env"
chmod 400 "$INSTALL_DIR/.env"

install -m 0644 deploy/sprintjudge.service /etc/systemd/system/sprintjudge.service
install -m 0644 deploy/nginx-sprintjudge.conf /etc/nginx/sites-available/sprintjudge
install -m 0644 deploy/sprintjudge.logrotate /etc/logrotate.d/sprintjudge
ln -sfn /etc/nginx/sites-available/sprintjudge /etc/nginx/sites-enabled/sprintjudge

nginx -t
systemctl daemon-reload
systemctl enable --now sprintjudge
systemctl enable --now nginx

echo "install: SprintJudge installed. Edit /opt/sprintjudge/.env to set CORS/TLS, then:"
echo "  bash deploy/health-check.sh"
