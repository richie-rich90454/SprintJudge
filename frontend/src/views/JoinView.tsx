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
      <form onSubmit={submit} className="w-full max-w-sm bg-surface shadow-card rounded-xl p-6 border border-border">
        <h1 className="text-2xl font-bold mb-1">OpenQuiz</h1>
        <p className="text-muted mb-6">Enter the game PIN to join.</p>
        <label className="block text-sm text-muted mb-1">Your nickname</label>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="w-full min-h-tap px-4 mb-4 rounded-lg border border-border bg-surface"
          placeholder="Alice"
        />
        <label className="block text-sm text-muted mb-1">6-digit PIN</label>
        <input
          value={pin}
          onChange={(e) => setPin(e.target.value)}
          inputMode="numeric"
          className="w-full min-h-tap px-4 mb-4 rounded-lg border border-border bg-surface font-mono tracking-widest text-center text-lg"
          placeholder="123456"
        />
        {error && <p className="text-danger text-sm mb-3">{error}</p>}
        <button type="submit" className="w-full min-h-tap rounded-lg bg-primary text-white font-medium hover:bg-primary-dark">
          Join game
        </button>
        <button type="button" onClick={() => setView("admin")} className="w-full min-h-tap mt-3 text-primary text-sm">
          I'm a host / admin
        </button>
      </form>
    </div>
  );
}
