# SprintJudge Repository Hardening Plan

Status: in execution.
Branch: `chore/repo-cleanup` (phases 0, 1a, 1b), then `refactor/java8-syntax` (phase 2).
Base: `main` at `4008918d`.

---

## 1. Goals

1. Remove every unneeded / non-regeneratable-ambiguous file; keep only what is provably
   regeneratable or real source.
2. Reorganize the repository so the root contains source and config only, and all
   transient runtime state lives in clearly named, gitignored directories.
3. Give every question-bank file a consistent `<subject>-<unit>-<topic>.json` name.
4. Convert the backend to student-readable, null-safe, warning-free Java that uses no
   post-Java-8 syntax, with no performance compromise.
5. Make developer experience excellent: one-command setup, fast feedback, clear errors,
   accurate docs.

## 2. Hard constraints (agreed with the owner)

- Java 8 syntax floor. Spring Boot 4 requires Java 17+, so this is a **syntax/style
  restriction on a 17+ compiler**, not a source-level downgrade.
- **Banned syntax / keywords:** lambdas, method references, `var`, records, text blocks,
  switch expressions and arrow rules, pattern-matching `instanceof`, `sealed`/`permits`,
  `enum`.
- **Allowed:** classic `switch` statements (including `switch` on `String`),
  `instanceof` with explicit cast, all post-Java-8 libraries and packages (`java.time`,
  `Optional`, `List.of`/`Map.of`/`Set.of`, `Collections`, streams internally where they
  do not require lambdas are still avoided because they cannot be written without
  functional interfaces).
- **Null safety everywhere:** every field, parameter, and return value annotated
  `@NonNull` or `@Nullable` (Eclipse JDT annotations).
- **Zero Java warnings:** `-Xlint:all -Werror`.
- **No performance compromise:** `scripts/load-test.mjs` is the before/after gate.
- **Frontend (TS/TSX) is out of scope** for the syntax conversion.
- Deletions are limited to files provably regeneratable from committed source or build.
- Toolchain auto-fixes run only through explicit `make fix`; every auto-fixed file is
  listed in the commit message for review (report-and-confirm).

## 3. Phase 0 — Toolchain (branch `chore/repo-cleanup`)

Add, verify, and wire into `mvn verify` and the Makefile:

| Tool | Purpose |
|---|---|
| `spotless-maven-plugin` (Eclipse formatter) | deterministic formatting; `spotless:check` gate, `spotless:apply` fix |
| `checkstyle-maven-plugin` + `config/checkstyle.xml` | `UnusedImports`; token bans: `LAMBDA`, `METHOD_REF`, `LITERAL_VAR`, `RECORD_DEF`, `TEXT_BLOCK_BEGIN`, `SWITCH_RULE`, `PATTERN_VARIABLE_DEF`, `ENUM_DEF` |
| `pmd-maven-plugin` + `config/pmd.xml` | unused local/field/private method; conservative bug rules |
| `maven-compiler-plugin` | `-Xlint:all -Werror` |
| NullAway (Error Prone) | compile-time null-safety gate in the normal build |
| ECJ null-analysis Maven profile | second null-analysis oracle reproducing the exact JDT warning classes |
| `rewrite-maven-plugin` (OpenRewrite) | bulk mechanical refactors and dead-code removal |
| `make lint` / `make fix` | single entry points wrapping the above |

Verification in this phase: toolchain runs on the untouched tree, NullAway is
compatible with JDK 25 + Boot 4 (ECJ profile is the fallback if not), and a
`make lint` baseline is captured.

## 4. Phase 1a — Reorganization and hygiene (branch `chore/repo-cleanup`)

### 4.1 Database relocation

- Create `database/` and `database/backups/`.
- Stop any running server (Windows locks the DB file).
- Move root `sprintjudge.db` to `database/sprintjudge.db`.
- Delete confirmed unreferenced orphans: root `iso2.db`, `iso3.db`,
  `data/sprintjudge.db`, and the empty `data/` directory.
