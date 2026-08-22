import { useEffect, useRef } from "react";
import { useTimerStore } from '../stores/useTimerStore';
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { HostControlsView } from "./HostControlsView";
import { HostLeaderboardView } from "./HostLeaderboardView";
import { CircularTimer } from "../components/Timer/CircularTimer";
import { useEnter } from "../hooks/useMotion";
import { motion } from "../services/MotionService";

export function HostLobbyView() {
  const pin = useUIStore((s) => s.pin);
  const connect = useGameStore((s) => s.connect);
  const join = useGameStore((s) => s.join);
  const room = useGameStore((s) => s.room);
  const q = useGameStore((s) => s.currentQuestion);
  const end = useTimerStore((s) => s.endEpochMs);

  const playerCount = room?.players.length ?? 0;
  const prevCount = useRef(0);
  const pinRef = useEnter<HTMLParagraphElement>("pin", [pin]);
  const cardRef = useEnter<HTMLDivElement>("card", [playerCount]);

  useEffect(() => {
    if (!pin) return;
    const proto = location.protocol === "https:" ? "wss" : "ws";
    connect(`${proto}://${location.host}/ws`);
    join(pin, "Host", "host");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pin]);

  useEffect(() => {
    if (playerCount > prevCount.current) motion.pulse(cardRef.current);
    prevCount.current = playerCount;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playerCount]);

  if (!pin) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4">
        <p className="text-muted">Create or open a game from the Admin dashboard to host.</p>
      </div>
    );
  }

  const statusLabel =
    room?.status === "ACTIVE" ? "Live round" :
    room?.status === "REVIEW" ? "Reviewing answers" : "Lobby open";

  return (
    <div className="pattern-exam min-h-screen pb-10">
      {/* Presenter header: PIN + status + timer for the live round */}
      <header className="border-b-2 bg-surface" style={{ borderColor: "#C8102E" }}>
        <div className="page-shell py-5 flex flex-wrap items-center gap-x-10 gap-y-4">
          <div ref={pinRef} className="flex items-baseline gap-3">
            <span className="label-caps">PIN</span>
            <span className="mono font-extrabold" style={{ fontSize: "clamp(34px,5vw,52px)", letterSpacing: "0.18em", lineHeight: 1 }}>
              {pin}
            </span>
          </div>
          <div className="h-10 w-px bg-line hidden md:block" />
          <div>
            <p className="label-caps mb-1">{statusLabel}</p>
            <p className="text-sm text-muted">
              <span className="mono font-bold" style={{ color: "#C8102E" }}>{playerCount}</span> players joined
            </p>
          </div>
          <div className="ml-auto">
            {room?.status === "ACTIVE" && end && q ? (
              <CircularTimer endEpochMs={end} totalSec={q.timeLimitSec} />
            ) : (
              <span className="label-caps">Awaiting start</span>
            )}
          </div>
        </div>
      </header>

      {/* Presenter flow: current-question preview above, leaderboard stage below */}
      <main className="page-shell mt-8 grid lg:grid-cols-[1fr_320px] gap-6 items-start">
        <section className="flex flex-col gap-6 min-w-0">
          {q && (
            <div ref={cardRef} className="card">
              <p className="label-caps mb-2">On screen now</p>
              <h2 className="text-2xl font-extrabold tracking-tight pr-20">{q.title}</h2>
              {q.description && (
                <p className="text-muted mt-2 line-clamp-3 whitespace-pre-wrap">{q.description}</p>
              )}
              <div className="mt-4 h-[3px] w-16" style={{ background: "#C8102E" }} />
            </div>
          )}
          <HostLeaderboardView />
        </section>

        <aside className="lg:sticky lg:top-6">
          <HostControlsView />
        </aside>
      </main>
    </div>
  );
}
