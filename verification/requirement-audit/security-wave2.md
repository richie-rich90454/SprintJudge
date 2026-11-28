# Security re-audit (wave 2) — read-only, src/main/java

Prior fixes intact: CSRF-ignore list/token materialization; SameSite=Lax+HttpOnly+Secure cookies; admin origin filter; reduced public-assignment fields; capped executor output; nsjail fail-closed; PIN quiz gate; import caps; CSV escaping; atomic room transitions; CORS wildcard fail-closed. No ZIP/file-upload endpoint exists in Java sources.

12 remaining issues (C = confirmed in source, S = config/runtime-dependent):

1. [C] `GameWebSocket.java:296-323` — `liveAuth` re-reads `session.getUserPrincipal()`, a handshake snapshot. Logout/revocation mid-game leaves host powers (END_GAME/KICK). Fix: resolve live SecurityContext from the captured HttpSession each message.
2. [C] `LoginRateLimitFilter.java:44-52` — eviction removes only expired buckets; >10k live attacker IPs make it unbounded. Fix: hard-evict oldest entries.
3. [C] `JoinRateLimiter.java:43-50` — same; >10k fresh IPs defeat eviction, unbounded memory. Fix: strict cap/evict oldest.
4. [C] `GameWebSocket.java:145,154,242,276` — `e.getMessage()` from roomManager reaches anonymous players; SQL/ID leakage. Fix: fixed error codes, log server-side.
5. [C] `GlobalExceptionHandler.java:46-50` + `BankController.java:131` — echoes raw exception reason. Fix: whitelist reason codes.
6. [C] `PublicController.java:133-152` — PIN lookup returns full Quiz (`createdBy`, `template`). Fix: return `BankDto`.
7. [C] `AssignmentController.java:86-90` — public GET by arbitrary id, no roster check. Fix: reuse `requireRosterStudent`.
8. [C] `AssignmentController.java:47-83`, `RoomExportController.java:40-54` — one ADMIN role, no owner scoping; any admin reads all assignments/exports. Fix: filter by `createdBy`.
9. [C] `GameWebSocket.java:208-213` + `GameRoomManager.java:196-215` — admin joins any PIN as host; no room-ownership binding. Fix: bind host to creator token.
10. [C] `ImportExportService.java:162-197` — import checks length/type but skips `requireCleanText`; `<img onerror>` in titles bypasses the guard. Fix: validate title/description too.
11. [S] `PublicController.java:77-87,322-335` — `public-run=true` enables unsandboxed anonymous RCE in native mode. Fix: remove flag or force nsjail.
12. [C] `PublicController.java:103-115` — `long[]` mutated inside `compute` without per-key sync; concurrent calls lose counts. Fix: AtomicInteger per slot.

Skipped: no builds/tests run; static reading cannot confirm thread interleaving or deployed config values.
