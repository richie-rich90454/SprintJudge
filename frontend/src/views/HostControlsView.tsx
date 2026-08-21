import { useGameStore } from "../stores/useGameStore";

export function HostControlsView() {
  const hostCommand = useGameStore((s) => s.hostCommand);
  const extendTimer = useGameStore((s) => s.extendTimer);
  const kick = useGameStore((s) => s.kick);
  const status = useGameStore((s) => s.status);
  const room = useGameStore((s) => s.room);

  const players = room?.players ?? [];

  return (
    <div className="card flex flex-col gap-3">
      <h3 className="header-double">Host controls</h3>
      <div className="flex flex-wrap gap-2">
        {status === "LOBBY" || status === "REVIEW" ? (
          <button onClick={() => hostCommand("NEXT_QUESTION")} className="btn btn-primary">
            {status === "LOBBY" ? "Start round" : "Next question"}
          </button>
        ) : (
          <button onClick={() => hostCommand("FORCE_SUBMIT")} className="btn btn-secondary">
            Force submit
          </button>
        )}
        <button onClick={() => extendTimer(30)} className="btn btn-secondary">+30s</button>
        <button onClick={() => hostCommand("END_GAME")} className="btn btn-danger">End game</button>
      </div>
      <div>
        <p className="text-sm text-muted mb-1">Players</p>
        <ul className="flex flex-col gap-1">
          {players.map((p) => (
            <li key={p.uuid} className="flex items-center justify-between text-sm border-b border-line py-1">
              <span>{p.name}</span>
              <button onClick={() => kick(p.uuid)} className="text-danger text-xs">Kick</button>
            </li>
          ))}
          {players.length === 0 && <li className="text-sm text-muted">None</li>}
        </ul>
      </div>
    </div>
  );
}
