# Question Bank

Structured, calibrated, additive question storage with human review.

## Record shape

See `contracts/question-schema.json` for the normative schema. Each
record carries id, subject, unit, topic, learning objective, practice,
scope (`current-scope`, `legacy-scope`, `core-scope`), cognitive
demand (bloom plus DOK), format, optional stimulus, stem, options,
answer, explanation, hint, calibrated difficulty and discrimination,
response-time baseline, provenance, tags, relations, and version.

## Scope tags

- `current-scope` — in the latest framework.
- `legacy-scope` — removed from the latest framework, preserved.
- `core-scope` — in both. Rooms default to current; practice uses all.

## Item-writing rules

- Stem holds the full problem. One correct answer (or defined set).
- Options parallel in grammar and length. No all/none-of-the-above.
- Every distractor maps to a named misconception.
- A code item must actually carry code in the stem, not describe it in prose.
- An explanation teaches the why, including why the trap is tempting.
- Stimulus necessary and licensed; license recorded in provenance.
- Reading load fits the subject; free of bias and idioms.
- Reviewer differs from author. Originality and license are 5/5 gates.

## Exam-style craft

Every question must work as a rigorous course-framework-style exam item:

- The stem supplies a real problem, scenario, source excerpt, data display, or code — not bare-recall trivia or a decorative persona that adds no information.
- Multiple-choice items carry four parallel, plausible options; each distractor names the specific wrong belief that makes it tempting.
- The explanation teaches the governing principle, why the key holds, and why each alternative fails.
- Cognitive demand matches the task: reasoning and analysis for data-bearing or source-based items, recall only for genuine definitions.
- Stimuli are original and licensed in provenance; no protected framework text is copied.
- Reading load fits the subject, and language courses keep target-language content where the subject expects it.

Six parallel audits scored the full bank against this rubric and drove the enrichment passes recorded in `verification/ap-style-audit/`. The final verification ledgers live in `verification/final-verification/`, `verification/merged-bank-verification/`, and `verification/new-course-audit/`.

## Verification convention

Every numeric answer, code trace, and pseudocode trace is **executed,
not reasoned about**. Scratch space lives **inside this working
directory** at `executor/tmp/verify-<team>/`, which is already
gitignored, and is deleted when the batch is finished. Do not use
`%TEMP%`, `/tmp`, or any path outside the repository: builds and CI
routinely run with a locked-down or read-only external temp directory,
and a verification step that depends on one blocks the whole pipeline.

| Batch type | How to verify |
|---|---|
| Java Programming | write the snippet to `executor/tmp/verify-<team>/`, run `javac` + `java`, compare stdout to the keyed option |
| Computing Foundations | run the pseudocode with `node` from the same directory and compare the printed value |
| Math, science, economics | recompute with a second independent method, and back-substitute where possible |

If a claim cannot be executed (for example a conservation argument),
recompute it by a different route and show both routes agree.

## Machine-enforced quality floors

`make check-bank` rejects a record when any of these fail, so a weak
item cannot merge even if a human reviewer misses it:

| Floor | Why |
|---|---|
| explanation at least 60 characters | must teach the why, not restate the answer |
| code formats carry code in the stem | a tracing item that never shows the code is not a tracing item |
| every distractor misconception at least 12 characters | "wrong" is not a misconception; name the belief |
| each option length within 0.3x-3.5x of the median | a short giveaway or a sentence among numerals breaks the item |
| stems at least 20 characters, no exact or 12-word-opening repeats | no padding, no number-swapped twins |
| exactly one correct option unless multi-select | no ambiguity |
| format matches the answer shape the runtime builder consumes | a record counted in volume but skipped at import is a defect |
| no exact duplicate option text | a duplicated distractor cannot be answered |
| no author scratch (`Wait compute`, `Let me keep`, anchored `Use `<n>`.`) | self-corrections must not ship |
| explanation ends with terminal punctuation and is not a fragment | teaching text must be complete |
| keyed verdict agrees with its explanation | no "claim is wrong" + "matches" contradictions |
| reviewer differs from author, stimulus licensed, attestation present | legal and provenance gates |

## Pipeline

Blueprint (human, attested) to draft (machine, firewalled) to linter
(schema, bias, demand, license, protected-content scan) to human
review (attested) to calibration pool (p-value, discrimination,
timing) to versioned bank record. Old versions stay; past analytics
stay valid.

## Coverage

The shipping catalog holds 82 course-framework subjects: 42 original, 4 fill,
and 36 new. Each stands at or above the 4,000-question floor (329,652 total).
Six platform feature tracks are implemented: analytics, assignments/LMS,
player profiles, localization, accessibility, and authoring/rich-stem tooling.
All bank content gates are green; the full gate matrix is recorded in
`verification/final-verification/integration.md`. Question content was
generator-authored and corrected through the audited fix passes, not bespoke
hand-authoring.

Every unit ships every applicable format, both scopes where frameworks
changed, balanced demand, and calibrated difficulty. Reports per
subject and per unit; CI fails on gaps.
