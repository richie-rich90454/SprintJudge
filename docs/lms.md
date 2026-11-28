# Assignments and LMS integration

Async assignments let an administrator schedule a quiz for a roster of students,
track per-student progress, and pass grades back to a learning platform. This is
a functional vertical slice: persistence, REST endpoints, and an admin screen.
The LTI pieces are deliberately stubs (see the warnings below).

## Data model

SQLite tables (see `src/main/resources/db/migration/V4__assignments.sql`):

- `assignments` — quiz id, title, optional `release_at` / `due_at` (epoch seconds),
  creator, optional `lti_deployment_id`.
- `assignment_roster` — `student_id`, `name`, `email`, unique per assignment.
- `assignment_progress` — status (`NOT_STARTED`, `IN_PROGRESS`, `SUBMITTED`,
  `GRADED`), start/submit/grade timestamps, score and max score, unique per
  assignment and student.

An assignment is "released" when `release_at` is null or in the past. Starting
after `due_at` is refused.

## Endpoints

Admin routes inherit the `/api/admin/**` ADMIN security rule (session cookie +
XSRF). Public routes are under `/api/public/**`.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/admin/assignments` | Create from a quiz. Body: `quizId`, `title`, optional `description`, `releaseAt`, `dueAt`, `ltiDeploymentId`. |
| GET | `/api/admin/assignments` | List assignments. |
| GET | `/api/admin/assignments/{id}` | One assignment. |
| GET | `/api/admin/assignments/{id}/roster` | Roster students. |
| POST | `/api/admin/assignments/{id}/roster` | CSV import. Body: `{ "csv": "..." }`. Returns `{ "imported": n }`. |
| GET | `/api/admin/assignments/{id}/progress` | All student progress rows. |
| POST | `/api/admin/assignments/{id}/grades` | Record a grade. Body: `studentId`, `score`, optional `maxScore` (defaults to the quiz's total points). Returns a grade result with passback payload. |
| GET | `/api/public/assignments/{id}` | Assignment detail. |
| POST | `/api/public/assignments/{id}/start` | Start/resume. Body: `studentId`. Checked against release window and roster. |
| GET | `/api/public/assignments/{id}/progress?studentId=...` | One student's progress (synthetic `NOT_STARTED` row if absent). |
| POST | `/api/public/assignments/lti/launch` | LTI 1.3 launch validation stub. |

## CSV roster format

First line is a header; `student_id` is required, `name` and `email` are
optional (`id` / `studentid` are accepted aliases). Quoted fields may contain
commas, and doubled quotes escape a quote.

```
student_id,name,email
s001,"Lovelace, Ada",ada@example.org
s002,Grace Hopper,
```

Rows are capped at 5,000 and the document at 1 MB. Existing student ids are
updated rather than duplicated.

## LTI 1.3 launch stub — NOT production ready

`POST /api/public/assignments/lti/launch` checks that the required claims
`iss`, `aud`, `sub`, and `deployment_id` are present and non-blank, accepts an
optional `context_id`, and returns a `signedLaunch` placeholder of the form
`PLACEHOLDER.<base64url claims>.UNSIGNED`.

**Warning:** this validates shape only. It does **not** verify the platform JWT
signature, issuer allow-list, audience, nonce, state, or expiry. The
`signedLaunch` value is not signed and must never be treated as an
authenticated credential. Before exposing this publicly, implement the OIDC
third-party login flow, verify the platform's JWKS-signed id token, bind a
server-side session, and enforce per-deployment configuration.

## Grade passback

Recording a grade stores the score on the student's progress row and returns a
generic AGS-shaped payload (`userId`, `activityId`, `scoreGiven`,
`scoreMaximum`, `gradingProgress`, `timestamp`) in `passbackPayload`. This is a
documented placeholder: no outbound call to a platform is made. A production
integration must store the platform's OAuth2 client credentials and line-item
URL, sign the request, and send the score with a retry/backoff policy.

## Frontend

`frontend/src/services/AssignmentApiService.ts` is the typed Axios client
(same-origin cookies and XSRF echo, matching the other API services).
`frontend/src/screens/AssignmentView.tsx` renders the list, create form, CSV
import, progress table, grade editor, and a student start check. It is a
self-contained exported component; wire it into the router with an admin guard
when integrating the track.

## Integration notes

- `V4__assignments.sql` is applied idempotently by `AssignmentService` at
  startup so this slice works without editing the shared migration bootstrap.
  It can also be added to `DatabaseConfig` later; running twice is harmless.
- The authenticated principal is resolved server-side for `createdBy`; the
  client cannot declare the assignment owner.
