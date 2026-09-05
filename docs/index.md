---
layout: home
hero:
  name: SprintJudge
  text: Real-time coding quiz platform with a built-in Online Judge
  tagline: The open-source alternative to closed MCQ-only tools — 12 question formats, live execution, Kahoot-style adrenaline.
  actions:
    - theme: brand
      text: Get Started
      link: /getting-started
    - theme: alt
      text: Admin Guide
      link: /admin-guide
features:
  - title: 12 question formats
    details: MCQ, True/False, Multiple Select, Numeric, Output Prediction, Fill Blank, Drag Sort, Click-the-Bug, Code Completion, Complexity, and two full Online-Judge modes.
  - title: Real code execution
    details: Players write actual C, C++, Java, Node.js or Python, compiled and run against hidden test cases with nsjail (Linux) or WSL2 (Windows) isolation.
  - title: Portable & fast
    details: Single-file SQLite (WAL), Java 25 virtual threads + ZGC, vanilla Jakarta WebSocket, auto-sized judge concurrency.
---

## Game modes

| Mode | Timer | Board | Notes |
|------|-------|-------|-------|
| STANDARD | per question | At review | Classic host-led game |
| AUTO_PILOT | per question | At review | Auto-advances after review |
| PRACTICE | none | Live | Instant feedback, auto-advance |
| EXAM | total clock | Hidden till end | Rankings only in GAME_REVIEW |
| TEAM | per question | At review | Create/join teams, team scoring |
| BATTLE | per question | At review | 1v1 matchmaking + bracket |

```mermaid
flowchart TB
    S["New game"] --> M{"mode?"}
    M -->|host-led| H["STANDARD / TEAM / BATTLE"]
    M -->|self-run| P["AUTO_PILOT / PRACTICE"]
    M -->|strict| E["EXAM total clock"]
    H --> PIN["6-digit PIN + /j/ link"]
    P --> PIN
    E --> PIN
```
