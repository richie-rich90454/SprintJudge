import { useEffect } from "react";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { HostControlsView } from "./HostControlsView";
import { HostLeaderboardView } from "./HostLeaderboardView";

export function HostLobbyView() {
  const pin = useUIStore((s) => s.pin);
  const connect = useGameStore((s) => s.connect);
  const join = useGameStore((s) => s.join);
  const room = useGameStore((s) => s.room);

  useEffect(() => {
    if (!pin) return;
    const proto = location.protocol === "https:" ? "wss" : "ws";
    connect(`${proto}://${location.host}/ws`);
    join(pin, "Host", "host");
  }, [pin]);

  if (!pin) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4">
        <p className="text-muted">Create or open a game from the Admin dashboard to host.</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen p-4 grid md:grid-cols-[1fr_320px] gap-4 max-w-5xl mx-auto">
      <div className="flex flex-col gap-4">
        <div className="bg-surface shadow-card rounded-xl border border-border p-4">
          <span className="text-xs uppercase tracking-wide text-muted">Game PIN</span>
          <p className="font-mono text-4xl font-bold tracking-widest text-center py-2">{pin}</p>
          <p className="text-center text-muted text-sm">{room?.players.length ?? 0} players in the lobby</p>
        </div>
        <HostLeaderboardView />
      </div>
      <HostControlsView />
    </div>
  );
}
