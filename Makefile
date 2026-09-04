# SprintJudge — GNU Make workflows (replaces scripts/*.ps1).
#
# Usage:
#   make check-env        audit toolchain
#   make dev-backend       API + WebSocket on :8080 (dev profile)
#   make dev-frontend      UI on :5173 (proxies /api and /ws)
#   make dev               both, in parallel (needs make -j support)
#   make test              full backend suite
#   make test-frontend     frontend typecheck
#   make test-e2e          Playwright browser specs (needs dev-backend running)
#   make package           SPA + fat jar (SKIP_FRONTEND=1 to skip the SPA)
#   make prod              production single-jar launch (BUILD=1 to rebuild first)
#   make verify-prod       boot prod jar on :8091 and hit health checks (PORT=8091)
#   make format            frontend formatter
#   make clean             remove build outputs (target/, frontend/dist/)
#
# Configuration lives in .env (see .env.example). Real OS environment
# variables always win over .env values.

SHELL := /bin/sh
MVNW := ./mvnw
JAR := target/sprintjudge.jar
FRONTEND := frontend
VERIFY_PORT ?= 8091
HEAP ?= 1g

# Load .env into the environment for every recipe (first .env wins: ./).
LOAD_ENV = if [ -f ./.env ]; then set -a; . ./.env; set +a; fi

.PHONY: help
help:
	@sed -n 's/^#   \(make [^ ]*\) *\(.*\)/\1 \2/p' $(MAKEFILE_LIST)

.PHONY: check-env
check-env:
	@echo "== SprintJudge environment check =="
	@java -version 2>&1 | head -1
	@test -f .mvn/wrapper/maven-wrapper.properties && echo "[OK]   Maven wrapper present (no Maven install needed)" || (echo "[MISS] Maven wrapper"; exit 1)
	@node --version
	@npm --version
	@for tool in gcc g++ javac python node; do \
		if command -v $$tool >/dev/null 2>&1; then echo "[OK]   native toolchain: $$tool"; \
		else echo "[WARN] native toolchain missing: $$tool (needed only for that OJ language)"; fi; \
	done
	@test -d $(FRONTEND)/node_modules || echo "[INFO] frontend deps not installed yet (make dev-frontend installs on first run)"
	@echo "RESULT: environment ready."

.PHONY: dev-backend
dev-backend:
	@$(LOAD_ENV); $(MVNW) spring-boot:run \
		-Dspring-boot.run.profiles=dev \
		"-Dspring-boot.run.jvmArguments=-Xms256m -Xmx256m -XX:+UseStringDeduplication -XX:+PerfDisableSharedMem"

.PHONY: dev-frontend
dev-frontend:
	@test -d $(FRONTEND)/node_modules || (echo "Installing frontend dependencies..." && npm install --prefix $(FRONTEND))
	npm run dev --prefix $(FRONTEND)

.PHONY: dev
dev:
	@$(MAKE) -j2 dev-backend dev-frontend

.PHONY: test
test:
	@$(LOAD_ENV); $(MVNW) test

.PHONY: test-frontend
test-frontend:
	npx --prefix $(FRONTEND) tsc --noEmit

.PHONY: test-e2e
test-e2e:
	npx --prefix $(FRONTEND) playwright install
	npm run test:e2e --prefix $(FRONTEND)

.PHONY: build-frontend
build-frontend:
	@test -d $(FRONTEND)/node_modules || npm install --prefix $(FRONTEND)
	npm run build --prefix $(FRONTEND)

.PHONY: package
package:
	@pkill -f 'sprintjudge\.jar' 2>/dev/null && sleep 1 || true
	@if [ "$(SKIP_FRONTEND)" != "1" ]; then $(MAKE) build-frontend; fi
	$(MVNW) -q clean package -DskipTests
	@test -f $(JAR) || (echo "BUILD INCOMPLETE: $(JAR) missing"; exit 1)
	@size_mb=$$(wc -c < $(JAR) | awk '{printf "%.1f", $$1/1048576}'); \
	if awk "BEGIN{exit !(($$size_mb+0) > 10)}"; then \
		echo "BUILD OK  jar=$${size_mb} MB (fat jar with SPA + library)"; \
	else \
		echo "BUILD INCOMPLETE: jar is $${size_mb} MB (expected >10 MB for fat jar)"; exit 1; \
	fi

