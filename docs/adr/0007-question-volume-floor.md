# ADR 0007 — Question Volume Floor

Status: accepted
Date: 2026-09-18

## Context

A practice engine teachers can trust must never run out of questions.

## Decision

Every course ships with at least 3,000 questions, targeting 4,000+.
Macroeconomics and Microeconomics may share one combined 3,000+ pool.
Every unit ships with 50+ questions, full format coverage, full scope
coverage, balanced cognitive demand, and calibrated difficulty spread.

## Consequences

- CI volume, unit, format, and scope checks gate every bank PR.
- Coverage and volume reports ship per subject and per unit.
- A course below the floor is not considered shipped.
