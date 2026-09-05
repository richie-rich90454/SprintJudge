# SprintJudge — GNU Make workflows (run from git-bash / WSL / Linux / macOS).
#
#   make [tab]            list targets grouped by section
#   make run              production single-jar launch (BUILD=1 rebuilds first)
#   make dev              backend + frontend side by side (needs make -j)
#
# Configuration lives in .env (see .env.example). Real OS environment
# variables always win over .env values.

SHELL := /bin/sh
MVNW := ./mvnw
JAR := target/sprintjudge.jar
FRONTEND := frontend
VERIFY_PORT ?= 8091
HEAP ?= 1g

# Windows needs the .cmd shims; POSIX shells use the bare names.
ifeq ($(OS),Windows_NT)
NPM := npm.cmd
NPX := npx.cmd
else
NPM := npm
NPX := npx
endif

# Load .env into the environment for every recipe (./.env only).
LOAD_ENV = if [ -f ./.env ]; then set -a; . ./.env; set +a; fi

.DEFAULT_GOAL := help

##@ Start

.PHONY: help
help: ## Show this help, grouped by section.
	@awk 'BEGIN { section = "" } \
		/^##@ / { section = substr($$0, 5); printf "\n%s:\n", section; next } \
		/^[a-zA-Z0-9_-]+:.*## / { split($$0, parts, ":.*## "); target = $$1; sub(/:.*/, "", target); printf "  %-15s %s\n", target, parts[2] } \
	' $(MAKEFILE_LIST)
	@echo ""
	@echo "Flags: SKIP_FRONTEND=1  BUILD=1  SKIP_BUILD=1  PORT=8091  HEAP=1g"

.PHONY: run
run: prod ## Launch the production jar (alias for prod).

.PHONY: prod
prod: ## Production single-jar launch (BUILD=1 rebuilds first).
	@if [ "$(BUILD)" = "1" ] || [ ! -f $(JAR) ]; then $(MAKE) package; fi
	@$(LOAD_ENV); \
	heap="$${SPRINTJUDGE_HEAP:-$(HEAP)}"; \
	port="$${SPRINTJUDGE_PORT:-8080}"; \
	echo "Starting SprintJudge (prod) on http://localhost:$$port heap=$$heap"; \
	set -- --spring.profiles.active=prod; \
	if [ -n "$${SPRINTJUDGE_DB_PATH:-}" ]; then set -- "$$@" "--sprintjudge.db.path=$${SPRINTJUDGE_DB_PATH}"; fi; \
	if [ -n "$${SPRINTJUDGE_PORT:-}" ]; then set -- "$$@" "--server.port=$${SPRINTJUDGE_PORT}"; fi; \
	if [ -n "$${SPRINTJUDGE_EXECUTOR_MODE:-}" ]; then set -- "$$@" "--sprintjudge.executor.mode=$${SPRINTJUDGE_EXECUTOR_MODE}"; fi; \
	exec java -XX:+UseZGC "-Xms$$heap" "-Xmx$$heap" \
		-XX:+UseStringDeduplication -XX:+PerfDisableSharedMem -XX:+UseCompactObjectHeaders \
		-jar $(JAR) "$$@"

##@ Develop

.PHONY: dev
dev: ## Backend + frontend side by side (parallel).
	@$(MAKE) -j2 dev-backend dev-frontend

.PHONY: dev-backend
dev-backend: ## API + WebSocket on :8080 (dev profile).
	@$(LOAD_ENV); $(MVNW) spring-boot:run \
		-Dspring-boot.run.profiles=dev \
		"-Dspring-boot.run.jvmArguments=-Xms256m -Xmx256m -XX:+UseStringDeduplication -XX:+PerfDisableSharedMem"

.PHONY: dev-frontend
dev-frontend: ## UI on :5173 (proxies /api and /ws).
	@test -d $(FRONTEND)/node_modules || (echo "Installing frontend dependencies..." && $(NPM) install --prefix $(FRONTEND))
	$(NPM) run dev --prefix $(FRONTEND)

##@ Build

.PHONY: build
build: package ## Full production build (alias for package).

.PHONY: build-frontend
build-frontend: ## Build the SPA only.
	@test -d $(FRONTEND)/node_modules || $(NPM) install --prefix $(FRONTEND)
	$(NPM) run build --prefix $(FRONTEND)

.PHONY: package
package: ## SPA + fat jar (SKIP_FRONTEND=1 skips the SPA).
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

##@ Test

.PHONY: check-env
check-env: ## Audit the toolchain.
	@echo "== SprintJudge environment check =="
	@java -version 2>&1 | head -1
	@test -f .mvn/wrapper/maven-wrapper.properties && echo "[OK]   Maven wrapper present (no Maven install needed)" || (echo "[MISS] Maven wrapper"; exit 1)
	@node --version
	@$(NPM) --version
	@for tool in gcc g++ javac python python3 node; do \
		if command -v $$tool >/dev/null 2>&1; then echo "[OK]   native toolchain: $$tool"; \
		else echo "[WARN] native toolchain missing: $$tool (needed only for that OJ language)"; fi; \
	done
	@test -d $(FRONTEND)/node_modules || echo "[INFO] frontend deps not installed yet (make dev-frontend installs on first run)"
	@echo "RESULT: environment ready."

.PHONY: test
test: ## Full backend test suite.
	@$(LOAD_ENV); $(MVNW) test

.PHONY: test-frontend
test-frontend: ## Frontend typecheck.
	cd $(FRONTEND) && $(NPX) tsc --noEmit

.PHONY: test-e2e
test-e2e: ## Playwright specs (needs make dev-backend running).
	cd $(FRONTEND) && $(NPX) playwright install
	$(NPM) run test:e2e --prefix $(FRONTEND)

.PHONY: verify-prod
verify-prod: ## Boot prod jar on :8091 and probe health (PORT=, SKIP_BUILD=1).
	@if [ "$(SKIP_BUILD)" != "1" ]; then $(MAKE) package; fi
	@echo "Booting prod jar on :$(VERIFY_PORT) for health checks..."
	@$(LOAD_ENV); \
	logdir="$${TMPDIR:-$${TEMP:-/tmp}}"; \
	java -jar $(JAR) --spring.profiles.active=prod --server.port=$(VERIFY_PORT) >"$$logdir/sprintjudge-verify.log" 2>&1 & \
	pid=$$!; \
	trap 'kill $$pid 2>/dev/null' EXIT; \
	ok=0; \
	for i in $$(seq 1 60); do \
		if curl -fsS http://localhost:$(VERIFY_PORT)/api/public/quizzes >/dev/null 2>&1; then ok=1; break; fi; \
		sleep 2; \
	done; \
	if [ "$$ok" = "1" ]; then echo "VERIFY OK: /api/public/quizzes serves on :$(VERIFY_PORT)"; \
	else echo "VERIFY FAILED (see $$logdir/sprintjudge-verify.log)"; tail -20 "$$logdir/sprintjudge-verify.log"; exit 1; fi

##@ Maintain

.PHONY: format
format: ## Run the frontend formatter.
	$(NPM) run format --prefix $(FRONTEND)

.PHONY: clean
clean: ## Remove build outputs (target/, frontend/dist/).
	rm -rf target $(FRONTEND)/dist
