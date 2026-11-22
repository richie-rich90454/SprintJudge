# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Repository hardening toolchain: Spotless, Checkstyle, PMD, OpenRewrite, ECJ and
  NullAway profiles, `-Xlint:all -Werror`, and `make lint` / `make fix`.
- Java 8-syntax and null-safety contract for all backend sources (see `PLAN.md` §8a).
- CSA classroom presets and the bank-to-quiz bridge.

### Changed

- Question bank files renamed deterministically to `<subject>-<unit-topic>.json`.
- Runtime state relocated to `database/`, `database/backups/`, and `seed/drafts/`.

### Fixed

- JDT problem 1102 diagnostics from suppressed null checks.
- Behaviour regressions found during the Java conversion: atomic streak bump, unknown
  game-mode fallback, PRACTICE timer guard, and value semantics on converted POJOs.

## [1.0.0] - 2026-10-25

### Added

- Live rooms, host-driven rounds, per-answer scoring, leaderboard, and force-submit.
- Built-in online judge for C, C++, Java, JavaScript, and Python.
- Team and Battle modes, read-only projector, CSV export, admin editors.
- VitePress documentation site and a GitHub Actions CI workflow.

[Unreleased]: https://github.com/richie-rich90454/SprintJudge/compare/main...HEAD
[1.0.0]: https://github.com/richie-rich90454/SprintJudge/releases/tag/v1.0.0
