import { useEffect } from "react";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { useEnter, useStaggerIn } from "../hooks/useMotion";
import { motion } from "../services/MotionService";

export function ResultView() {
  const leaderboard = useGameStore((s) => s.leaderboard);
  const setView = useUIStore((s) => s.setView);

  const cardRef = useEnter<HTMLDivElement>("card");
  const listRef = useStaggerIn<HTMLOListElement>("li", [leaderboard.length], 0.07);

  useEffect(() => {
    motion.countUp(listRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [leaderboard.length]);

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div ref={cardRef} className="card w-full max-w-lg">
        <h2 className="header-double text-2xl mb-4 text-center">Final results</h2>
        <ol ref={listRef} className="flex flex-col gap-2">
          {leaderboard.map((r) => (
            <li key={r.uuid} className="flex items-center justify-between px-4 py-3 border border-line">
              <span className="flex items-center gap-3">
                <span className="w-7 h-7 bg-primary text-white text-sm flex items-center justify-center font-mono">{r.rank}</span>
                {r.name}
              </span>
              <span className="font-mono font-semibold" data-score={r.score}>{r.score}</span>
            </li>
          ))}
          {leaderboard.length === 0 && <li className="text-muted text-center">No results.</li>}
        </ol>
        <button onClick={() => setView("join")} className="btn btn-secondary w-full mt-6">
          Back to home
        </button>
      </div>
    </div>
  );
}
