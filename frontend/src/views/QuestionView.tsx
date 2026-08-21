import { useState } from "react";
import { useGameStore } from "../stores/useGameStore";
import { QuestionRendererHost } from "../components/QuestionRendererHost";
import { CircularTimer } from "../components/Timer/CircularTimer";
import { isCoding } from "../services/ScoringService";
import { QuestionDto } from "../types";

export function QuestionView() {
  const q = useGameStore((s) => s.currentQuestion) as QuestionDto | null;
  const end = useGameStore((s) => s.questionEndEpochMs);
  const status = useGameStore((s) => s.status);
  const submit = useGameStore((s) => s.submit);
  const [response, setResponse] = useState<unknown>(null);
  const [submitted, setSubmitted] = useState(false);

  if (status === "REVIEW") {
    return (
      <div className="min-h-screen flex items-center justify-center p-4">
        <div className="card text-center">
          <p className="text-muted">Round over — waiting for the host…</p>
        </div>
      </div>
    );
  }

  if (!q || !end) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p className="text-muted">Waiting for the host to start the next question…</p>
      </div>
    );
  }

  const doSubmit = () => {
    submit(q.id, response, isCoding(q.type) ? (response as { language?: string })?.language : undefined);
    setSubmitted(true);
    try { localStorage.removeItem(`openquiz_code_${q.id}`); } catch { /* ignore */ }
  };

  return (
    <div className="min-h-screen flex flex-col items-center p-4">
      <div className="card w-full max-w-2xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="text-xs uppercase tracking-wide text-muted">{q.type.replace("_", " ")}</span>
            <h2 className="text-xl font-bold mt-1">{q.title}</h2>
            {q.description && <p className="text-muted mt-1 whitespace-pre-wrap">{q.description}</p>}
          </div>
          <CircularTimer endEpochMs={end} totalSec={q.timeLimitSec} onExpire={() => !submitted && doSubmit()} />
        </div>

        <QuestionRendererHost question={q} onResponse={setResponse} />

        <button
          onClick={doSubmit}
          disabled={submitted}
          className="btn btn-primary w-full mt-6 disabled:opacity-50"
        >
          {submitted ? "Submitted" : isCoding(q.type) ? "Run & submit" : "Submit answer"}
        </button>
      </div>
    </div>
  );
}
