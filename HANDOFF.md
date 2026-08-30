# SprintJudge — Agent Handoff

> **This file is NOT committed to git.** It exists solely for agent-to-agent
> knowledge transfer. Do not `git add` it.

---

## Project Identity

**SprintJudge** (formerly OpenQuiz) — self-hosted, real-time coding quiz platform
with a built-in multi-language Online Judge. GPLv3 license.

## What Works Right Now

- Backend: Spring Boot 4 / Java 25 / SQLite WAL — 298 tests green
- Frontend: React 19 + Vite 8 + Tailwind v4 + Framer Motion + Tone.js — tsc clean, build clean
- Fat jar: single jar bundles SPA + API + WebSocket + 2000-question library
- Auth: simple username/password form login (MVP) — OAuth2 code preserved but unwired
- Question library: 560 sets × 10 questions auto-seeds on first boot (5120 questions total)
  - Languages: Java, Python, C++, C, JavaScript/Node.js
  - Topics: Cybersecurity, Web Dev (HTML/CSS/JS), Math (logic, sets, probability, combinatorics, algebra)
  - Difficulty: Easy, Medium, Hard per topic
- Audio: Tone.js chiptune engine — procedural 8-bit music + SFX (correct/wrong/timer/victory/combo)
- Live code runner: xterm.js canvas console + POST /api/run endpoint (Juicemind-style)
- TanStack Query for REST API caching + RxJS for WebSocket (separate concerns)
- Theme: system preference detection + manual toggle (dark/light)
- Fonts: strict Noto Sans + Noto Sans Mono (CSS enforcement, no fallbacks)
- Game modes (6): STANDARD, AUTO_PILOT, PRACTICE, EXAM, TEAM, BATTLE
  - Practice: no timer, instant feedback, auto-advance
  - Exam: total time limit, leaderboard hidden until end
  - Team: create/join teams, team scoring
  - Battle: 1v1 matchmaking, bracket system
- Admin dashboard: top-tabs layout (Dashboard, Quizzes, Questions, Games, Settings)
- Question page: left-right layout (question + editor side-by-side, no scroll)
- Visual effects: confetti, screen shake, glow, framer-motion transitions
- Submission feedback: SUBMISSION_RESULT now shows pass/fail + score live
- Full gameplay loop verified end-to-end after the Aug 2026 bug-fix sweep

### Recent bug-fix sweep (Aug 2026) — all fixed, regression-tested

| Bug | Root cause | Fix |
|-----|-----------|-----|
| No server broadcasts reached anyone | `WebSocketSessionManager.register()` had zero callers | Registered/unregistered in `GameWebSocket.onOpen/onClose` |
| "Start round" skipped question 0 / instantly ended 1-question games | `nextQuestion()` incremented index unconditionally | Only advances when status ≠ LOBBY |
| First JOIN of every session was silently lost | Client `send()` dropped messages while socket was CONNECTING (`connect()` then `join()` are synchronous) | `WebSocketService` queues outbound until `onopen` |
| Coding scores never hit leaderboard | `SubmissionProcessor` passed sessionId (UUID) into pin-keyed `broadcastLeaderboard` → `NumberFormatException` per OJ submit | PIN threaded through `processCoding(sessionId, pin, ...)` |
| Leaderboard didn't update on join/leave/kick | Join delta recorded but coalescing tick never armed by roster events | join/leave/kick call `broadcastLeaderboard(pin)` |
| Departed players stayed on client leaderboards | Delta protocol has no tombstone | Client reconciles leaderboard against `ROOM_STATE` roster |
| Tied-score players couldn't be removed from skip list | `remove(uuid, score)` traversed with seq=MIN_VALUE, stopping before equal-score nodes | Real joinSeq passed through removal |
| Resync cascaded phantom seq gaps to other clients | `fullBatch()` inflated the ledger seq by roster size | Resync stamps current seq without mutating the ledger |
| Verbose programs misjudged as timeouts | Judge read stdout via pipe after `waitFor` (pipe-buffer deadlock) | Output redirected to per-case file, capped-read after exit |
| First-time C/C++ submissions ran a missing binary | `CompileArtifactCache.put` MOVED the fresh binary out of the run dir | Copies instead |
| OJ editor was a fake (Monaco imported, textarea mounted) | Dead preload in `QuestionView` + imperative renderers used textareas | New `services/CodeEditor.ts`: lazy real Monaco, textarea fallback; dead `MonacoLazy`/`CodeMirrorLight` deleted |

