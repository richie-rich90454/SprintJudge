# ADR 0004 — No Trademarked Subject Names

Status: accepted
Date: 2026-09-18

## Context

Subject names must stand on their own without using any test-owner marks.

## Decision

Every subject uses its own self-standing name (Java Programming,
Computing Foundations, Chemistry, Calculus I, and so on). No user-facing
surface, question metadata, doc, or commit message uses a trademarked
program name except the two internal policy docs where it is disclaimed.

## Consequences

- CI grep enforces the rule on every PR.
- Contributor guide repeats the rule with PR checkboxes.
- Internal rename map documents the mapping for contributors only.
