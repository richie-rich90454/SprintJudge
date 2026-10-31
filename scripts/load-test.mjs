// 30-player classroom load probe.
// Connects N WebSocket players to a live practice room, then measures join,
// question-start, and reveal broadcast latency. Requires a running backend
// (default :8080) with a seeded bank.
//
//   node scripts/load-test.mjs [players] [rounds]
//   BASE_URL=http://host:8080 PLAYERS=30 node scripts/load-test.mjs
const BASE = (process.env.BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
const WS = process.env.WS_URL ?? BASE.replace(/^http/, "ws") + "/ws";
const PLAYERS = Number(process.env.PLAYERS ?? process.argv[2] ?? 30);
const TIMEOUT_MS = Number(process.env.TIMEOUT_MS ?? 25_000);

const now = () => Number(process.hrtime.bigint() / 1_000_000n);

function percentile(values, p) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return Math.round(sorted[index]);
}

async function json(url, init) {
  const res = await fetch(url, init);
  if (!res.ok) throw new Error(`${url} -> ${res.status}`);
  return res.json();
}

async function main() {
  const banks = await json(`${BASE}/api/public/banks`);
  if (!Array.isArray(banks) || banks.length === 0) {
    throw new Error("no banks returned by /api/public/banks");
  }
  const room = await json(`${BASE}/api/public/practice`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ quizId: banks[0].id }),
  });
  if (!/^\d{6}$/.test(room.pinCode ?? "")) throw new Error("practice room has no PIN");

  const stats = {
    joined: 0,
    questionStarted: 0,
    revealed: 0,
    errors: [],
    joinMs: [],
    questionMs: [],
    revealMs: [],
  };

  const timing = { submitAt: 0 };
  const clients = Array.from({ length: PLAYERS }, (_, i) =>
    connect(i, room.pinCode, stats, timing),
  );
  await Promise.all(clients.map((c) => c.ready));
  timing.submitAt = now();
  // One answer closes the practice round and fans ROUND_RESULT out to everyone.
  if (!clients[0].questionId) throw new Error("first client never received a question");
  clients[0].socket.send(
    JSON.stringify({ type: "SUBMIT", questionId: clients[0].questionId, response: { selectedIndex: 0 } }),
  );
  await Promise.all(clients.map((c) => c.revealed));
  const elapsed = now() - timing.submitAt;
  for (const c of clients) c.socket.close();

  const report = {
    players: PLAYERS,
    joined: stats.joined,
    questionStarted: stats.questionStarted,
    revealed: stats.revealed,
    joinP50: percentile(stats.joinMs, 50),
    joinP95: percentile(stats.joinMs, 95),
    questionP50: percentile(stats.questionMs, 50),
    questionP95: percentile(stats.questionMs, 95),
    revealP50: percentile(stats.revealMs, 50),
    revealP95: percentile(stats.revealMs, 95),
    revealWallMs: elapsed,
    errors: stats.errors.slice(0, 5),
  };
  console.log(JSON.stringify(report, null, 2));

  const dropped = PLAYERS - Math.min(stats.joined, stats.questionStarted, stats.revealed);
  if (dropped > 0 || stats.errors.length > 0) {
    console.error(`load test failed: ${dropped} client(s) dropped`);
    process.exitCode = 1;
  }
}

function connect(index, pin, stats, timing) {
  const socket = new WebSocket(WS);
  const joinedAt = now();
  let questionId = null;
  let resolveReady;
  let resolveRevealed;
  const ready = new Promise((resolve) => (resolveReady = resolve));
  const revealed = new Promise((resolve) => (resolveRevealed = resolve));
  const timer = setTimeout(() => {
    stats.errors.push(`player ${index} timed out`);
    resolveReady();
    resolveRevealed();
  }, TIMEOUT_MS);

  socket.addEventListener("open", () => {
    socket.send(JSON.stringify({ type: "JOIN", role: "player", name: `Load-${index}`, pin }));
  });

  socket.addEventListener("message", (event) => {
    let message;
    try {
      message = JSON.parse(String(event.data));
    } catch {
      return;
    }
    if (message.type === "JOINED") {
      stats.joined++;
      stats.joinMs.push(now() - joinedAt);
      return;
    }
    if (message.type === "QUESTION_START") {
      if (questionId === null) {
        questionId = message.question?.id ?? null;
        if (stats.questionStarted < PLAYERS) {
          stats.questionStarted++;
          stats.questionMs.push(now() - joinedAt);
        }
        resolveReady();
      }
      return;
    }
    if (message.type === "ROUND_RESULT") {
      if (stats.revealed < PLAYERS) {
        stats.revealed++;
        stats.revealMs.push(now() - (timing.submitAt || joinedAt));
      }
      clearTimeout(timer);
      resolveReady();
      resolveRevealed();
      return;
    }
    if (message.type === "ERROR" && message.message) {
      stats.errors.push(`player ${index}: ${message.message}`);
    }
  });

  socket.addEventListener("error", () => {
    stats.errors.push(`player ${index}: socket error`);
    clearTimeout(timer);
    resolveReady();
    resolveRevealed();
  });

  return { socket, ready, revealed, get questionId() { return questionId; } };
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
