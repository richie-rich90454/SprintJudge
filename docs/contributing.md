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

## UI lockdown (non-negotiable)

- Light mode only — no dark selectors, no theme toggle.
- `border-radius: 0` and no `box-shadow`/`text-shadow`/`backdrop-filter` anywhere
  (enforced by a global kill rule in `frontend/src/index.css`).
- Font is Noto Sans for everything, self-hosted from `frontend/public/fonts/`.
- Colors come only from the `myColor` tuple in `frontend/src/design/kahoot.ts`
  (warm orange scale) — no raw hexes, no default palette names.
