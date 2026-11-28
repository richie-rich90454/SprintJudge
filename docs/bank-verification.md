# Bank Verification Ledger

Hand-verification of every bank source file, run in parallel by subject
groups. Each JSONL line records one file: `file`, `subject`, `questions`,
`verdict` (PASS/DEFECT), `defects`, `verifier`, `verifiedAt`. The final
verification ledgers live in `verification/final-verification/` (final group
sweeps, hygiene and platform reviews, and the integration gate summary),
`verification/merged-bank-verification/` (wave B, six ledgers) and
`verification/new-course-audit/` (eight new-course groups); the wave A ledgers
below remain in `verification/bank-verification/`, and the rubric audits in
`verification/ap-style-audit/`.

| Ledger | Files | DEFECT files | Defect entries |
|---|---:|---:|---:|
| `verification/bank-verification/math.jsonl` | 498 | 67 | 167 |
| `verification/bank-verification/science.jsonl` | 502 | 13 | 23 |
| `verification/bank-verification/social.jsonl` | 495 | 24 | 52 |
| `verification/bank-verification/computing.jsonl` | 467 | 100 | 142 |
| `verification/bank-verification/humanities.jsonl` | 547 | 61 | 513 |
| `verification/bank-verification/remaining.jsonl` | 482 | 51 | 89 |

Every DEFECT entry carries a file, question id, verbatim quote and exact fix.
The defects were applied in place with version bumps and refreshed review
timestamps; no records were deleted. `node scripts/check-bank.mjs` is the
machine re-check that the fixes landed.

## Exam-style quality audits

Six parallel audits scored every question against the exam-style rubric in
`docs/question-bank.md`. Their reports live in `verification/ap-style-audit/`
(math, science, social, computing, humanities, remaining subjects). Each report
records heuristic counts for terse stems, missing scenarios, short explanations,
generic distractors, and the fifteen worst items with verbatim quotes and fixes.

The audit findings drove six parallel enrichment passes that added genuine
stimuli and scenarios, expanded explanations, named misconceptions, expanded
sub-four-option items to four, and repaired broken keys and corrupted text.
A final bank-wide sweep cleared every duplicate-stem, near-duplicate-opening,
option-length, and misconception advisory. The `check-bank` gate now reports
zero warnings and zero errors across all 329,652 questions in the 82-course
catalog. Question content was generator-authored with audited fixes applied,
not bespoke hand-authoring. The full gate matrix is summarized below.

## What was verified

- Stem asks a determinate question; keyed answer is correct; distractors are
  plausible and distinct.
- Numeric answers were recomputed by an independent route; code-tracing keys
  were executed where the language was available.
- Explanations agree with keys, teach the why, and are complete sentences.
- No author scratch text, no verdict contradictions, no branded tokens.
- Format matches the answer shape the runtime builder consumes.

## Gate matrix

`node scripts/check-regression.mjs` runs eleven gates by default: bank schema
and content, volume floors, unit floors, seed sets, legal, UI, frontend
typecheck and tests, frontend build, backend verify, E2E, and the production
probe. `--fast` runs only the seven static gates. Recorded outcomes live in
`verification/final-verification/integration.md`: regression clean, legal and
UI clean, backend verify green; the local E2E run failed on an environment
setup mismatch (test password and public-run flag), which CI configures
explicitly.

## Known limits

Deep-reading every one of ~330,000 questions was not possible; flagged items
and a representative sample of unflagged items were read in full, and every
file was scanned by deterministic rules. Residual factual errors may remain
in unread questions. Wave B completed the sweep over the merged bank; its
ledgers are in `verification/merged-bank-verification/`.
