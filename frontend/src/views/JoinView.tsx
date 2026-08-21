import { useState } from "react";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";

export function JoinView() {
  const [pin, setPin] = useState("");
  const [name, setName] = useState("");
  const join = useGameStore((s) => s.join);
  const connect = useGameStore((s) => s.connect);
  const error = useGameStore((s) => s.error);
  const setView = useUIStore((s) => s.setView);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!pin.trim() || !name.trim()) return;
    const proto = location.protocol === "https:" ? "wss" : "ws";
    connect(`${proto}://${location.host}/ws`);
    join(pin.trim(), name.trim());
    setView("play");
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <form onSubmit={submit} className="card w-full max-w-sm">
        <h1 className="text-2xl font-bold mb-1">OpenQuiz</h1>
        <p className="text-muted mb-6">Enter the game PIN to join.</p>

        <label className="block text-sm text-muted mb-1">Your nickname</label>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="input-underline mb-6"
          placeholder="Alice"
          maxLength={20}
        />

        <label className="block text-sm text-muted mb-1">6-digit PIN</label>
        <input
          value={pin}
          onChange={(e) => setPin(e.target.value)}
          inputMode="numeric"
          className="input-underline mb-6 mono text-center text-lg tracking-widest"
          placeholder="123456"
        />

        {error && <p className="text-danger text-sm mb-3">{error}</p>}

        <button type="submit" className="btn btn-primary w-full">Join game</button>
        <button type="button" onClick={() => setView("admin")} className="btn btn-secondary w-full mt-3">
          I'm a host / admin
        </button>
      </form>
    </div>
  );
}
