# ADR 0002 — Question Bank Architecture

Status: accepted
Date: 2026-09-18

## Context

The bank must scale to thousands of questions per course with provenance,
scope tags, calibration data, and format coverage, without ever deleting.

## Decision

Store each question as a validated JSON record under
`src/main/resources/bank/<subject>/`. Blueprints live under `blueprints/`.
Drafts live under `drafts/` (gitignored). A linter enforces schema,
provenance, scope tags, and license fields before merge.

## Consequences

- Additive only: retirement hides, never deletes.
- Every question carries scope, cognitive demand, and provenance.
- Coverage and volume checks run in CI.
