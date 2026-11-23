# Bank Verification Ledger

Wave A hand-verification of every bank source file, run in parallel by subject
groups. Each JSONL line records one file: `file`, `subject`, `questions`,
`verdict` (PASS/DEFECT), `defects`, `verifier`, `verifiedAt`.

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

## What was verified

- Stem asks a determinate question; keyed answer is correct; distractors are
  plausible and distinct.
- Numeric answers were recomputed by an independent route; code-tracing keys
  were executed where the language was available.
- Explanations agree with keys, teach the why, and are complete sentences.
- No author scratch text, no verdict contradictions, no branded tokens.
- Format matches the answer shape the runtime builder consumes.

## Known limits

Deep-reading every one of ~169,000 questions was not possible; flagged items
and a representative sample of unflagged items were read in full, and every
file was scanned by deterministic rules. Residual factual errors may remain
in unread questions. Wave B repeats the sweep over the merged bank.
