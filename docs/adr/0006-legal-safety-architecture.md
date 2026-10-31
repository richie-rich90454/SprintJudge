# ADR 0006 — Legal Safety Architecture

Status: accepted
Date: 2026-09-18

## Context

An open-source bank must be safe by construction: no copied questions,
no reproduced framework text, no model training on protected material.

## Decision

Isolate generation: human-authored blueprints are the only input to the
generation scripts, which may read only `blueprints/**` and write only
`drafts/**`. Every question is authored from first principles, reviewed
by a human, and attested. Stimuli are original, public domain, or
compatibly licensed with the license recorded.

## Consequences

- Firewall script plus CI checks enforce isolation.
- Human review is mandatory before any draft enters the bank.
- Legal notice ships in README and NOTICE.