## Architecture Summary

```
target/sprintjudge.jar          ← fat jar: UI + API + WS + seed library
src/main/java/com/sprintjudge/  ← backend (Spring Boot 4)
frontend/src/                   ← frontend (React 19 + TS)
docs/                           ← VitePress site
scripts/                        ← PS1 helpers (5.1-compatible)
seed/sets/<lang>/               ← per-set JSON question files
executor/compile-scripts/       ← c.sh cpp.sh java.sh node.sh python.sh
```

### Key backend packages

| Package | What it does |
|---------|-------------|
| `config/` | SecurityConfig (form login), DatabaseConfig (SqlScriptRunner), SpaWebConfig, ExecutorSizingConfig, WebSocketConfig |
| `service/` | GameRoomManager (rooms + round flow + submit rules + AUTO_PILOT auto-advance), ScoringEngine (fraction-based), SubmissionProcessor (CompletableFuture<Boolean> for @Async), SubmissionWriteBuffer (250ms batch), BroadcastScheduler (16ms tick), MetricsService, ImportExportService |
| `service/leaderboard/` | RankedSkipList (Redis zskiplist algorithm), DeltaLedger (seq-numbered deltas), LiveLeaderboard facade |
| `service/room/` | RoomRegistry (IntObjectMap keyed by int PIN) |
| `service/executor/` | CodeExecutor → NativeExecutor (Windows dev+prod + live run()), WslExecutor (dev), NsJailExecutor (Linux prod); CompileArtifactCache (SHA-256 keyed LRU, copies never moves); RunRequest/RunResult records |
| `websocket/` | GameWebSocket (@ServerEndpoint), SecureHandshakeConfigurator (principal + IP at upgrade), WebSocketSessionManager (session registry — MUST be fed by onOpen/onClose), JoinRateLimiter |
| `repository/` | JOOQ DAOs — all BIGINT reads go through RepoUtil.asLong() |

### Key frontend structure

```
src/services/AudioEngine.ts       ← Tone.js chiptune synth + procedural music + SFX
src/services/MotionService.ts     ← Framer Motion presets (replaces old GSAP)
src/services/WebSocketService.ts  ← RxJS wrapper; QUEUES sends while CONNECTING
src/services/GameStateManager.ts  ← game state machine + delta protocol + SUBMISSION_RESULT
src/services/CodeEditor.ts        ← lazy Monaco with textarea fallback (OJ questions)
src/services/AdminApiService.ts   ← TanStack Query backed REST client
src/services/renderers/           ← BaseQuestionRenderer + 12 concrete renderers + xterm.js console
src/hooks/useVirtualWindow.ts     ← dependency-free list virtualization
src/stores/useTimerStore.ts       ← isolated timer ticks (no leaderboard re-render)
src/stores/useUIStore.ts          ← Zustand view state ("router") + system theme detection
```

### Language handling for OJ questions (smart rules)

- `Question.languagesAllowed` lives at the QUESTION TOP LEVEL (not inside config).
  Seeds use e.g. `["c"]`; the wizard sets it from `emptyDraft()`.
- Backend enforces it in `GameRoomManager.submit`: disallowed language →
  ERROR "Language not allowed for this question" to that player only.
- Frontend threads it DTO → `QuestionRendererHost` → factory → renderer base.
  Single-language questions render no dropdown at all; multi-language render
  only allowed options. Default = `config.defaultLanguage` when permitted,
  else first allowed. Never a mismatched selection.

## Authentication (current MVP state)

