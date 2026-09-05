# Architecture

SprintJudge is a Spring Boot 4 monolith with a vanilla Jakarta WebSocket endpoint and a
React 19 single-page frontend. Everything runs on a single portable SQLite database.

## System overview

```mermaid
flowchart TB
    subgraph Clients["Browsers"]
        P["Player SPA<br/>React 19 + RxJS services"]
        H["Host / Admin SPA<br/>form-login session"]
    end

    subgraph Edge["Nginx - TLS termination"]
        N["Reverse proxy<br/>slash-ws upgrade · slash-api · static"]
    end

    subgraph App["Spring Boot 4 · Java 25 · virtual threads · ZGC"]
        direction TB
        WS["GameWebSocket<br/>vanilla @ServerEndpoint"]
        REST["REST controllers<br/>Admin · Public"]
        GRM["GameRoomManager<br/>ConcurrentHashMap rooms"]
        EV["EvaluationService<br/>per-type correctness"]
        SC["ScoringEngine<br/>decay + attempts"]
        SP["SubmissionProcessor<br/>Semaphore(100)"]
        EXC["CodeExecutor<br/>nsjail / WSL2 / native"]
        IE2["ImportExportService"]
        AS2["AdminSettingsService"]
    end

    DB[("SQLite · WAL mode")]

    P -->|WebSocket /ws| N
    H -->|HTTPS REST + WS| N
    N --> WS
    N --> REST
    REST --> GRM
    REST --> IE2
    REST --> AS2
    WS --> GRM
    GRM --> SP
    SP --> EV
    SP --> SC
    SP --> EXC
    GRM -->|"leaderboard broadcasts"| WS
    GRM --> DB
    SP --> DB
```

## Game state machine

A session walks a strict lifecycle. The host drives transitions; players never can.

```mermaid
stateDiagram-v2
    [*] --> LOBBY: host creates game (6-digit PIN)
    LOBBY --> ACTIVE: NEXT_QUESTION
    ACTIVE --> REVIEW: timer hits zero or FORCE_SUBMIT
    REVIEW --> ACTIVE: NEXT_QUESTION (more questions)
    REVIEW --> ENDED: NEXT_QUESTION (last question) or END_GAME
    ENDED --> [*]
    note right of ACTIVE: coding submissions stay queued<br/>on the auto-sized semaphore; force-submit<br/>never preempts them
```

## Submission pipeline (Online Judge)

```mermaid
flowchart LR
    A["SUBMIT over WebSocket"] --> B{"Schema check<br/>language whitelist<br/>64KB source cap"}
    B -->|"rejected"| E1["ERROR to sender"]
    B -->|"accepted"| C["Enqueue on<br/>virtual-thread executor"]
    C --> D{"Auto-sized semaphore<br/>slot available?"}
    D -->|"wait in queue"| D
    D -->|"acquired"| F["Write source to temp dir"]
    F --> G{"Compiled language?"}
    G -->|"yes"| H["Compile once<br/>fail fast on error"]
    G -->|"no"| I
    H -->|"compilation_error"| J["All cases failed<br/>log carries reason"]
    H -->|"ok"| I["Run per test case<br/>stdin from file · timeout · 1MB stdout cap"]
    I --> K["Compare trimmed stdout"]
    K --> L["Score = passed/total × base × speed decay"]
    L --> M["Keep highest score per player"]
    M --> N["Broadcast LEADERBOARD"]
```

## Module layering

```mermaid
flowchart TD
    subgraph Web["websocket · controller · exception"]
        IN["Inbound adapters"]
    end
    subgraph Domain["service"]
        SVC["Game orchestration · scoring · judging · settings"]
    end
    subgraph Persistence["repository"]
        DAO["JOOQ DAOs — type-safe, parameterized, no raw SQL"]
    end
    subgraph Model["domain"]
        M["enums · records · DTOs"]
    end
    IN --> SVC --> DAO
    SVC --> M
    DAO --> M
```

## Performance architecture (10k players/room)

Three exact, lock-light structures carry the hot path:

```mermaid
flowchart LR
    SUB["score mutation"] --> IDX["RankedSkipList<br/>order-statistic skip list<br/>O(log n) · exact spans"]
    IDX --> LED["DeltaLedger<br/>monotonic seq per room"]
    LED --> CO["BroadcastScheduler<br/>16 ms coalescing tick"]
    CO --> OUT["serialize once →<br/>fan out to sessions"]
    SUB --> BUF["SubmissionWriteBuffer<br/>250 ms JOOQ batch"]
```

- `RankedSkipList` — Redis zskiplist spans; rank/select are exact, ties by join order.
- `DeltaLedger` — merges pending changes per player; clients resync on any seq gap.
- `LiveLeaderboard` — binds identity map + index + ledger behind one facade.
  The host holds a roster seat only and never enters the scored board.
- `ExecutorSizingConfig` — judge concurrency is `cores × factor` (floor 8, cap 512),
  overridable via `sprintjudge.executor.max-concurrent`.
- `RoomRegistry` — int-keyed open-addressing map for thousands of rooms.
- `CompileArtifactCache` — SHA-256 keyed binaries for identical C/C++ resubmits.
- `/api/admin/metrics` — heap/GC/threads, judge latency percentiles, cache ratio.

## Executor modes

| Mode | Where | Isolation | Use when |
|------|-------|-----------|----------|
| `native` | Windows/Linux dev | None (dev only) | Fastest setup; toolchains on PATH |
| `wsl` | Windows dev | Separate Linux VM | Parity with production scripts |
| `nsjail` | Linux production | chroot + rlimits | Always, in production |
