# Full-corpus audit, seventh wave — exploratory E2E and hardening

Six parallel agents added four exploratory E2E journeys (host controls, profiles/settings, judge edge cases, admin accessibility), a security re-audit, and a bank performance audit. Their findings were fixed in production where real defects were proven; spec assumptions were corrected where the application was right.

## Real application defects fixed

- CORS rejected Vite loopback origins and omitted PATCH, breaking profile creation/rename/link; loopback origins and PATCH are now allowed by default, never wildcard.
- Unsupported profile DELETE surfaced as 500; now a safe 405.
- Leaderboard resync was dropped when a coalesced delta jumped the sequence, leaving host scores at 0; resync snapshots are applied first and the in-flight flag is cleared after accepted deltas.
- QuestionView reset wiped the renderer's first answer, so the initial judge SUBMIT carried `response:null`; answers are now tagged by questionId.
- Wizard config was POSTed as an object while the API stores a JSON string; now serialized in the store.
- Validation alerts did not take focus on async save failure; a callback ref focuses them on mount.
- Admin quick-actions select had no accessible name; transient fade animations produced serious axe contrast violations during scans; both fixed.
- Deleting a quiz referenced by an assignment returned 500; now a safe 409.
- Bank adaptive recommendations triggered ~1,022 full 831 MB scans per request; a one-pass subject/unit/topic index, config cache, id/subject indexes and streaming parse reduce this to one slice read.
- Backend security: live WS authority from HttpSession, hard rate-limit caps, fixed WS error codes, safe exception reasons, header-only PIN lookup, roster-gated public assignment metadata, clean-text validation on imports, atomic limiters.

## Final verification

`node scripts/check-regression.mjs` default full gate: clean — 1,259 frontend tests at 100% TS/TSX coverage, backend verify with 100% Java instruction/branch coverage, 55 E2E specs, production health probe, bank/volume/legal/UI gates.
