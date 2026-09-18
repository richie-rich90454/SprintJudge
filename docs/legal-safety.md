# Legal Safety

How this project stays safe by construction: original questions only,
no brand misuse, no model training on protected material.

## Copyright rules

1. Every question is authored from first principles, from independent
   knowledge of the subject. Never copy, paraphrase, or adapt any
   published exam question, practice test, or framework text.
2. Never redistribute released questions, practice tests, or course
   materials. Past release does not remove copyright.
3. Scope (a list of topics) guides coverage; expression (text,
   examples, diagrams) is never copied.
4. Stimuli (passages, images, diagrams, datasets) must be the author's
   own creation, public domain, or compatibly licensed (CC0, CC BY,
   CC BY-SA, MIT, Apache 2.0) with attribution in `provenance`.
   A documented human-reviewed fair-use rationale is the rare
   exception, never the default. Unknown license means do not use.
5. Nothing protected ships in the bank, seed, docs, or repo.

## Generation rules

1. No model is trained on any external testing program content.
2. No tool rewrites or adapts a protected question.
3. No output may be substantially similar to a protected question.
4. No protected document is ever fed into any generation pipeline.
5. Generation reads only human-authored blueprints in `blueprints/**`
   and writes only to `drafts/**` (gitignored, never committed).
6. Generation never accesses the network except the configured model
   endpoint, and never browses or retrieves protected content.
7. Every machine-assisted draft needs a named human reviewer who
   attests it is original before it enters the bank.

## Contributor rules

- Do not submit branded names, copied questions, or protected text.
- Record stimulus licenses in `provenance.stimulusLicense`.
- Mark blueprints `attestedFromFirstPrinciples: true`.
- When in doubt, leave it out and ask first.

## Reporting

If you believe this project contains content that infringes your
rights, open an issue with the file path and we will remove it
promptly. Legal contact is tracked in the NOTICE file.

## Enforcement

- `make check-trademarks` — branded names grep.
- `make check-content` — protected-content pattern scan of the bank.
- `make check-stimulus-license` — every stimulus has a valid license.
- `make check-ai-firewall` — generation scripts stay inside `blueprints/**`.
- `make check-blueprints` — every blueprint carries first-principles attestation.
- `make check-ai-attestation` — every assisted question has a reviewer attestation.
- `make check-additivity` — nothing is ever removed from the bank.
