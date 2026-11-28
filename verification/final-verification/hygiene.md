# Repo hygiene audit

## Status
- 15 modified tracked files (Makefile, docs/*, frontend/src/*, SecurityConfig.java + test, tools/gen/generate.js). Appear to be in-progress work — leave untouched.
- Untracked scratch: `frontend/e2e/_debug.spec.ts`, `frontend/e2e/_login_debug.mjs` (not ignored).

## Ignored / stale artifacts present
- Root logs: `verify.log`, `verify2.log`, `compile.log`, `compile2.log`, `compile3.log` (covered by `.gitignore:34 *.log`).
- `seed/drafts/` incl. `seed/drafts/pending-bank-batches/*.json` and `seed/drafts/jp-1.1-0001.draft.json` (covered by `.gitignore:75`).
- `target/`: absent. `executor/tmp/`: absent but covered by `.gitignore:46`.

## .gitignore coverage
- `executor/tmp/` — covered.
- Verification scratch — NOT covered: `verification/` is fully tracked (30 files across bank-verification, merged-bank-verification, ap-style-audit, new-course-audit, final-verification). If intended as transient, add an ignore rule; otherwise keep committed deliberately.

## Jar contamination
- No generator/script files under `src/main/resources/bank/` (JSON only). `tools/gen/generate.js` is tracked and outside resources — no generator copied into the jar.

## Branch history
- Conventional commits throughout (`style:`, `fix:`, `docs:`, `test:`). Clear and consistent.

## Recommended cleanup (exact paths)
- Delete: `frontend/e2e/_debug.spec.ts`, `frontend/e2e/_login_debug.mjs`.
- Delete stale ignored: root `*.log` files above; `seed/drafts/**`.
- Optionally ignore transient `verification/` scratch if not meant to ship.
