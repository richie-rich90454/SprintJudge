# ADR 0003 — Scoring Model

Status: accepted
Date: 2026-09-18

## Context

Scores must reward accuracy first and treat response time as signal,
not verdict, while staying explainable to students and teachers.

## Decision

Adopt the SprintJudge Score (SJS): accuracy 50, response time 20,
consistency 10, improvement 15, mastery 5. Difficulty weights combine
DOK, calibrated p-value, and format multipliers. Response-time credit
is linear between quartiles. Speed-accuracy signal uses plain language.

## Consequences

- Pure functions in `com.sprintjudge.scoring`, fully tested.
- Leaderboard shows rank, delta, top three, distribution, and tier.
- Bottom half never named publicly.
