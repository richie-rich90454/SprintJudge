import { useEffect, useState } from "react";
import { useAdminStore } from "../stores/useAdminStore";
import { useUIStore } from "../stores/useUIStore";
import { adminApi } from "../services/AdminApiService";
import { QuestionWizard } from "./QuestionWizard";

export function AdminDashboard() {
  const { quizzes, questions, activeQuizId, loadQuizzes, loadQuestions, openWizard, createQuiz } = useAdminStore();
  const wizardOpen = useAdminStore((s) => s.wizardOpen);
  const setView = useUIStore((s) => s.setView);
  const setPin = useUIStore((s) => s.setPin);
  const [title, setTitle] = useState("");
  const [desc, setDesc] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    loadQuizzes();
  }, []);

  const host = async (quizId: string) => {
    setBusy(true);
    try {
      const game = await adminApi.createGame(quizId);
      setPin(game.pinCode);
      setView("host");
    } finally {
      setBusy(false);
    }
  };

  const doExport = async () => {
    const json = await adminApi.exportBank();
    const blob = new Blob([json], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = "openquiz-bank.json";
    a.click();
  };

  const doImport = async (file: File) => {
    const json = await file.text();
    await adminApi.importBank(json, true);
    await loadQuizzes();
  };

  return (
    <div className="min-h-screen p-6 max-w-4xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Admin · Question bank</h1>
        <div className="flex gap-2">
          <button onClick={() => setView("join")} className="min-h-tap px-4 rounded-lg border border-border bg-surface hover:border-primary text-sm">Player view</button>
          <button onClick={doExport} className="min-h-tap px-4 rounded-lg border border-border bg-surface hover:border-primary text-sm">Export</button>
          <label className="min-h-tap px-4 rounded-lg border border-border bg-surface hover:border-primary text-sm cursor-pointer">
            Import
            <input type="file" accept="application/json" className="hidden" onChange={(e) => e.target.files?.[0] && doImport(e.target.files[0])} />
          </label>
        </div>
      </div>

      <div className="bg-surface shadow-card rounded-xl border border-border p-4 mb-6">
        <h2 className="font-semibold mb-3">Create quiz</h2>
        <div className="flex flex-col sm:flex-row gap-2">
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Quiz title" className="flex-1 min-h-tap px-4 rounded-lg border border-border bg-surface" />
          <input value={desc} onChange={(e) => setDesc(e.target.value)} placeholder="Description" className="flex-1 min-h-tap px-4 rounded-lg border border-border bg-surface" />
          <button onClick={async () => { if (title.trim()) { await createQuiz(title.trim(), desc.trim()); setTitle(""); setDesc(""); } }}
            className="min-h-tap px-4 rounded-lg bg-primary text-white font-medium hover:bg-primary-dark">Add</button>
        </div>
      </div>

      <div className="grid sm:grid-cols-2 gap-4">
        {quizzes.map((q) => (
          <div key={q.id} className="bg-surface shadow-card rounded-xl border border-border p-4">
            <h3 className="font-semibold">{q.title}</h3>
            <p className="text-muted text-sm mb-3 line-clamp-2">{q.description}</p>
            <div className="flex gap-2 flex-wrap">
              <button onClick={() => loadQuestions(q.id)} className="min-h-tap px-3 text-sm rounded-lg border border-border bg-surface hover:border-primary">Open</button>
              <button onClick={() => host(q.id)} disabled={busy} className="min-h-tap px-3 text-sm rounded-lg bg-primary text-white hover:bg-primary-dark disabled:opacity-50">Host</button>
              <button onClick={() => openWizard(q.id)} className="min-h-tap px-3 text-sm rounded-lg border border-border bg-surface hover:border-primary">+ Question</button>
            </div>
            {activeQuizId === q.id && (
              <ul className="mt-3 flex flex-col gap-1">
                {questions.map((qn, i) => (
                  <li key={qn.id} className="text-sm flex justify-between"><span>{i + 1}. {qn.title}</span><span className="text-muted">{qn.questionType}</span></li>
                ))}
                {questions.length === 0 && <li className="text-sm text-muted">No questions yet.</li>}
              </ul>
            )}
          </div>
        ))}
        {quizzes.length === 0 && <p className="text-muted">No quizzes. Create one above.</p>}
      </div>

      {wizardOpen && <QuestionWizard />}
    </div>
  );
}
