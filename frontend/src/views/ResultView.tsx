import { useEffect } from "react";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { useEnter, useStaggerIn } from "../hooks/useMotion";
import { motion } from "../services/MotionService";

export function ResultView() {
  const leaderboard = useGameStore((s) => s.leaderboard);
  const setView = useUIStore((s) => s.setView);

  const cardRef = useEnter<HTMLDivElement>("card");
  const podium = leaderboard.slice(0, 3);
  const rest = leaderboard.slice(3);
  const podiumRef = useEnter<HTMLDivElement>("podium", [leaderboard.length]);
  const listRef = useStaggerIn<HTMLOListElement>("li", [rest.length], 0.06);

  useEffect(() => { motion.countUp(listRef.current); }, [rest.length]);

  const heights = [128, 88, 64];                       // 1st, 2nd, 3rd column heights
  const order = [1, 0, 2];                             // display: 2nd, 1st, 3rd
  const medal = ["1", "2", "3"];

  return (
    <div className="pattern-exam min-h-screen py-10">
      <div className="page-shell max-w-3xl">
        <div className="text-center mb-10">
          <p className="label-caps mb-2">Final standings</p>
          <h1 className="font-extrabold tracking-tight" style={{ fontSize: "clamp(34px,5vw,56px)" }}>
            Results
          </h1>
          <div className="mt-4 h-[3px] w-20 mx-auto" style={{ background: "#C8102E" }} />
        </div>

        <div ref={cardRef} className="card pt-10 pb-8 px-6 md:px-10">
          {/* Podium */}
          <div ref={podiumRef} className="flex items-end justify-center gap-4 mb-10 min-h-[190px]">
            {podium.length === 0 && <p className="text-muted">No results.</p>}
            {order.map((idx) => {
              if (idx >= podium.length) return null;
              const p = podium[idx];
              return (
                <div key={p.uuid} className="flex flex-col items-center w-28 md:w-36">
                  <span className="mono font-extrabold mb-2"
                        style={{ fontSize: idx === 0 ? 30 : 22, color: "#C8102E" }}>
                    {p.score.toLocaleString()}
                  </span>
                  <span className={`font-bold truncate max-w-full ${idx === 0 ? "text-lg" : ""}`}
                        title={p.name}>
                    {p.name}
                  </span>
                  <div className="w-full mt-2 border-x-2 border-t-2 flex items-start justify-center pt-2"
                       style={{
                         height: heights[idx],
                         borderColor: "#C8102E",
                         background: "var(--oq-row-alt)",
                         borderRadius: "12px 12px 0 0",
                       }}>
                    <span className="mono font-extrabold"
                          style={{ fontSize: idx === 0 ? 44 : 30, color: "#C8102E", lineHeight: 1 }}>
                      {medal[idx]}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Everyone else */}
          {rest.length > 0 && (
            <ol ref={listRef} className="flex flex-col gap-2">
              {rest.map((r) => (
                <li key={r.uuid}
                    className="flex items-center justify-between px-4 py-3 border border-line rounded-xl bg-row-alt">
                  <span className="flex items-center gap-3">
                    <span className="w-7 h-7 mono text-sm flex items-center justify-center border border-line rounded-md">
                      {r.rank}
                    </span>
                    {r.name}
                  </span>
                  <span className="mono font-semibold" data-score={r.score}>{r.score}</span>
                </li>
              ))}
            </ol>
          )}

          <button onClick={() => setView("join")} className="btn btn-secondary w-full mt-8">
            Back to lobby
          </button>
        </div>
      </div>
    </div>
  );
}
