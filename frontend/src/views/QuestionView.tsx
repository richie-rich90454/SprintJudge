import { useEffect, useState } from "react";
import { useGameStore } from "../stores/useGameStore";
import { useTimerStore } from "../stores/useTimerStore";
import { QuestionRendererHost } from "../components/QuestionRendererHost";
import { CircularTimer } from "../components/Timer/CircularTimer";
import { isCoding } from "../services/ScoringService";
import { useEnter, useStaggerIn } from "../hooks/useMotion";
import { QuestionDto } from "../types";

const idle = (cb: () => void): void => {
  const w = window as unknown as { requestIdleCallback?: (cb: () => void) => number };
  if (typeof w.requestIdleCallback === "function") w.requestIdleCallback(cb);
  else setTimeout(cb, 50);
};

export function QuestionView() {
  const q = useGameStore((s) => s.currentQuestion) as QuestionDto | null;
  const status = useGameStore((s) => s.status);
  const submit = useGameStore((s) => s.submit);
  const end = useTimerStore((s) => s.endEpochMs);
  const [response, setResponse] = useState<unknown>(null);
  const [submitted, setSubmitted] = useState(false);

  const cardRef = useEnter<HTMLDivElement>("card", [q?.id]);
  const optionsRef = useStaggerIn<HTMLDivElement>(".renderer-host button", [q?.id], 0.05);
  const barRef = useEnter<HTMLButtonElement>("bar", [q?.id]);

  useEffect(() => {
    if (!q || !isCoding(q.type)) return;
    idle(() => { import("monaco-editor").catch(() => { /* textarea fallback */ }); });
  }, [q?.id]);

  if (status === "REVIEW") {
    return (
      <div className="pattern-exam min-h-screen flex items-center justify-center p-4">
        <div className="card text-center max-w-md w-full">
          <p className="label-caps mb-2">Round complete</p>
          <h2 className="text-2xl font-extrabold">Answers locked.</h2>
          <p className="text-muted mt-2">The host is preparing the next round…</p>
        </div>
      </div>
    );
  }

  if (!q || !end) {
    return (
      <div className="pattern-exam min-h-screen flex items-center justify-center">
        <div className="text-center">
          <p className="label-caps mb-2">Standby</p>
          <p className="text-muted">Waiting for the host to start the next question…</p>
        </div>
      </div>
    );
  }

  const doSubmit = () => {
    submit(q.id, response, isCoding(q.type) ? (response as { language?: string })?.language : undefined);
    setSubmitted(true);
    try { localStorage.removeItem(`sprintjudge_code_${q.id}`); } catch { /* ignore */ }
  };

  return (
    <div className="pattern-exam min-h-screen flex flex-col items-center p-4 md:p-8">
      <div className="page-shell w-full max-w-3xl">
        {/* Exam-paper header strip */}
        <div className="flex items-end justify-between border-b-2 pb-3" style={{ borderColor: "#C8102E" }}>
          <span className="label-caps">{q.type.replace(/_/g, " ")}</span>
          <span className="mono text-sm text-muted">{q.pointsBase} pts</span>
        </div>

        <div ref={cardRef} className="card mt-6 relative pb-8">
          <div className="absolute -top-4 -right-4 md:-top-6 md:-right-6">
            <CircularTimer endEpochMs={end} totalSec={q.timeLimitSec} onExpire={() => !submitted && doSubmit()} />
          </div>

          <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight pr-24 md:pr-28">{q.title}</h1>
          {q.description && (
            <p className="text-muted mt-3 whitespace-pre-wrap leading-relaxed pr-16">{q.description}</p>
          )}
          <div className="h-px bg-line my-5" />

          <div ref={optionsRef}>
            <QuestionRendererHost question={q} onResponse={setResponse} />
          </div>

          <button
            ref={barRef}
            onClick={doSubmit}
            disabled={submitted}
            className="btn btn-primary w-full mt-8 text-base disabled:opacity-40"
          >
            {submitted ? "✓ Answer locked in" : isCoding(q.type) ? "Compile & submit" : "Lock in answer"}
          </button>
        </div>
      </div>
    </div>
  );
}
