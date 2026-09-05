# API Reference

All REST endpoints under `/api/admin/**` require an authenticated admin session
(form login at `/admin/login`). Public
endpoints are under `/api/public/**`.

## Request lifecycle

```mermaid
flowchart LR
    R["HTTP request"] --> A{"Route guard"}
    A -->|"/api/admin/**"| OA["Admin session cookie required<br/>else 302 to /admin/login"]
    A -->|"/api/public/**"| V
    OA --> V["Bean Validation<br/>@NotBlank · @Size · @Min"]
    V -->|"400 + field details"| X["Problem returned"]
    V -->|"valid"| H["Controller → service → JOOQ"]
    H --> S["200 with JSON"]
```

## Public

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/public/quizzes` | List all quizzes |
| POST | `/api/public/run` | Live code console: compile + run with stdin, returns combined output (30 req/min per IP) |

## Admin

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/quizzes` | List quizzes |
| POST | `/api/admin/quizzes` | Create quiz (validated) |
| DELETE | `/api/admin/quizzes/{id}` | Delete quiz |
| GET | `/api/admin/quizzes/{id}/questions` | List questions |
| POST | `/api/admin/quizzes/{id}/questions` | Add question (validated) |
| PUT | `/api/admin/questions/{id}` | Update question (validated) |
| DELETE | `/api/admin/questions/{id}` | Delete question |
| GET | `/api/admin/settings` | Admin settings map |
| PUT | `/api/admin/settings` | Update settings |
| POST | `/api/admin/games` | Create a game for a quiz; host is resolved from the login session |
| GET | `/api/admin/export` | Export entire bank as JSON |
| POST | `/api/admin/import` | Import bank (`{ json, replace }`) |
| GET | `/api/admin/metrics` | Runtime metrics: memory/GC/threads, judge latency percentiles, compile-cache ratio, write-buffer depth |

Question payloads embed answer keys in their `config`, so they are admin-only by design —
the public surface never exposes them.

## Export JSON schema

```json
{
  "version": "1.0",
  "exportedAt": 1234567890,
  "quizzes": [
    {
      "id": "qz1",
      "title": "Algorithms 101",
      "description": "...",
      "questions": [
        {
          "id": "q1",
          "type": "MCQ",
          "title": "...",
          "description": "...",
          "timeLimitSec": 30,
          "pointsBase": 100,
          "config": { "options": ["A","B","C","D"], "correctIndex": 0 },
          "languagesAllowed": null
        }
      ]
    }
  ],
  "adminSettings": { "default_time_limit": "60", "mcq_max_attempts": "1" }
}
```

## Validation

Request bodies are validated with Jakarta Bean Validation (`@NotBlank`, `@Size`, `@Min`,
`@Valid`). Invalid input returns `400 Bad Request` with the failing fields; malformed JSON
also returns `400`.

## CSRF and headers

Admin auth is a same-origin session cookie. CSRF protection is **relaxed for the
MVP**: `/api/**`, `/admin/login` and `/admin/logout` are exempt from the CSRF
filter — re-enable it before exposing any public form POST. Responses carry HSTS,
`X-Content-Type-Options: nosniff`,
a locked-down Content-Security-Policy, `frame-ancestors 'none'` and a same-origin referrer
policy. CORS defaults to the Vite dev origin; configure `sprintjudge.cors.allowed-origins`
in production.
