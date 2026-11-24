# Final integration verification (2026-11-24)

Environment: Windows + MSYS2 bash (Makefile needs POSIX shell), node v24.13.0, JDK 25.0.1.

## Results

| Gate | Exit | Result |
|---|---|---|
| `node scripts/check-regression.mjs --full` | 0 | `regression: clean` (all 10 steps) |
| `make check-legal` | 0 | `trademarks: clean`, `ai firewall: clean`, `blueprints: attested`, `bank: clean` (329652 questions / 4594 files) |
| `make check-ui` | 0 | `check-ui: 99 files clean` |
| `make test-e2e` | 2 | FAILED: **7 failed, 23 passed** (30 tests) |

Regression highlights: backend verify `Tests run: 2922, Failures: 0, Errors: 0, Skipped: 0`, `All coverage checks have been met`, `BUILD SUCCESS`; `VERIFY OK: /actuator/health is UP on :8091`.

## Failing gate: `make test-e2e`

`npm run test:e2e` (Playwright, 1 worker) failed at:

- `e2e/admin.spec.ts:17`, `e2e/functionality.spec.ts:23,49,181`, `e2e/screens.spec.ts:166,177` — exact error:
  `Error: expect(page).toHaveURL(expected) failed` / `Expected pattern: /\/admin\/dashboard/` / `Received string: "http://127.0.0.1:8080/admin/login"` (timeouts 15000ms/5000ms).
  Backend log repeats: `java.lang.NullPointerException: Cannot invoke "org.springframework.security.web.csrf.CsrfToken.getToken()" because the return value of "jakarta.servlet.ServletRequest.getAttribute(String)" is null` at `com.sprintjudge.config.SecurityConfig$7.doFilter(SecurityConfig.java:101)`.
- `e2e/functionality.spec.ts:151` (native executor): `Error: python run failed` / `expect(received).toBeTruthy() Received: false`. Direct probe of `POST /api/public/run` returned HTTP 403 `{"type":"ERROR","message":"public_runner_disabled"}` (`sprintjudge.executor.public-run` defaults false; CI enables it).

Setup mismatch also present: tests default to `ci-admin-password` while `.env` loaded by `make dev-backend` sets `local-dev-only`; CI sets the test password and public-run flag. Backend/Vite were stopped after the run; no source files modified.
