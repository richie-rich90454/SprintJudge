import { useGameStore } from "../stores/useGameStore";

export function HostLeaderboardView() {
  const leaderboard = useGameStore((s) => s.leaderboard);
  const room = useGameStore((s) => s.room);

  const rows = leaderboard.length ? leaderboard : (room?.players ?? []).map((p, i) => ({ uuid: p.uuid, name: p.name, score: p.score, rank: i + 1 }));

  return (
    <div className="bg-surface shadow-card rounded-xl border border-border p-4">
      <h3 className="font-semibold mb-3">Live leaderboard</h3>
      <ol className="flex flex-col gap-2">
        {rows.map((r) => (
          <li key={r.uuid} className="flex items-center justify-between px-4 py-2 rounded-lg bg-surface-alt">
            <span className="flex items-center gap-3">
              <span className="w-6 h-6 rounded-full bg-primary text-white text-xs flex items-center justify-center font-mono">{r.rank}</span>
              {r.name}
            </span>
            <span className="font-mono font-semibold">{r.score}</span>
          </li>
        ))}
        {rows.length === 0 && <li className="text-muted text-sm">No players yet.</li>}
      </ol>
    </div>
  );
}
