import { useGameStore } from "../stores/useGameStore";
import { useVirtualWindow } from "../hooks/useVirtualWindow";

const ROW_H = 40;

/**
 * Virtualized live leaderboard: renders only the visible window, so a
 * 10,000-player room scrolls as smoothly as ten players. Ranks come straight
 * from the server's exact skip-list order — the client never re-sorts.
 */
export function HostLeaderboardView() {
  const leaderboard = useGameStore((s) => s.leaderboard);
  const room = useGameStore((s) => s.room);

  const rows = leaderboard.length
    ? leaderboard
    : (room?.players ?? []).map((p, i) => ({ uuid: p.uuid, name: p.name, score: p.score, rank: i + 1 }));

  const { ref, start, end } = useVirtualWindow(rows.length, ROW_H);
  const slice = rows.slice(start, end);

  return (
    <div className="card">
      <h3 className="header-double mb-3">Live leaderboard</h3>
      <div className="grid grid-cols-[48px_1fr_90px] px-2 pb-1 text-xs uppercase tracking-wide text-muted">
        <span>#</span><span>Player</span><span className="text-right">Score</span>
      </div>
      <div ref={ref} className="overflow-y-auto" style={{ maxHeight: 420 }}>
        {rows.length > 0 && (
          <>
            <div style={{ height: start * ROW_H }} aria-hidden="true" />
            {slice.map((r) => (
              <div
                key={r.uuid}
                className="lb-row grid grid-cols-[48px_1fr_90px] items-center border-b border-dotted border-line"
                style={{ height: ROW_H }}
              >
                <span className="font-mono text-muted">{r.rank}</span>
                <span className="truncate">{r.name}</span>
                <span className="text-right font-mono font-semibold" data-score={r.score}>{r.score}</span>
              </div>
            ))}
            <div style={{ height: Math.max(0, (rows.length - end) * ROW_H) }} aria-hidden="true" />
          </>
        )}
        {rows.length === 0 && <p className="text-muted text-sm">No players yet.</p>}
      </div>
    </div>
  );
}
