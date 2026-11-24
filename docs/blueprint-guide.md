# Blueprint Guide

How to author a human blueprint that a generation script may consume.

## Rules

- Write from first principles in your own words. Never quote, cite,
  or point to any protected question, test, or framework passage.
- Describe the concept (for example "declaring and initializing int
  variables"), the misconception to target, bloom level, DOK, format,
  and optional stimulus type.
- Set `attestedFromFirstPrinciples: true` only when the above holds.
  The generator refuses any blueprint without it.
- One file per blueprint under `blueprints/<prefix>/`, as YAML or JSON.

## Required fields

`id` (`bp-<prefix>-<unit>.<part>-<4 digits>`), `subject`, `unit`
(`Unit <n>`, matching the id), `topic`, `concept`, `bloom`, `dok` (1-4),
`format`, `author`, `authoredAt` (ISO date), and
`attestedFromFirstPrinciples: true`.

Optional: `scope` (default `current-scope`), `misconceptionToTarget`,
`stimulusType`, `stimulusLicense`, `learningObjective`, `practice`, `tags`.
Unknown keys are rejected so typos do not silently disappear.

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

`tools/gen/generate.js` is the validated generator (offline, no network).
It parses the blueprint, checks the attestation and required fields, and
writes a schema-shaped draft shell to `seed/drafts/` with empty content
fields and a `todo` list. It never invents question text.

```sh
node tools/gen/generate.js blueprints/jp/bp-jp-1.1-0001.yaml seed/drafts/jp-1.1-0001.draft.json
node tools/gen/generate.js --all          # every attested blueprint under blueprints/
node tools/gen/generate.js --self-check   # assert-based validation smoke test
```

The firewall (`tools/gen/firewall.js`) still allows reads only from
`blueprints/**` and writes only to `seed/drafts/**`; `make check-ai-firewall`
verifies the allowlist. All inputs in a batch are validated before any
draft is written, so one bad attestation cannot leave a partial batch.

A human completes the shell (stem, options, answer, explanation, hint,
reviewer), the bank linter checks schema and legal rules, a different
human reviews and attests, and only then does the record enter
`src/main/resources/bank/`. The wizard's item-quality panel reports the
same advisory rules while authoring.
