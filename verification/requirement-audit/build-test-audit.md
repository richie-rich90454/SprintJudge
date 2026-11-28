# Build & Test Audit

## Confirmed bugs

1. `application.yml:4-5` — `profiles.active: dev` hardcoded; bare `java -jar` boots DEBUG/native/insecure cookies in prod. Fix: default `prod` or require explicit profile.
2. `application.yml:66-73` — cookie `secure: false` in base config; only `application-prod.yml:17` overrides. Custom/staging profiles inherit insecure cookies. Fix: default `true`, opt out in dev.
3. `vite.config.ts:46-48` — coverage `include: ["src/**/*.ts"]` excludes all `.tsx`; the "100% coverage" claim never measures UI. Fix: include `.tsx` or add an E2E coverage gate.
4. `pom.xml:132-134` — `maven-antrun-plugin` unversioned and unmanaged by Boot parent; non-reproducible, Maven 4 fails. Fix: pin version.
5. `pom.xml:304-307,336-339` — `combine.self="override"` drops `-Xlint:all -Werror` in `nullaway`/`ecj`, disabling the warning gate; `-Pnullaway` runs no NullAway. Fix: append args; rename profile.
6. `Makefile:150-151` — `cygpath` used unconditionally in `test-e2e`; absent on Linux/macOS, leaving TMP/TEMP empty. Fix: gate on `$(OS)=Windows_NT`.
7. `ci.yml:92-93` — E2E boots `prod` over HTTP with `secure=true`, unlike `make test-e2e` (`Makefile:152`). Drift can break admin sessions. Fix: mirror the override.
8. `ci.yml:195-208` — legal gates (`make check-legal`, `Makefile:201-219,270`) never run in CI. Fix: add a step.
9. `frontend/package.json:13` — `lint` is only `tsc --noEmit`; no ESLint/Biome or CI lint. Fix: add a real linter or rename.
10. `frontend/package.json:14` — `oxfmt` writes only; no `--check` gate in CI. Fix: add `format:check` to CI.
11. `check-regression.mjs:16-22` — default gate skips verify/E2E/prod probe; "regression: clean" misleads. Fix: make `--full` default.
12. `playwright.config.ts:3-27` — no `webServer`/`globalSetup`; bare `npx playwright test` fails and boot logic is duplicated in Makefile/CI (drift, #7). Fix: declare `webServer`.

## Speculative concerns

13. `ci.yml:89-99` — backend started with `&`, step then `exit 0`s; child may be reaped before Playwright, causing flaky E2E. Fix: one step or `nohup`.
14. `SecurityConfig.java:81` — CSRF ignored on `POST /api/public/run,/practice` (`PublicController.java:315,336`); protection rests on SameSite=Lax. Fix: confirm statelessness or narrow the ignore.
15. `CoverageClosureTest.java:169-183` / `pom.xml:278-279` — 100% branch gate met by reflection/static-mock branch-touching tests with no assertions. Fix: lower threshold on defensive branches.
