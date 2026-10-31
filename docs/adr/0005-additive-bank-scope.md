# ADR 0005 — Additive Bank Scope

Status: accepted
Date: 2026-09-18

## Context

Frameworks revise. Teachers still expect historical topics to be present.

## Decision

The bank is a superset, always. Removed topics stay tagged `legacy-scope`.
Added topics enter as `current-scope`. Shared topics are `core-scope`.
Rooms default to `current-scope`; practice defaults to everything.

## Consequences

- No question or topic is ever deleted.
- CI additivity check fails on any removal.
- Scope coverage check requires both scopes where frameworks changed.
