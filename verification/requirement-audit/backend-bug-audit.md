# Backend Bug Audit (read-only)

Ranked by severity. C = confirmed in source; S = runtime-dependent.

1. [C] `GameRoomManager.java:934-981` — `onTimerExpired` checks the deadline, then dispatches to the sweeper; a `+time` in that gap still reveals early. Re-check `now < end` inside the sweeper under the room lock.
2. [C] `GameRoomManager.java:301-320` — status flips to ACTIVE under lock, but `currentQuestionId` and timer are set after unlock; a concurrent submit sees a stale round. Assign all fields before releasing.
3. [C] `SecurityConfig.java:81` + `BasisBackchannelController.java:51-99` — permitAll, CSRF-ignored `action=test` makes the server POST `resourceSecret` to the issuer; `TEST_WINDOW` never evicts. Require a signed token; bound the map.
4. [C] `LoginRateLimitFilter.java:40-41` — over 10k tracked IPs clears *all* buckets; 10k XFF values (trusted from a loopback proxy) reset the admin login throttle. Evict only stale entries.
5. [C] `NativeExecutor.java:252-262` — compiler stdout is piped but read only after `waitFor`; >64KB diagnostics block until timeout, so valid code reports `compilation_error`. Redirect to a capped file.
6. [C] `NsJailExecutor.java:17-18` / `NativeExecutor.java:30` — `mode=nsjail` with the dev profile yields no `CodeExecutor` bean; boot fails. Drop `@Profile("!dev")`.
7. [C] `PublicController.java:315-327` — anonymous `/api/public/run` runs arbitrary code when `public-run=true`; no session check. Gate behind admin auth or remove the flag.
8. [C] `AssignmentController.java:85-88` — permitAll GET returns the full Assignment (`quizId`, `createdBy`, `ltiDeploymentId`, windows) with no roster check. Return only public launch fields.
9. [C] `GlobalExceptionHandler.java:19-22,51-54` — echoes `IllegalArgumentException`/`IllegalStateException` messages; internal SQL, paths and IDs leak. Return fixed text and log details.
10. [C] `PublicController.java:103-114,367-381` — limiter ages buckets from first use, so any caller resets its budget every 120s. Track last-request time instead.
11. [C] `GameRoomManager.java:427-447` — status/current-question gate is read outside `synchronized(room)`; a racing skip or timer can score a stale question. Hold the lock through gating.
12. [C] `AssignmentService.java:222-226,303-319` — find-then-insert progress is non-atomic; concurrent starts create duplicate rows or a 500. Use an upsert.
13. [C] `GameWebSocket.java:208-212,284-292` — host/admin flags are snapshotted at handshake; session logout mid-game leaves a live socket with host powers. Revalidate per control message.
14. [C] `WebSocketSessionManager.java:30-39` — `containsKey` + `put` is non-atomic; concurrent handshakes exceed `MAX_SESSIONS`. Use `putIfAbsent`.
15. [S] `GameRoomManager.java:1033-1062` — `endGame` removes the registry entry outside the room lock; a join holding the old room can leave a ghost room. Remove under lock.

Skipped: no builds/tests were run and no files outside this report were touched. Risk: static reading cannot confirm thread-interleaving timing or deployed config values.
