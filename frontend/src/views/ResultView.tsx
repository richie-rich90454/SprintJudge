import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";

export function ResultView() {
  const leaderboard = useGameStore((s) => s.leaderboard);
  const setView = useUIStore((s) => s.setView);

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="w-full max-w-lg bg-surface shadow-card rounded-xl border border-border p-6">
        <h2 className="text-2xl font-bold mb-4 text-center">Final results</h2>
        <ol className="flex flex-col gap-2">
          {leaderboard.map((r) => (
            <li key={r.uuid} className="flex items-center justify-between px-4 py-3 rounded-lg bg-surface-alt">
              <span className="flex items-center gap-3">
                <span className="w-7 h-7 rounded-full bg-primary text-white text-sm flex items-center justify-center font-mono">{r.rank}</span>
                {r.name}
              </span>
              <span className="font-mono font-semibold">{r.score}</span>
            </li>
          ))}
          {leaderboard.length === 0 && <li className="text-muted text-center">No results.</li>}
        </ol>
        <button onClick={() => setView("join")} className="w-full min-h-tap mt-6 rounded-lg border border-border bg-surface hover:border-primary">
          Back to home
        </button>
      </div>
    </div>
  );
}
