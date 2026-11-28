# Full-corpus audit, fifth wave — zero remaining documented gaps

This pass closed every item previously marked skipped in waves 2–4.

## Bank

- All 329,652 questions scanned. 99 terse stems expanded, 73 stems given runnable code (42 prose fences replaced), 55 MCQs restored to four options, 699 meta artifacts removed, 1,321 near-duplicate openings diversified, 15 numeric keys rekeyed after stem-only recomputation.
- Exhaustive numeric recomputation covered every stem carrying a computable relation (6,506 independently derived; 15 confirmed wrong keys fixed; 0 flags remaining). The remaining numeric-bearing items are conceptual/historical data or reference unseen stimuli, so no independent value can be derived; they were covered by the structural, semantic, and execution audits.
- `check-bank`: 0 warnings, 0 errors; volume floors met; trademarks clean.

## Frontend

- Real oxlint and oxfmt `--check` gates added to `lint`/`format:check` and CI.
- Playwright `webServer` added; harness-owned servers disable it via `SPRINTJUDGE_E2E_HARNESS=1` to prevent double-boot races; CI E2E runs in one step; long admin flow timeout raised.
- `check-regression` is full by default (`--fast` opts out).
- All hardcoded UI strings moved through `useI18n`; EN/AR dictionaries hold 590 matching keys; host, authoring, and `src/ui` subtrees included; quality-engine messages localized at the display boundary.
- Coverage now includes both `.ts` and `.tsx` with 100% thresholds: 1,243 tests, 100% statements/branches/functions/lines across 95 files.
- Three previously speculative findings verified in a real browser with passing Playwright specs; all remaining unreachable defensive branches were removed rather than waived.

## Final verification

`node scripts/check-regression.mjs` default full gate: clean — bank, volume, unit volume, seed sets, legal, UI, frontend typecheck and tests, frontend build, backend verify (100% instruction/branch coverage), 33 E2E specs, and production health probe.
