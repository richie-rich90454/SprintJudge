import { useEffect, useState } from "react";
import { useAdminStore } from "../stores/useAdminStore";
import { useUIStore } from "../stores/useUIStore";
import axios from "axios";
import { adminApi } from "../services/AdminApiService";
import { QuestionWizard } from "./QuestionWizard";
import { useStaggerIn } from "../hooks/useMotion";

export function AdminDashboard() {
  const { quizzes, questions, activeQuizId, loadQuizzes, loadQuestions, openWizard, createQuiz } = useAdminStore();
  const wizardOpen = useAdminStore((s) => s.wizardOpen);
  const setView = useUIStore((s) => s.setView);
  const setPin = useUIStore((s) => s.setPin);
  const [title, setTitle] = useState("");
  const [desc, setDesc] = useState("");
  const [busy, setBusy] = useState(false);
  const [needsAuth, setNeedsAuth] = useState(false);

  const gridRef = useStaggerIn<HTMLDivElement>(".card", [quizzes.length], 0.06);

  useEffect(() => {
    loadQuizzes().catch((e: unknown) => {
      if (axios.isAxiosError(e) && e.response?.status === 401) setNeedsAuth(true);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (needsAuth) {
    return (
      <div className="pattern-exam min-h-screen flex items-center justify-center p-4">
        <div className="card text-center max-w-sm w-full">
          <p className="label-caps mb-2">Authentication required</p>
          <h2 className="text-2xl font-extrabold mb-4">Admin sign-in</h2>
          <p className="text-muted mb-6">Sign in with your Microsoft account to access the admin panel.</p>
          <a href="/oauth2/authorization/microsoft" className="btn btn-primary w-full no-underline">
            Sign in with Microsoft
          </a>
        </div>
      </div>
    );
  }

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
    a.download = "sprintjudge-bank.json";
    a.click();
  };

  const doImport = async (file: File) => {
    const json = await file.text();
    await adminApi.importBank(json, true);
    await loadQuizzes();
  };

  return (
    <div className="min-h-screen p-6 max-w-content mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Admin · Question bank</h1>
        <div className="flex gap-2">
          <button onClick={() => setView("join")} className="btn btn-secondary text-sm">Player view</button>
          <button onClick={doExport} className="btn btn-secondary text-sm">Export</button>
          <label className="btn btn-secondary text-sm cursor-pointer">
            Import
            <input type="file" accept="application/json" className="hidden" onChange={(e) => e.target.files?.[0] && doImport(e.target.files[0])} />
          </label>
        </div>
      </div>

      <div className="card mb-6">
        <h2 className="header-double mb-3">Create quiz</h2>
        <div className="flex flex-col sm:flex-row gap-2">
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Quiz title" className="input-underline flex-1" />
          <input value={desc} onChange={(e) => setDesc(e.target.value)} placeholder="Description" className="input-underline flex-1" />
          <button onClick={async () => { if (title.trim()) { await createQuiz(title.trim(), desc.trim()); setTitle(""); setDesc(""); } }}
            className="btn btn-primary">Add</button>
        </div>
      </div>

      <div ref={gridRef} className="grid sm:grid-cols-2 gap-4">
        {quizzes.map((q) => (
          <div key={q.id} className="card">
            <h3 className="font-bold">{q.title}</h3>
            <p className="text-muted text-sm mb-3 line-clamp-2">{q.description}</p>
            <div className="flex gap-2 flex-wrap">
              <button onClick={() => loadQuestions(q.id)} className="btn btn-secondary text-sm">Open</button>
              <button onClick={() => host(q.id)} disabled={busy} className="btn btn-primary text-sm disabled:opacity-50">Host</button>
              <button onClick={() => openWizard(q.id)} className="btn btn-secondary text-sm">+ Question</button>
            </div>
            {activeQuizId === q.id && (
              <ul className="mt-3 flex flex-col gap-1">
                {questions.map((qn, i) => (
                  <li key={qn.id} className="text-sm flex justify-between border-b border-line py-1">
                    <span>{i + 1}. {qn.title}</span><span className="text-muted">{qn.questionType}</span>
                  </li>
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
