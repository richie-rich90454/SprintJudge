# Contributing

SprintJudge is open source. Contributions are welcome.

## Architecture

Backend is `com.sprintjudge` with Spring Boot, JOOQ (no raw SQL), and vanilla Jakarta WebSocket.
Packages: config, domain (enums/models/dto), repository (JOOQ DAOs), service, websocket, controller, exception.

Frontend is React 19 + TypeScript with OOP services (singletons) and Zustand stores.
Question rendering is an abstract base plus 12 concrete renderers.

## Adding a question type

1. Add the enum value in `QuestionType`.
2. Implement correctness in `EvaluationService`.
3. Add a renderer class and register it in `QuestionRendererFactory`.
4. Add a config form branch in the admin wizard.

## Commit style

One file per commit. Use `feat(scope): description` or `fix(scope): description`.
No `any` in TypeScript. No raw SQL. Flat UI only.

Verify a commit series with:

```bash
git log <base>..HEAD --name-only  # every commit lists exactly one path
```

## Verify before pushing

```bash
mvn -o test                    # full backend suite (2400+ tests)
mvn -o verify                  # suite + JaCoCo gate (100% lines + branches)
cd frontend && npx tsc --noEmit && npm run build
cd frontend && npm run test:unit   # vitest, 700+ tests at 100% coverage
cd docs && npm run docs:build
```

Mermaid diagram rules (rendered client-side, must never overflow):

- Node labels stay under ~20 characters; use `<br/>` for a second line.
- Edge labels are single short verbs, or no label at all.
- Prefer TB/LR with at most ~6 nodes per rank.
- Sequence participants get short names; notes stay two lines max.

## UI lockdown (non-negotiable)

- Light mode only — no dark selectors, no theme toggle.
- `border-radius: 0` and no `box-shadow`/`text-shadow`/`backdrop-filter` anywhere
  (enforced by a global kill rule in `frontend/src/index.css`).
- Font is Noto Sans for everything, self-hosted from `frontend/public/fonts/`.
- Colors come only from the `myColor` tuple in `frontend/src/design/kahoot.ts`
  (warm orange scale) — no raw hexes, no default palette names.
