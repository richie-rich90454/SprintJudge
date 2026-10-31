# Blueprint Guide

How to author a human blueprint that a generation script may consume.

## Rules

- Write from first principles in your own words. Never quote, cite,
  or point to any protected question, test, or framework passage.
- Describe the concept (for example "declaring and initializing int
  variables"), the misconception to target, bloom level, DOK, format,
  and optional stimulus type.
- Set `attestedFromFirstPrinciples: true` only when the above holds.
- One file per blueprint under `blueprints/<prefix>/`.

## Minimal example

```yaml
id: bp-jp-1.1-0001
subject: JAVA_PROGRAMMING
unit: Unit 1
topic: Primitive Types
concept: declare and initialize int variables before use
bloom: APPLY
dok: 1
format: CONCEPT_MCQ
misconceptionToTarget: uninitialized variable defaults to zero
author: j.doe
authoredAt: 2026-09-18T00:00:00Z
attestedFromFirstPrinciples: true
```

## What happens next

`tools/gen/generate.ts` drafts from the blueprint, the linter checks
schema and license, a different human reviews and attests, and only
then does the record enter `src/main/resources/bank/`.
