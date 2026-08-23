import axios from "axios";
import { useEffect, useState } from "react";
import { useAdminStore } from "../stores/useAdminStore";
import { useUIStore } from "../stores/useUIStore";
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
  const [showCreate, setShowCreate] = useState(false);

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
          <a href="/oauth2/authorization/ms-callback" className="btn btn-primary w-full no-underline">
            Sign in with Microsoft
          </a>
        </div>
      </div>
    );
  }

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
    <div className="pattern-exam min-h-screen pb-12">
      {/* Toolbar */}
      <header className="border-b bg-surface" style={{ borderColor: "var(--oq-border)" }}>
        <div className="page-shell py-4 flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-3 mr-auto">
            <h1 className="text-xl font-extrabold tracking-tight">Admin</h1>
            <span className="chip chip-neutral">{quizzes.length} sets</span>
          </div>
          <button onClick={() => setView("join")} className="btn btn-secondary btn-sm">Player view</button>
          <button onClick={doExport} className="btn btn-secondary btn-sm">Export</button>
          <label className="btn btn-secondary btn-sm cursor-pointer">
            Import
            <input type="file" accept="application/json" className="hidden" onChange={(e) => e.target.files?.[0] && doImport(e.target.files[0])} />
          </label>
          <button onClick={() => setShowCreate(true)} className="btn btn-primary btn-sm">+ New set</button>
        </div>
      </header>

      {/* Create new quiz (inline, not a modal) */}
      {showCreate && (
        <div className="page-shell mt-6">
          <div className="card">
            <h3 className="header-double">Create question set</h3>
            <div className="flex flex-col sm:flex-row gap-3">
              <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Title (e.g. Java · Objects — Foundations 01)" className="input-underline flex-1" />
              <input value={desc} onChange={(e) => setDesc(e.target.value)} placeholder="Description (optional)" className="input-underline flex-1" />
              <button
                onClick={async () => { if (title.trim()) { await createQuiz(title.trim(), desc.trim()); setTitle(""); setDesc(""); setShowCreate(false); } }}
                className="btn btn-primary"
              >
                Create
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Quiz grid */}
      <main className="page-shell mt-6">
        <div ref={gridRef} className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {quizzes.map((q) => (
            <div key={q.id} className="card flex flex-col gap-3">
              <div className="flex items-start justify-between gap-2">
                <h3 className="font-bold text-base leading-snug">{q.title}</h3>
              </div>
              {q.description && <p className="text-muted text-sm line-clamp-2 flex-1">{q.description}</p>}
              <div className="flex flex-wrap gap-2 pt-2 border-t border-line">
                <button onClick={() => loadQuestions(q.id)} className="btn btn-secondary btn-sm">Questions</button>
                <button onClick={() => host(q.id)} disabled={busy} className="btn btn-primary btn-sm disabled:opacity-40">
                  Host
                </button>
                <button onClick={() => openWizard(q.id)} className="btn btn-secondary btn-sm">+ Add</button>
              </div>
              {activeQuizId === q.id && (
                <ul className="mt-2 flex flex-col gap-1 max-h-40 overflow-y-auto text-sm">
                  {questions.map((qn, i) => (
                    <li key={qn.id} className="flex justify-between items-center py-1 px-2 rounded hover:bg-row-alt transition-colors">
                      <span className="truncate">{i + 1}. {qn.title}</span>
                      <span className="chip chip-neutral !text-[10px] !py-0.5 !px-2">{qn.questionType}</span>
                    </li>
                  ))}
                  {questions.length === 0 && <li className="text-muted text-sm">No questions yet.</li>}
                </ul>
              )}
            </div>
          ))}
          {quizzes.length === 0 && (
            <div className="card col-span-full text-center py-16">
              <p className="label-caps mb-2">Empty library</p>
              <p className="text-muted">Create your first question set above, or import a bank JSON.</p>
            </div>
          )}
        </div>
      </main>

      {wizardOpen && <QuestionWizard />}
    </div>
  );
}
