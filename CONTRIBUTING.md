# Contributing to SprintJudge

Thanks for helping improve SprintJudge. This document covers the GitHub workflow. For
architecture, question authoring rules, and commit conventions, see
[docs/contributing.md](docs/contributing.md).

## Quick path for a first contribution

1. Fork the repository and create a branch from `main`.
2. Pick an issue labelled `good first issue` (or open one describing your change).
3. Make a small, focused change with a test.
4. Run the checks below, open a pull request, and fill in the PR template.

## Development setup

Prerequisites: JDK 25, Node.js 20+, GNU Make. The Maven wrapper is included.

```bash
make check-env
make dev-backend     # API + WebSocket on :8080
make dev-frontend    # UI on :5173 (proxies /api and /ws)
make dev             # both, side by side
```

## Checks that must pass before a PR

```bash
make lint                                  # Spotless, Checkstyle, PMD, compile with -Werror
mvn test                                   # backend suite
make test-frontend                         # tsc + frontend unit tests
make check-ui                              # UI design rules
node scripts/check-bank.mjs                # 168k-question bank contract
node scripts/check-volume.mjs              # per-subject volume floors
cd frontend && npx playwright test         # E2E (needs the dev servers running)
```

`mvn verify` adds the 100% JaCoCo coverage gate. CI runs all of the above on every PR.

## Code conventions

- **Java:** no lambdas, method references, `var`, records, text blocks, switch arrows,
  pattern `instanceof`, `sealed`, or `enum`. Annotate nullability with JDT
  `@NonNull` / `@Nullable`. Every new file must compile with zero warnings.
- **TypeScript:** no `any`. Prefer existing primitives in `frontend/src/ui/` before adding
  new ones. DaisyUI components are the preferred building blocks.
- **UI:** follow the tokens in `frontend/src/index.css`. No uppercase labels, middle dots,
  em dashes in copy, emoji, or global `!important` resets. `make check-ui` enforces this.
- **Commits:** one logical change per commit, `type(scope): description`.

## Question bank contributions

Question content lives in `src/main/resources/bank/` and is governed by strict rules:
original first-principles authoring, a named human reviewer, recorded stimulus licences,
and no branded or copyrighted material. Read
[docs/legal-safety.md](docs/legal-safety.md) and [docs/contributing.md](docs/contributing.md)
before adding content. Generation scripts may read only `blueprints/**`.

## Reporting bugs and proposing features

Use the issue templates. Include reproduction steps, expected vs. actual behaviour, and
the versions of Java, Node, and the browser. Security issues must not be filed publicly —
see [SECURITY.md](SECURITY.md).

## Code of conduct

Participation is governed by the [Code of Conduct](CODE_OF_CONDUCT.md).