Simple username/password via Spring Security `formLogin()`:

```env
SPRINTJUDGE_ADMIN_USERNAME=admin
SPRINTJUDGE_ADMIN_PASSWORD=change-me-please
```

- Login page at `/admin/login` (rendered by AdminLoginView.tsx in the SPA)
- POSTs to `/admin/login` (Spring Security's processing URL)
- Session cookie (SameSite=Lax, HttpOnly) persists across same-origin requests
- The WS handshake carries the session cookie; host commands require BOTH
  `role=host` JOIN and an authenticated upgrade (SecureHandshakeConfigurator)
- CSRF is **disabled entirely** for the MVP — re-enable when adding public forms

### OAuth2 (unwired but preserved)

All OAuth2 classes remain in the codebase:
- `OAuth2LoginSuccessHandler.java` — upserts user, redirects to /admin/dashboard
- `OAuth2CallbackController.java` — bridges custom callback paths
- `SecurityConfig.oidcUserService()` was removed from the active chain but the
  admin-email allowlist pattern is documented in git history

To re-wire OAuth2:
1. Restore the oauth2 yml block in application.yml (see git history)
2. Re-add `.oauth2Login(...)` to SecurityConfig with oidcUserService
3. Re-add `.exceptionHandling()` AFTER `.oauth2Login()` (critical ordering)
4. Set SPRINTJUDGE_MS_* env vars and add redirect URI to Azure Portal
5. Azure Portal redirect URI must be `{baseUrl}/login/oauth2/code/microsoft`
   (Spring Security hardcodes this path — custom paths require a bridge controller)

## Critical Gotchas (learned the hard way)

1. **SQLite returns Integer for BIGINT columns** — always use `RepoUtil.asLong()`,
   never cast directly to Long.
2. **WS sessions MUST be registered in GameWebSocket.onOpen/onClose.** If a refactor
   drops those calls, every broadcast silently no-ops while direct replies still work
   — the app LOOKS alive (join succeeds) but nothing else happens.
3. **Client sends queue while CONNECTING** (`WebSocketService.pending`). Don't
   "simplify" send back to an OPEN check — connect()+join() run synchronously.
4. **Rolldown/Vite 8 crashes on Chrome 49 targets** — minimum safe floor is
   Chrome ≥ 60. Do not set lower targets.
5. **`[Environment]::SetEnvironmentVariable` doesn't work reliably in PS5.1 dot-
   sourced scripts** — parse .env in PS1 scripts and pass values as JVM args
   or explicit `Set-Item env:\` calls.
6. **Windows file locks on JAR during repackage** — always stop running Java
   instances before `mvn package`. build-all.ps1 does this automatically.
7. **Spring Boot 4 removed `AntPathRequestMatcher`** — use lambda
   `RequestMatcher` or `PathPatternRequestMatcher` instead.
8. **PowerShell `param()` must be the first statement** — never insert code
   before it (the _dotenv.ps1 dot-source line goes after param).
9. **PS1 scripts must be saved as UTF-8 with BOM** for PowerShell 5.1 to parse
   them as UTF-8.
10. **The SPA has no router** — navigation is Zustand state (`useUIStore.view`).
    After any full-page reload, `detectInitialView()` in useUIStore.ts maps
    `window.location.pathname` back to the correct view. Add new path mappings
    there.
11. **Leaderboard delta contract**: room-global monotonic seq; clients apply
    strictly in order, any gap triggers RESYNC_LEADERBOARD → full snapshot
    stamped with current seq WITHOUT touching pending deltas. Never bump seq
    during snapshot builds or other clients cascade-resync.
12. **Judge output goes to per-case FILES, not pipes** — reading pipes after
    `waitFor` deadlocks on verbose programs. Keep the redirectOutput approach.
13. **CompileArtifactCache.put COPIES** — the caller still executes the binary
    from its run directory after caching.
14. **RankedSkipList.remove needs the real joinSeq** — MIN_VALUE breaks ties
    traversal and silently fails mid-group removals.
15. **GameRoom.nextQuestion semantics**: LOBBY start begins at index 0;
    REVIEW→next advances then starts. Don't unconditionally increment.

## Commit Rules (STRICT)

- One file per commit — each commit touches exactly one file
- Messages: `feat(scope): description` or `fix(scope): description`
- Scopes used: `backend`, `backend-test`, `frontend`, `frontend-renderer`, `db`,
  `config`, `build`, `deploy`, `docs`, `docs-theme`, `e2e`, `readme`, `license`
- Verify after committing: `git log <base>..HEAD --name-only` — every commit
  must list exactly one path (a staged-debris accident happened once; use
  pathspec-limited `git commit -m msg -- <file>` to stay airtight)
- DESCRIPTION.md IS committed (project documentation); this file is NOT

## UI Design Rules (NON-NEGOTIABLE)

- Color: examination red `#C8102E` on warm paper (`#F5F4F1`) — NO blue anywhere
- Flat surfaces only: no gradients, no glassmorphism, no glow, no blur
- Softly rounded: 8–14px radius (NOT square, NOT pill-shaped)
- Font: Noto Sans (UI) + Noto Sans Mono (code) — self-hosted WOFF2 only, no system-ui fallback
- Hairline borders (`--oq-border`), dotted separators for tables
- Small-caps tracked labels (`.label-caps`)
- Framer Motion + CSS for transitions/animations (replaced GSAP)
- Dark mode via CSS variables + `prefers-color-scheme` + manual toggle

## Testing

- Backend: `mvn test` → 298 tests (unit + concurrency + security + stress),
  includes regressions for every bug in the sweep table above
- Frontend type-check: `npx tsc --noEmit` (run inside frontend/)
- Frontend build: `npm run build` (dual bundle: modern ESM + legacy SystemJS)
- E2E: Playwright specs in `frontend/e2e/` (written, not run)
- NEVER run live servers or polling loops in the agent session — the user
  will run `.\scripts\run-prod.ps1` themselves

- AI grading: configurable via .env (sprintjudge.ai.enabled/provider/endpoint/model/api-key)
  - Supports OpenAI-compatible APIs (cloud or local llama.cpp)
  - Best-effort feedback on failed test cases, never blocks submission flow

## Known Limitations / TODO

- OAuth2 is unwired; re-enable per instructions above
- CSRF disabled (MVP) — required before any public form POST
- StrictMode dev double-mount can double-JOIN as host in `npm run dev`
- Selection scores ignore `pointsBase` by design: speed-bonus scale 0–1000
- No Postgres support yet (architecture ready for it via repository layer)
- E2E specs exist but browsers may need `npx playwright install`
- Departed players vanish from live leaderboards AND from GAME_END rankings
- Team mode: Yjs CRDT shared editor not yet wired (team management works)
- Battle mode: bracket advancement not yet implemented (matchmaking works)
- Post-game review suite: podium, answer key (with correct rates), per-student error review (names hidden initially), class difficulty analysis
- Admin Games + Settings tabs: stubs only ("Coming soon")

## File Locations Cheat Sheet

| What | Where |
|------|-------|
| Full project description | `DESCRIPTION.md` |
| .env template | `.env.example` |
| Schema DDL | `src/main/resources/db/migration/V1__init.sql` |
| Seed library source | `tools/bank-gen/gen.mjs` |
| Generated seed sets | `seed/sets/{java,python,c,c}/*.json` |
| Master seed bundle | `seed/master-bundle.json` → copied to resources |
| Deploy configs | `deploy/nginx-sprintjudge.conf`, `deploy/sprintjudge.service` |
| Env checker | `scripts/check-env.ps1` |
| Prod verifier | `scripts/verify-prod.ps1` |
| Lazy Monaco service | `frontend/src/services/CodeEditor.ts` |
| WS client (queued sends) | `frontend/src/services/WebSocketService.ts` |
| Delta protocol (client) | `frontend/src/services/GameStateManager.ts` |
