# Full-corpus audit, fourth wave — application bug review

Three read-only audits (backend, frontend, build/test) ranked confirmed defects. All confirmed findings in the owned files were fixed and re-verified.

## Fixed

| Area | Fixes |
|---|---|
| Backend concurrency | Late `+time` reveal closed; room start/submit transitions made atomic under the room lock; host/admin authority revalidated per message; WebSocket registration bounded atomically; `endGame` removal locked |
| Backend security | Signed/admin-gated backchannel test action with capped nonce map; rate limiter evicts only stale buckets; compiler output drained to a capped file with lenient locale decoding; nsjail bean available in every profile; public assignment GET returns launch fields only; exception messages no longer echoed; limiter ages from last request |
| Frontend | PWA reload only on real worker update; persisted music restored; boot-time skip target; localized error boundary with in-place retry; deferred blob URL revocation; initial answers emitted; reveal signal wired; queued code drafts survive refresh; keyboard reordering; stable renderer callbacks; applied-session analytics filter; accessible tabs and admin error routes; live reduced-motion updates |
| i18n | 17 screens and 12 components now use `useI18n`; 480 EN/AR keys added; two locale-switch tests |
| Build/config | Prod default profile and secure cookies by default; antrun version pinned; compiler warning flags restored; CI legal gate and E2E cookie override aligned; POSIX-safe `cygpath` usage; PMD XML reports avoid site-skin resolution |
| Bank | 186 compressed Python trace stems made runnable and re-executed; 44 new opening advisories cleared |

## Final gates

`check-bank` 0 warnings/0 errors; `check-volume` floors met; `make check-legal` clean; `make check-ui` clean; frontend 937 tests and 100% coverage; backend `verify` green with 100% instruction/branch coverage; full regression clean; E2E 30/30.

Skipped: coverage still measures `.ts` but not `.tsx`; `lint` remains typecheck-only; regression default remains `--full`-opt-in; Playwright has no `webServer`; CI E2E still starts the backend in a background step; three speculative frontend findings remain unproven at runtime; `components/ui`, `components/host`, and `components/authoring` were outside the i18n pass; Arabic translations were not natively reviewed. Risk: these are process and translation gaps, not known runtime failures.