- Update defaults in `src/main/resources/application.yml`,
  `src/main/resources/application-dev.yml`, `src/main/resources/application-prod.yml`
  (`${SPRINTJUDGE_DB_PATH:./database/sprintjudge.db}`), `.env.example`, `README.md`,
  `scripts/backup-db.sh`, `scripts/restore-db.sh`, Makefile comments.
- Backups default to `database/backups/`.
- Add `executor/cache/` to `.gitignore`.

### 4.2 Deletions (provably regeneratable)

- `target/` — rebuilt by `mvn package`.
- Root `seed/master-bundle.json` — SHA-256 verified identical to
  `src/main/resources/seed/master-bundle.json`, which is the copy actually loaded.

Kept (not regeneratable): `drafts/` content (relocated), one-off scripts and tools,
`seed/sets/**`, `HANDOFF.md`.

### 4.3 Drafts relocation

- Move `drafts/` to `seed/drafts/`.
- Update `tools/gen/firewall.js` (`ALLOWED_WRITE_ROOT`) and `tools/gen/generate.js`
  (`logPath`).
- Re-grep every writer before moving.
- Update `.gitignore` (`drafts/` -> `seed/drafts/`).

### 4.4 Dead-code sweep

Remove the unused imports, locals, fields, and the unused private test helper
identified in the editor diagnostics (for example `PublicController.environment`,
`RoomReducer` locals `started`/`submitted`/`finished`, `PracticeSoloFlowTest.uuid`,
`GameRoomTeamBattleFlowTest.session`, plus all unused imports listed).

## 5. Phase 1b — Bank renaming (branch `chore/repo-cleanup`)

All 2,982 files under `src/main/resources/bank/<subject>/`.

Rule (lossless, deterministic):

- Strip the `batch-` prefix.
- Prepend the subject directory name.
- Result: `<subject>-<unit-and-topic>.json`.

Examples:

Filenames below are shown without the unchanged subject directory.

| Before (filename) | After (filename) |
|---|---|
| `batch-u2b-methods.json` | `java-programming-u2b-methods.json` |
| `batch-u9-t6-fixed-exchange-rates-after.json` | `world-history-u9-t6-fixed-exchange-rates-after.json` |
| `batch-cells.json` | `biology-cells.json` |
| `batch-u10.json` | `chemistry-u10.json` |

- Outliers `cf-2.1-0001.json` and `jp-1.1-0001.json` are inspected and mapped by the
  same rule, noting their meaning in the commit message.
- No filename is parsed by application code or scripts (verified: all loaders glob
  `*.json`), so renames are functionally inert.
- Per-subject file counts must be identical before and after; `make check-bank` and
  `make check-volume` must report the same totals.
- Renames are committed separately from deletions.

## 6. Phase 2 — Java conversion (branch `refactor/java8-syntax`)

Order, with the suite green after each step:

1. Annotate the codebase `@NonNull`/`@Nullable`; enable NullAway.
2. `domain` (models, DTOs, enums, reducer, events): 100 records become plain classes
   with fields, getters/setters, `equals`/`hashCode`/`toString` where needed; enums
   become constant classes.
3. `service` (including `leaderboard`, `executor`): lambdas become anonymous inner
   classes, streams become explicit loops, `var` becomes explicit types.
4. `controller`, `config`, `websocket`, `repository`, `util`, `bank`, `scoring`,
   `exception`.
5. Tests: same conversion; coverage and counts preserved.
6. OpenRewrite custom recipes automate the highest-frequency lambda shapes under the
   report-and-confirm policy; the rest are converted by hand.
7. Performance gate: `scripts/load-test.mjs` before vs after; no regressions.
8. Zero-warning gate: `-Xlint:all -Werror` clean.

## 7. Phase 3 — Developer experience

- One-command setup and run: tighten `make check-env`, `make dev`, `make prod`.
- Fast feedback: `make lint` / `make fix`, frontend typecheck/unit/build.
- Error messages and logs: audit judge, DB, and WebSocket failure paths.
- Onboarding docs: fix README/docs drift (port mismatch, stale claims).

## 8. Verification gates

