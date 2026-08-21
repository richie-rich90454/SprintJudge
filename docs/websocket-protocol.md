# WebSocket Protocol

The game uses a single vanilla Jakarta @ServerEndpoint at /ws. Messages are JSON with a "type" field.

## Client to Server

JOIN: { "type": "JOIN", "role": "player", "name": "Alice", "pin": "123456" }

SUBMIT: { "type": "SUBMIT", "questionId": "q123", "language": "python", "response": { ... } }

Host or admin commands: NEXT_QUESTION, FORCE_SUBMIT, END_GAME, EXTEND_TIMER (with "seconds"), KICK_PLAYER (with "playerUuid").

### Message schema validation

- A message must declare a non-empty "type".
- JOIN requires "pin" and "name".
- SUBMIT requires "questionId" and a "response" payload.

## Server to Client

| Type | Contents |
|------|----------|
| ROOM_STATE | players, status, question count |
| QUESTION_START | full question DTO, time limit, start timestamp |
| ROUND_RESULT | scores per player, correct answer |
| LEADERBOARD | rankings array |
| GAME_END | final rankings |
| ERROR | message |
| TIMER_UPDATE | new end timestamp, extend seconds |

## Edge cases on the wire

- Player names are sanitized (alphanumerics, spaces, hyphens, underscores; max 20 chars) before they enter any broadcast.
- Force-submit does not preempt in-flight judge tasks; it only reveals the current round result.
