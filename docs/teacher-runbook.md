# Teacher runbook

A short guide for running a live class. Written for the production jar on the
teacher's machine.

## 1. First boot

1. Install Java 21 or newer.
2. Put `sprintjudge.jar` in a folder the teacher can write to.
3. Copy `.env.example` to `.env` next to the jar and set the admin login:

   ```env
   SPRINTJUDGE_ADMIN_USERNAME=admin
   SPRINTJUDGE_ADMIN_PASSWORD=choose-a-real-one
   SPRINTJUDGE_PORT=8080
   ```

4. Start the jar:

   ```bash
   java -jar sprintjudge.jar --spring.profiles.active=prod
   ```

5. Open `http://localhost:8080` and sign in to `/admin`.

The first boot seeds the bundled question bank. On a typical laptop it takes
about 30 seconds; later boots skip the seed and start in under 10 seconds.
Do not stop the process while `Seeding` is still in the log.

The SQLite file (`sprintjudge.db`) is created next to the jar. Back it up before
upgrading; it holds every question set, game, and score.

## 2. Check the bank

```bash
make check-bank
```

This is the strict content gate. It must be green before a class that depends on
the bundled library. The app itself still boots if one bank file is malformed;
that file is skipped and logged.

## 3. Run a game

1. Sign in at `/admin`.
2. Pick a quiz, or use the CSA presets on the dashboard.
3. Choose a mode:

   - Standard: teacher-paced rounds.
   - Auto-pilot: rounds advance themselves.
   - Practice: one student, untimed, immediate feedback.
   - Exam: one clock for the whole paper, board hidden until the end.
   - Team: students pick a team in the lobby; every answer feeds the team total.
   - Battle: 1v1 rounds with a bye and a full bracket, ending in a champion.

4. Press **Start round** (or **Start battle** in battle mode) and project the
   host screen with **Full screen**.
5. **Reveal answer** closes the round and shows the distribution, the top
   misconception, and the explanation.
6. **End game** produces the results screen. Export CSV downloads the room
   report; the Teams tab appears for team games.

## 4. Students join on the LAN

1. The host screen shows the join URL and a QR code.
2. The URL uses the machine's LAN address when the teacher opened the app on
   `localhost`, so phones on the same network can join.
3. If phones cannot connect:
   - allow the port through the local firewall (default 8080),
   - confirm the phone is on the same Wi-Fi and not on a guest network,
   - open the printed LAN URL directly, not `localhost`.
4. A refresh keeps the student in the game; the seat and score are reclaimed
   from the stored session token.

## 5. Troubleshooting

| Symptom | Check |
|---|---|
| Page does not load | Is the jar still running? Is the port free? |
| Students get "Invalid PIN" | The room ended; start a new game and share the new PIN. |
| A student is stuck after refresh | Ask them to open the same join link again. |
| Coding runs fail on Linux | Set `SPRINTJUDGE_EXECUTOR_MODE=nsjail` and verify nsjail. |
| Bank file warnings in the log | Run `make check-bank` and fix the named file. |
| Jar cannot be replaced on Windows | Stop the running server first; Windows locks the file. |
