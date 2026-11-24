# Platform tracks review (read-only)

## High
- `frontend/src/services/AnalyticsApiService.ts:76-83,105-109` — `buildAdaptiveSet` POSTs via raw `fetch` with **no `X-XSRF-TOKEN`**. `SecurityConfig.java:73-89` CSRF-protects `/api/admin/**` (only `/api/public/**`, `/ws`, login, backchannel ignored). "Build playable quiz" will 403. Axios clients set the header (`AssignmentApiService.ts:70-71`, `AdminApiService.ts:48-49`); fetch clients do not.

## Medium
- `AssignmentController.java:93-96` / `AssignmentService.java:229-234` — `/api/public/assignments/{id}/progress?studentId=…` returns any student's status/score with no roster proof or capability token (IDOR/privacy). `start` (`:88-91`) similarly lets any caller start work as a roster student.
- `AssignmentController.java:102-105` / `AssignmentService.java:260-283` — public LTI launch returns `valid:true` for arbitrary unauthenticated claims; no JWT signature/OIDC state check. Placeholder `signedLaunch` limits impact, but any consumer trusting `valid` is an auth bypass.
- `frontend/src/services/ProfileApiService.ts:57-78` — sole profile credential stored in `localStorage` (XSS-readable). `ProfileReport.java:101-132` echoes `linkToken` on every report; GET already requires the token, so no cross-user leak observed.

## Low
- `ProfileService.java:35-44` — failed link after `create` leaves an orphan profile row; public POST is unmetered.
- `AssignmentService.java:156-161` — `MAX_CSV_BYTES` compares chars, not bytes; roster fields unvalidated (admin-only, React-escaped).
- `ExtraTypeConfigForm.tsx:170` — hot-spot preview allows arbitrary `http(s)` images without `isSafeMediaUrl` (`RichStemFields.tsx:70`); CSP `img-src 'self' data:` blocks loading.
- `i18n/index.ts:64-70` — naive `{var}` substitution; static dictionaries only, no HTML sink.

## Game flows
V4/V5 are add-only (no existing table altered); `/admin/**` gated, `/profile` permitted. No regression found in join/play/host paths. SQL is parameterized throughout; `MessageDigest.isEqual` token check is constant-time.
