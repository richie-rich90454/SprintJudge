import { useGameStore } from "../stores/useGameStore";

export function HostControlsView() {
  const hostCommand = useGameStore((s) => s.hostCommand);
  const extendTimer = useGameStore((s) => s.extendTimer);
  const status = useGameStore((s) => s.status);
  const room = useGameStore((s) => s.room);

  const players = room?.players ?? [];
  const active = status === "ACTIVE";

  return (
    <div className="card !p-0 overflow-hidden">
      <div className="px-5 pt-5 pb-4 border-b border-line">
        <h3 className="font-extrabold text-lg">Controls</h3>
        <p className="label-caps mt-1">{active ? "Round live" : "Awaiting start"}</p>
      </div>

      <div className="p-5 flex flex-col gap-3">
        {active ? (
          <>
            <button onClick={() => hostCommand("FORCE_SUBMIT")} className="btn btn-primary w-full">
              Force submit
            </button>
            <button onClick={() => extendTimer(30)} className="btn btn-secondary w-full">
              +30 seconds
            </button>
          </>
        ) : (
          <button onClick={() => hostCommand("NEXT_QUESTION")} className="btn btn-primary w-full">
            {status === "LOBBY" ? "Start round" : "Next question"}
          </button>
        )}
        <button onClick={() => hostCommand("END_GAME")} className="btn btn-danger w-full">
          End game
        </button>
      </div>

      <div className="px-5 pb-5 pt-4 border-t border-line">
        <p className="label-caps mb-3">{players.length} players</p>
        <ul className="flex flex-col gap-1 max-h-48 overflow-y-auto">
          {players.map((p) => (
            <li key={p.uuid} className="flex items-center justify-between text-sm py-1.5 px-3 rounded-lg hover:bg-row-alt transition-colors">
              <span className="font-medium">{p.name}</span>
              <span className="mono text-xs text-muted">{p.score}</span>
            </li>
          ))}
          {players.length === 0 && <li className="text-muted text-sm">Share the PIN to invite players.</li>}
        </ul>
      </div>
    </div>
  );
}
