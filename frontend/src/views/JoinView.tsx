import { useEffect, useState } from "react";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { useEnter, useStaggerIn } from "../hooks/useMotion";
import { LogoMark } from "../components/LogoMark";

export function JoinView() {
  const [pin, setPin] = useState("");
  const [name, setName] = useState("");
  const join = useGameStore((s) => s.join);
  const connect = useGameStore((s) => s.connect);
  const error = useGameStore((s) => s.error);
  const setView = useUIStore((s) => s.setView);

  const cardRef = useEnter<HTMLFormElement>("card");
  const fieldsRef = useStaggerIn<HTMLDivElement>(".input-underline");

  useEffect(() => {
    if (error) cardRef.current?.classList.add("animate-pulse");
  }, [error]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!pin.trim() || !name.trim()) return;
    const proto = location.protocol === "https:" ? "wss" : "ws";
    connect(`${proto}://${location.host}/ws`);
    join(pin.trim(), name.trim());
    setView("play");
  };

  return (
    <div className="pattern-exam min-h-screen flex flex-col">
      {/* Slim top bar */}
      <header className="border-b border-line">
        <div className="page-shell py-3 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <LogoMark size={28} />
            <span className="font-extrabold tracking-tight">SprintJudge</span>
          </div>
          <button onClick={() => setView("admin")} className="btn btn-secondary btn-sm">Host / Admin</button>
        </div>
      </header>

      {/* Main: giant PIN entry */}
      <main className="flex-1 flex items-center justify-center p-6">
        <form ref={cardRef} onSubmit={submit} className="w-full max-w-lg text-center">
          <p className="label-caps mb-4">Join a live game</p>
          <h1 className="font-extrabold tracking-tight leading-none mb-10"
              style={{ fontSize: "clamp(40px, 8vw, 88px)" }}>
            Sprint<span style={{ color: "var(--oq-red)" }}>Judge</span>
          </h1>

          <div ref={fieldsRef} className="space-y-6 max-w-sm mx-auto">
            <div>
              <label htmlFor="jq-nick" className="label-caps block mb-2 text-left">Your name</label>
              <input
                id="jq-nick"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="input-underline w-full"
                placeholder="Alice"
                maxLength={20}
              />
            </div>
            <div>
              <label htmlFor="jq-pin" className="label-caps block mb-2 text-left">Game PIN</label>
              <input
                id="jq-pin"
                value={pin}
                onChange={(e) => setPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
                inputMode="numeric"
                autoComplete="off"
                className="input-underline w-full mono text-center font-bold"
                style={{ fontSize: "clamp(28px,5vw,42px)", letterSpacing: ".25em", lineHeight: 1.2 }}
                placeholder="000000"
              />
            </div>
          </div>

          {error && <p className="text-danger text-sm mt-4">{error}</p>}

          <button type="submit"
                  disabled={pin.length !== 6 || !name.trim()}
                  className="btn btn-primary w-full max-w-sm mx-auto mt-8 text-base disabled:opacity-30">
            Let's go →
          </button>
        </form>
      </main>

      <footer className="border-t border-line py-3">
        <div className="page-shell flex justify-between text-xs text-muted">
          <span>GPLv3</span>
          <span>Self-hosted · Real-time code judge</span>
        </div>
      </footer>
    </div>
  );
}
