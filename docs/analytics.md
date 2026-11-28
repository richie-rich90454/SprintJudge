# Learning analytics and adaptive practice

Admin-only REST endpoints under `/api/admin/analytics/**`. Authentication is the
same login session used by the other admin endpoints; no security wiring lives
in the controller.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/admin/analytics/students?sessionId=...` | One row per player: sessions, attempts, accuracy, average score, tries per item. |
| GET | `/api/admin/analytics/classes?sessionId=...` | Class rollup plus a row per game session, with hardest and easiest question ids. |
| GET | `/api/admin/analytics/items?subject=<code>` | One row per attempted bank item in a subject. |
| POST | `/api/admin/analytics/adaptive-set` | Builds a playable quiz from selected bank questions. |

`sessionId` is optional on the first two endpoints; omit it to aggregate every
session. A missing subject on the item report is a 400.

## Metrics

- **Accuracy / p-value** — correct submissions divided by all stored
  submissions. Retries count as additional attempts.
- **Discrimination (observed)** — early-half minus late-half correct rate by
  submission time, clamped to `[-1, 1]`; 0 for fewer than two attempts. This is
  the same proxy used by the calibration readout, not a point-biserial score.
- **Bank difficulty / discrimination / timing** — authored values from the
  question bank. Bank headers supply difficulty; full records supply
  discrimination and the response-time baseline (`p25Sec`, `medianSec`,
  `p75Sec`). Items are matched to bank records by question id first, then by
  unit and topic from the stored question config.
- **Class hardest / easiest** — question ids with the lowest and highest
  observed accuracy among questions that have attempts.

## Set builder

Request body:

```json
{
  "subject": "ALGEBRA_I",
  "unit": "",
  "size": 12,
  "mode": "balanced",
  "targetDifficulty": 0.5,
  "title": "",
  "seed": 0
}
```

`size` is clamped to 1–30. In `balanced` mode the subject's bank is split into
four difficulty bands (`0.00-0.25` through `0.75-1.00`) and questions are taken
evenly from each band by highest discrimination. In `adaptive` mode questions
are ranked by `discrimination - 2 * |difficulty - targetDifficulty|`. A seeded
tie-break keeps repeat runs stable. Selected records are converted with the
existing bank quiz builder's question converter and imported as an ordinary
quiz; the response returns the new quiz id, band counts, and selection
averages. Unplayable records are skipped and reported as `skipped`.

## Frontend

`frontend/src/screens/AnalyticsView.tsx` renders class, student, and item
reports with tables and CSS bars (the project has no chart library), plus the
set-builder form. `frontend/src/services/AnalyticsApiService.ts` holds the
typed fetch calls. The screen is a standalone component; adding it to the
router is a separate wiring step.
