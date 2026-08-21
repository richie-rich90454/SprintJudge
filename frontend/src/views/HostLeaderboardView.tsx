import { useGameStore } from "../stores/useGameStore";

export function HostLeaderboardView() {
  const leaderboard = useGameStore((s) => s.leaderboard);
  const room = useGameStore((s) => s.room);

  const rows = leaderboard.length
    ? leaderboard
    : (room?.players ?? []).map((p, i) => ({ uuid: p.uuid, name: p.name, score: p.score, rank: i + 1 }));

  return (
    <div className="card">
      <h3 className="header-double mb-3">Live leaderboard</h3>
      <table className="table-dotted">
        <thead>
          <tr>
            <th>#</th>
            <th>Player</th>
            <th>Score</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.uuid}>
              <td className="font-mono">{r.rank}</td>
              <td>{r.name}</td>
              <td className="font-mono font-semibold">{r.score}</td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr><td colSpan={3} className="text-muted">No players yet.</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
