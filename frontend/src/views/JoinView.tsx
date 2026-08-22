import { useEffect, useRef, useState } from "react";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { useEnter, useStaggerIn } from "../hooks/useMotion";
import { motion } from "../services/MotionService";

/**
 * Full-bleed immersive join: Swiss poster composition on a ruled exam-paper
 * backdrop — oversized display type, giant mono PIN field, red accent rules.
 */
export function JoinView() {
  const [pin, setPin] = useState("");
  const [name, setName] = useState("");
  const join = useGameStore((s) => s.join);
  const connect = useGameStore((s) => s.connect);
  const error = useGameStore((s) => s.error);
  const setView = useUIStore((s) => s.setView);

  const cardRef = useEnter<HTMLFormElement>("card");
  const fieldsRef = useStaggerIn<HTMLUListElement>(".input-underline");

  const pinRef = useRef<HTMLInputElement>(null);
  useEffect(() => { if (pin.length === 6) pinRef.current?.blur(); }, [pin]);

  useEffect(() => {
    if (error) motion.shake(cardRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
      {/* Masthead: brand rule + wordmark, poster style */}
      <header className="page-shell w-full pt-8 pb-4 flex items-center justify-between border-b-2" style={{ borderColor: "#C8102E" }}>
        <div className="flex items-center gap-3">
          <img src="/favicon.svg" alt="" width={34} height={34} />
          <span className="text-xl font-extrabold tracking-tight">SprintJudge</span>
        </div>
        <button onClick={() => setView("admin")} className="btn btn-secondary text-sm">Host / Admin</button>
      </header>

      <main className="page-shell flex-1 w-full grid lg:grid-cols-[1.2fr_1fr] gap-12 items-center py-10">
        {/* Poster column */}
        <section>
          <p className="label-caps mb-3">Real-time code quiz arena</p>
          <h1 className="font-extrabold leading-[0.95] tracking-tight"
              style={{ fontSize: "clamp(44px, 7vw, 92px)" }}>
            Answer fast.<br />
            Code faster.<br />
            <span style={{ color: "#C8102E" }}>Win the room.</span>
          </h1>
          <div className="mt-6 h-[3px] w-24" style={{ background: "#C8102E" }} />
          <ul ref={fieldsRef} className="mt-8 space-y-2 text-muted max-w-md">
            <li className="border-b border-dotted border-line pb-2">Live host-driven rounds with countdown timers</li>
            <li className="border-b border-dotted border-line pb-2">Real compilation against hidden test cases</li>
            <li>C, C++, Java, JavaScript &amp; Python — judged instantly</li>
          </ul>
        </section>

        {/* Join card */}
        <form ref={cardRef} onSubmit={submit} className="card w-full max-w-md justify-self-center lg:justify-self-end w-full">
          <p className="label-caps mb-1">Enter the room</p>
          <h2 className="text-2xl font-extrabold mb-6">Join a game</h2>

          <label className="block text-sm font-semibold mb-1" htmlFor="jq-nick">Nickname</label>
          <input
            id="jq-nick"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="input-underline mb-5"
            placeholder="Your name"
            maxLength={20}
          />

          <label className="block text-sm font-semibold mb-1" htmlFor="jq-pin">Game PIN</label>
          <input
            id="jq-pin"
            ref={pinRef}
            value={pin}
            onChange={(e) => setPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
            inputMode="numeric"
            autoComplete="off"
            className="input-underline mb-2 mono text-center"
            style={{ fontSize: "clamp(26px, 4vw, 38px)", letterSpacing: "0.28em", fontWeight: 700 }}
            placeholder="000000"
          />
          <p className="label-caps mb-5" style={{ letterSpacing: "0.35em" }}>
            {[0, 1, 2, 3, 4, 5].map((i) => (
              <span key={i} className="inline-block w-[1ch]" style={{ opacity: i < pin.length ? 1 : 0.25 }}>
                {i < pin.length ? "•" : "·"}
              </span>
            ))}
          </p>

          {error && <p className="text-danger text-sm mb-3">{error}</p>}

          <button type="submit" disabled={pin.length !== 6 || !name.trim()}
                  className="btn btn-primary w-full disabled:opacity-40">
            Enter the arena →
          </button>
        </form>
      </main>

      <footer className="page-shell w-full py-4 border-t border-dotted border-line text-xs text-muted flex justify-between">
        <span>SprintJudge — self-hosted quiz + online judge</span>
        <span className="mono">GPLv3</span>
      </footer>
    </div>
  );
}
