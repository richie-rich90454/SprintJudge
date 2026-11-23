# Optional player profiles

Joining a game stays anonymous: the join flow only needs a PIN and a nickname.
A profile is opt-in and exists solely so a player can keep a nickname, score
history, achievements, and a portfolio of finished quizzes across sessions.

## Identity model

There is no password account. Creating a profile returns a random 64-character
`linkToken`. The browser stores that token plus the profile id locally and
sends them as `Authorization: Bearer <token>` on later calls. Tokens are
compared in constant time; without the token a profile cannot be read or
changed. Profile ids are not a secret and are not enough on their own.

To attach past results, the client also supplies the `playerUuid` it received
when joining and the live `rejoinToken` from the same game. The server checks
that pair against `player_sessions`; only then is the game identity linked.
A profile may be created without a link and linked later. A new browser session
gets a new game identity, so `PATCH` adds another link instead of replacing the
old one - both sets of results keep accumulating in the same profile.

## Endpoints

All routes live under `/api/public/profiles` and are CSRF-exempt like the other
public routes.

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/public/profiles` | `{ nickname, playerUuid?, rejoinToken? }` | Create a profile; returns `201` and the full report including the link token |
| GET | `/api/public/profiles/{id}` | – | Read profile, stats, history, achievements, portfolio |
| PATCH | `/api/public/profiles/{id}` | `{ nickname?, playerUuid?, rejoinToken? }` | Rename and/or link a game identity |

Statuses: `400` invalid nickname or rejoin token, `401` missing/invalid bearer
token, `404` unknown profile, `409` game identity already linked. Nicknames go
through the same sanitizer as join names (letters, digits, spaces, hyphens,
underscores; 20 characters).

## Where the data comes from

Two tables are added (`V5__profiles.sql`): `player_profiles` for the nickname
and token, and `profile_links` mapping one profile to the game identities it
has claimed. History is **derived**, not duplicated: the store groups existing
`submissions` rows by game session (joined through `profile_links` to
`game_sessions` and `quizzes`), bounded to the 500 most recent sessions.
Portfolio entries are the same history grouped by quiz. Stats and achievements
are computed from those rows:
first quiz, five quizzes, 10 answers at 80% accuracy, a five-answer correct
streak, and 1,000 total points. Nothing in the game loop or scoring path was
changed, and anonymous play writes nothing to the profile tables.

The profile store applies the idempotent migration script at construction, so
older databases gain the tables without a manual step. Registering
`V5__profiles.sql` in the normal startup migration list as well is harmless.

## Frontend

`ProfileView` shows a create form when no local identity exists, then the
achievement grid, score history, portfolio, and a rename field. It reads the
current game identity from the game store, so the "link this game identity"
action only appears when a token is available. `ProfileApiService` owns the
local credential storage and the three HTTP calls.

## Integration points for the host application

- Add a `/profile` route pointing at `ProfileView` and a link from the landing
  screen.
- Permit the `/profile` deep link in the web security matcher (the SPA forward
  already serves unknown paths from `index.html`).

## Limitations

The link token is a capability: losing local storage loses access to that
profile, and there is no account recovery or delete endpoint. Rejoin tokens
expire, so an old game identity can only be linked while its session is fresh.
