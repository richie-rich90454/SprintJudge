import { useGameStore } from "../stores/useGameStore";

export function HostControlsView() {
  const hostCommand = useGameStore((s) => s.hostCommand);
  const extendTimer = useGameStore((s) => s.extendTimer);
  const kick = useGameStore((s) => s.kick);
  const status = useGameStore((s) => s.status);
  const room = useGameStore((s) => s.room);

  const players = room?.players ?? [];

  return (
    <div className="bg-surface shadow-card rounded-xl border border-border p-4 flex flex-col gap-3">
      <h3 className="font-semibold">Host controls</h3>
      <div className="flex flex-wrap gap-2">
        {status === "LOBBY" || status === "REVIEW" ? (
          <button onClick={() => hostCommand("NEXT_QUESTION")} className="min-h-tap px-4 rounded-lg bg-primary text-white font-medium hover:bg-primary-dark">
            {status === "LOBBY" ? "Start round" : "Next question"}
          </button>
        ) : (
          <button onClick={() => hostCommand("FORCE_SUBMIT")} className="min-h-tap px-4 rounded-lg border border-border bg-surface hover:border-primary">
            Force submit
          </button>
        )}
        <button onClick={() => extendTimer(30)} className="min-h-tap px-4 rounded-lg border border-border bg-surface hover:border-primary">
          +30s
        </button>
        <button onClick={() => hostCommand("END_GAME")} className="min-h-tap px-4 rounded-lg border border-danger text-danger bg-surface hover:bg-surface-alt">
          End game
        </button>
      </div>
      <div>
        <p className="text-sm text-muted mb-1">Players</p>
        <ul className="flex flex-col gap-1">
          {players.map((p) => (
            <li key={p.uuid} className="flex items-center justify-between text-sm">
              <span>{p.name}</span>
              <button onClick={() => kick(p.uuid)} className="text-danger text-xs">Kick</button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