.PHONY: prod
prod:
	@if [ "$(BUILD)" = "1" ] || [ ! -f $(JAR) ]; then $(MAKE) package; fi
	@$(LOAD_ENV); \
	heap="$${SPRINTJUDGE_HEAP:-$(HEAP)}"; \
	port="$${SPRINTJUDGE_PORT:-8080}"; \
	echo "Starting SprintJudge (prod) on http://localhost:$$port heap=$$heap"; \
	set -- --spring.profiles.active=prod; \
	if [ -n "$${SPRINTJUDGE_DB_PATH:-}" ]; then set -- "$$@" "--sprintjudge.db.path=$${SPRINTJUDGE_DB_PATH}"; fi; \
	if [ -n "$${SPRINTJUDGE_PORT:-}" ]; then set -- "$$@" "--server.port=$${SPRINTJUDGE_PORT}"; fi; \
	if [ -n "$${SPRINTJUDGE_EXECUTOR_MODE:-}" ]; then set -- "$$@" "--sprintjudge.executor.mode=$${SPRINTJUDGE_EXECUTOR_MODE}"; fi; \
	if [ -n "$${SPRINTJUDGE_MS_CLIENT_ID:-}" ]; then set -- "$$@" "--spring.security.oauth2.client.registration.microsoft.client-id=$${SPRINTJUDGE_MS_CLIENT_ID}"; fi; \
	if [ -n "$${SPRINTJUDGE_MS_CLIENT_SECRET:-}" ]; then set -- "$$@" "--spring.security.oauth2.client.registration.microsoft.client-secret=$${SPRINTJUDGE_MS_CLIENT_SECRET}"; fi; \
	tenant_id="$${SPRINTJUDGE_MS_TENANT_ID:-common}"; \
	set -- "$$@" "--spring.security.oauth2.client.provider.microsoft.authorization-uri=https://login.microsoftonline.com/$$tenant_id/oauth2/v2.0/authorize"; \
	set -- "$$@" "--spring.security.oauth2.client.provider.microsoft.token-uri=https://login.microsoftonline.com/$$tenant_id/oauth2/v2.0/token"; \
	if [ -n "$${SPRINTJUDGE_OAUTH2_REDIRECT_URI:-}" ]; then set -- "$$@" "--spring.security.oauth2.client.registration.microsoft.redirect-uri=$${SPRINTJUDGE_OAUTH2_REDIRECT_URI}"; fi; \
	if [ -n "$${SPRINTJUDGE_ADMIN_EMAILS:-}" ]; then set -- "$$@" "--sprintjudge.admin-emails=$${SPRINTJUDGE_ADMIN_EMAILS}"; fi; \
	exec java -XX:+UseZGC "-Xms$$heap" "-Xmx$$heap" \
		-XX:+UseStringDeduplication -XX:+PerfDisableSharedMem -XX:+UseCompactObjectHeaders \
		-jar $(JAR) "$$@"

.PHONY: verify-prod
verify-prod:
	@if [ "$(SKIP_BUILD)" != "1" ]; then $(MAKE) package; fi
	@echo "Booting prod jar on :$(VERIFY_PORT) for health checks..."
	@$(LOAD_ENV); \
	java -jar $(JAR) --spring.profiles.active=prod --server.port=$(VERIFY_PORT) >/tmp/sprintjudge-verify.log 2>&1 & \
	pid=$$!; \
	trap 'kill $$pid 2>/dev/null' EXIT; \
	ok=0; \
	for i in $$(seq 1 60); do \
		if curl -fsS http://localhost:$(VERIFY_PORT)/api/public/quizzes >/dev/null 2>&1; then ok=1; break; fi; \
		sleep 2; \
	done; \
	if [ "$$ok" = "1" ]; then echo "VERIFY OK: /api/public/quizzes serves on :$(VERIFY_PORT)"; \
	else echo "VERIFY FAILED (see /tmp/sprintjudge-verify.log)"; tail -20 /tmp/sprintjudge-verify.log; exit 1; fi

.PHONY: format
format:
	npm run format --prefix $(FRONTEND)

.PHONY: clean
clean:
	rm -rf target $(FRONTEND)/dist
