import { useEffect, useRef } from "react";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { HostControlsView } from "./HostControlsView";
import { HostLeaderboardView } from "./HostLeaderboardView";
import { useEnter } from "../hooks/useMotion";
import { motion } from "../services/MotionService";

export function HostLobbyView() {
  const pin = useUIStore((s) => s.pin);
  const connect = useGameStore((s) => s.connect);
  const join = useGameStore((s) => s.join);
  const room = useGameStore((s) => s.room);

  const cardRef = useEnter<HTMLDivElement>("card", [pin]);
  const pinRef = useEnter<HTMLParagraphElement>("pin", [pin]);
  const playerCount = room?.players.length ?? 0;
  const prevCount = useRef(0);

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

  return (
    <div className="min-h-screen p-4 grid md:grid-cols-[1fr_320px] gap-4 max-w-content mx-auto">
      <div className="flex flex-col gap-4">
        <div ref={cardRef} className="card">
          <span className="text-xs uppercase tracking-wide text-muted">Game PIN</span>
          <p ref={pinRef} className="mono text-4xl font-bold tracking-widest text-center py-2" aria-label={`PIN ${pin}`}>
            {pin.split("").map((d, i) => <span key={i}>{d}</span>)}
          </p>
          <p className="text-center text-muted text-sm">{playerCount} players in the lobby</p>
        </div>
        <HostLeaderboardView />
      </div>
      <HostControlsView />
    </div>
  );
}