- `mvn verify` green (2,680 backend tests, 100% coverage policy unchanged).
- Frontend `tsc`, unit, build green (untouched).
- `make check-bank`, `make check-volume`, `make check-ui` unchanged results.
- `scripts/load-test.mjs` no regression.
- Zero compiler warnings under `-Xlint:all`.
- `git status` clean at each commit boundary.

## 8a. Conversion conventions (Phase 2 contract)

Every converter MUST follow these rules exactly so the result compiles and reads
the same everywhere.

**Records -> plain classes**

```java
public final class RoundResult {
    private String type;
    private String questionId;
    private boolean revealed;
    private JsonNode correctAnswer;
    private List<PlayerScore> scores;
    private Map<String, Integer> answerCounts;

    public RoundResult() {}

    public RoundResult(String type, String questionId, boolean revealed,
                       JsonNode correctAnswer, List<PlayerScore> scores,
                       Map<String, Integer> answerCounts) {
        this.type = type;
        this.questionId = questionId;
        this.revealed = revealed;
        this.correctAnswer = correctAnswer;
        this.scores = scores;
        this.answerCounts = answerCounts;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    // ... one getter/setter per field; booleans use isRevealed()/setRevealed()

    @Override public boolean equals(Object o) { /* all fields */ }
    @Override public int hashCode() { /* all fields */ }
    @Override public String toString() { /* all fields */ }
}
```

- Record accessors `x()` become `getX()` / `isX()` (booleans). Callers are updated
  in the same change.
- Nested records become `public static final class` members.
- `sealed`/`permits` is removed; interfaces stay plain interfaces.
- Every class gets a no-arg constructor (Jackson) plus the all-args constructor.
- `equals`/`hashCode`/`toString` are implemented with all fields.

**Enums -> constant classes**

```java
public final class GameStatus {
    public static final String LOBBY = "LOBBY";
    public static final String ACTIVE = "ACTIVE";
    public static final String REVIEW = "REVIEW";
    public static final String ENDED = "ENDED";

    private GameStatus() {}
}
```
(Use `int` constants when the enum was ordinal-based; otherwise String.)

**Lambdas -> anonymous inner classes** (explicit types, explicit `return`):

```java
Runnable r = new Runnable() {
    @Override public void run() { doWork(); }
};
Function<Player, String> nameOf = new Function<Player, String>() {
    @Override public String apply(Player p) { return p.getName(); }
};
Comparator<Player> byScore = new Comparator<Player>() {
    @Override public int compare(Player a, Player b) {
        return Long.compare(b.getScore(), a.getScore());
    }
};
```

**Streams -> explicit loops**: `collect(toList())` -> `new ArrayList<>` + `add`;
`groupingBy`/`toMap` -> `HashMap`/`TreeMap` + loops; `anyMatch` -> loop with
`break` and a `boolean`; `findFirst` -> loop returning early; `sorted` ->
`Collections.sort(list, comparator)`; `limit` -> counter; `flatMap` -> nested
loops. Preserve short-circuit semantics exactly.

**Other syntax**

- `var x = ...` -> explicit type declaration.
- Text blocks (`"""`) -> string concatenation.
- Switch expressions / arrow rules -> classic `switch` statement with `break`, or
  if/else chains. Pattern-matching `instanceof T t` -> `if (x instanceof T) {
  T t = (T) x; ... }`.
- Keep `java.time`, `Optional`, `List.of`, `Map.of`, `Set.of`, streams-free
  Collections APIs.

**Null safety**: add `@NonNull`/`@Nullable` (org.eclipse.jdt.annotation) at the
package level using `package-info.java` with `@NonNullByDefault`, and mark every
nullable field/parameter/return with `@Nullable`. Add the annotation dependency
to the POM.

**No behaviour change**: preserve semantics, ordering, and logging; the test suite
(2,717 tests) is the oracle. Do not reformat unrelated code.

## 9. Commit policy

- Conventional commits: `type(scope): description`.
- One logical change per commit; mechanical batches (renames, formatter output) are
  single commits with an exhaustive file list or a summary count plus the list in the
  body.
- No commits to `main` directly; everything lands on the phase branches.
