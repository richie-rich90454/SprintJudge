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
- Stimulus necessary and licensed; license recorded in provenance.
- Reading load fits the subject; free of bias and idioms.
- Reviewer differs from author. Originality and license are 5/5 gates.

## Pipeline

Blueprint (human, attested) to draft (machine, firewalled) to linter
(schema, bias, demand, license, protected-content scan) to human
review (attested) to calibration pool (p-value, discrimination,
timing) to versioned bank record. Old versions stay; past analytics
stay valid.

## Coverage

Every unit ships every applicable format, both scopes where frameworks
changed, balanced demand, and calibrated difficulty. Reports per
subject and per unit; CI fails on gaps.
