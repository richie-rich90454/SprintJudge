import { useAdminStore, WizardStep } from "../stores/useAdminStore";
import { ALL_QUESTION_TYPES, QuestionType } from "../types";
import { QuestionRendererHost } from "../components/QuestionRendererHost";
import { QuestionDto } from "../types";

const STEPS: WizardStep[] = ["type", "statement", "config", "preview"];

function ConfigForm() {
  const draft = useAdminStore((s) => s.draft);
  const setDraft = useAdminStore((s) => s.setDraft);
  const type = draft.questionType as QuestionType;
  const config = (draft.config as Record<string, unknown>) ?? {};
  const set = (patch: Record<string, unknown>) => setDraft({ config: { ...config, ...patch } });

  switch (type) {
    case "MCQ":
    case "OUTPUT_PRED":
    case "COMPLEXITY": {
      const options = (config["options"] as string[]) ?? ["", "", "", ""];
      return (
        <div className="flex flex-col gap-2">
          {type === "OUTPUT_PRED" && <textarea placeholder="Code snippet" value={(config["code"] as string) ?? ""} onChange={(e) => set({ code: e.target.value })} className="mono w-full min-h-[100px] p-3 rounded-lg border border-border bg-surface text-sm" />}
          {options.map((o, i) => (
            <input key={i} value={o} placeholder={`Option ${String.fromCharCode(65 + i)}`} onChange={(e) => { const n = [...options]; n[i] = e.target.value; set({ options: n }); }} className="min-h-tap px-4 rounded-lg border border-border bg-surface" />
          ))}
          <label className="text-sm text-muted mt-2">Correct option index</label>
          <input type="number" min={0} value={(config["correctIndex"] as number) ?? 0} onChange={(e) => set({ correctIndex: Number(e.target.value) })} className="min-h-tap px-4 rounded-lg border border-border bg-surface w-24" />
        </div>
      );
    }
    case "TRUE_FALSE":
      return (
        <label className="text-sm text-muted">Correct answer
          <select value={String(config["correct"] ?? "true")} onChange={(e) => set({ correct: e.target.value === "true" })} className="min-h-tap px-3 ml-2 rounded-lg border border-border bg-surface">
            <option value="true">True</option><option value="false">False</option>
          </select>
        </label>
      );
    case "MULTIPLE_SELECT": {
      const options = (config["options"] as string[]) ?? ["", "", "", ""];
      return (
        <div className="flex flex-col gap-2">
          {options.map((o, i) => (
            <input key={i} value={o} placeholder={`Option ${String.fromCharCode(65 + i)}`} onChange={(e) => { const n = [...options]; n[i] = e.target.value; set({ options: n }); }} className="min-h-tap px-4 rounded-lg border border-border bg-surface" />
          ))}
          <label className="text-sm text-muted mt-2">Correct indices (comma sep)</label>
          <input value={((config["correctIndices"] as number[]) ?? []).join(",")} onChange={(e) => set({ correctIndices: e.target.value.split(",").map((x) => Number(x.trim())).filter((n) => !Number.isNaN(n)) })} className="min-h-tap px-4 rounded-lg border border-border bg-surface" />
        </div>
      );
    }
    case "NUMERIC":
      return (
        <div className="flex gap-2 flex-wrap">
          <label className="text-sm">Answer<input type="number" value={(config["answer"] as number) ?? 0} onChange={(e) => set({ answer: Number(e.target.value) })} className="min-h-tap px-3 ml-1 rounded-lg border border-border bg-surface" /></label>
          <label className="text-sm">Tolerance<input type="number" step="0.01" value={(config["tolerance"] as number) ?? 0} onChange={(e) => set({ tolerance: Number(e.target.value) })} className="min-h-tap px-3 ml-1 rounded-lg border border-border bg-surface" /></label>
        </div>
      );
    case "FILL_BLANK":
      return (
        <div className="flex flex-col gap-2">
          <textarea placeholder="Snippet (use ___ for the blank)" value={(config["snippet"] as string) ?? ""} onChange={(e) => set({ snippet: e.target.value })} className="mono w-full min-h-[100px] p-3 rounded-lg border border-border bg-surface text-sm" />
          <input placeholder="Correct answer" value={(config["answer"] as string) ?? ""} onChange={(e) => set({ answer: e.target.value })} className="min-h-tap px-4 rounded-lg border border-border bg-surface" />
        </div>
      );
    case "DRAG_SORT": {
      const lines = (config["lines"] as { id: string; text: string }[]) ?? [];
      return (
        <div className="flex flex-col gap-2">
          {lines.map((l, i) => (
            <input key={i} value={l.text} placeholder={`Line ${i + 1}`} onChange={(e) => { const n = [...lines]; n[i] = { ...l, text: e.target.value }; set({ lines: n }); }} className="min-h-tap px-4 rounded-lg border border-border bg-surface" />
          ))}
          <button onClick={() => set({ lines: [...lines, { id: String(lines.length), text: "" }] })} className="text-primary text-sm self-start">+ Add line</button>
        </div>
      );
    }
    case "CLICK_BUG": {
      const codeLines = (config["codeLines"] as string[]) ?? [];
      return (
        <div className="flex flex-col gap-2">
          <textarea placeholder="One buggy line per row" value={codeLines.join("\n")} onChange={(e) => set({ codeLines: e.target.value.split("\n") })} className="mono w-full min-h-[120px] p-3 rounded-lg border border-border bg-surface text-sm" />
          <label className="text-sm text-muted">Bug line index (0-based)</label>
          <input type="number" value={(config["bugLine"] as number) ?? 0} onChange={(e) => set({ bugLine: Number(e.target.value) })} className="min-h-tap px-4 rounded-lg border border-border bg-surface w-24" />
        </div>
      );
    }
    case "CODE_COMPLETION":
      return (
        <textarea placeholder="Skeleton / starter code (editable region)" value={(config["skeleton"] as string) ?? ""} onChange={(e) => set({ skeleton: e.target.value })} className="mono w-full min-h-[140px] p-3 rounded-lg border border-border bg-surface text-sm" />
      );
    case "OJ_FULL":
    case "OJ_PATCH": {
      const tc = (config["testCases"] as { input: string; expectedOutput: string; isHidden: boolean }[]) ?? [];
      return (
        <div className="flex flex-col gap-2">
          {type === "OJ_PATCH" && <textarea placeholder="Buggy function" value={(config["buggyFunction"] as string) ?? ""} onChange={(e) => set({ buggyFunction: e.target.value })} className="mono w-full min-h-[120px] p-3 rounded-lg border border-border bg-surface text-sm" />}
          {type === "OJ_FULL" && <textarea placeholder="Starter code" value={(config["starter"] as string) ?? ""} onChange={(e) => set({ starter: e.target.value })} className="mono w-full min-h-[120px] p-3 rounded-lg border border-border bg-surface text-sm" />}
          <p className="text-sm text-muted mt-1">Test cases</p>
          {tc.map((c, i) => (
            <div key={i} className="flex gap-2">
              <input placeholder="input" value={c.input} onChange={(e) => { const n = [...tc]; n[i] = { ...c, input: e.target.value }; set({ testCases: n }); }} className="min-h-tap px-3 flex-1 rounded-lg border border-border bg-surface text-sm" />
              <input placeholder="expected" value={c.expectedOutput} onChange={(e) => { const n = [...tc]; n[i] = { ...c, expectedOutput: e.target.value }; set({ testCases: n }); }} className="min-h-tap px-3 flex-1 rounded-lg border border-border bg-surface text-sm" />
            </div>
          ))}
          <button onClick={() => set({ testCases: [...tc, { input: "", expectedOutput: "", isHidden: false }] })} className="text-primary text-sm self-start">+ Add test case</button>
        </div>
      );
    }
    default:
      return <p className="text-muted">No config editor for this type.</p>;
  }
}

export function QuestionWizard() {
  const { wizardStep, wizardType, draft, setStep, setType, setDraft, saveQuestion, closeWizard } = useAdminStore();
  const previewQuestion: QuestionDto = {
    id: "preview",
    type: (draft.questionType as QuestionType) ?? "MCQ",
    title: draft.title ?? "Preview question",
    description: draft.description ?? "",
    timeLimitSec: draft.timeLimitSec ?? 30,
    pointsBase: draft.pointsBase ?? 100,
    languagesAllowed: (draft.languagesAllowed as string[]) ?? null,
    config: draft.config ?? {},
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
      <div className="w-full max-w-2xl bg-surface shadow-card rounded-xl border border-border p-6 max-h-[90vh] overflow-auto">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-semibold">Question wizard</h2>
          <button onClick={closeWizard} className="text-muted">Close</button>
        </div>

        <div className="flex gap-2 mb-5">
          {STEPS.map((s) => (
            <button key={s} onClick={() => setStep(s)} className={`min-h-tap px-3 rounded-lg border text-sm capitalize ${wizardStep === s ? "border-primary bg-surface-alt" : "border-border bg-surface"}`}>{s}</button>
          ))}
        </div>

        {wizardStep === "type" && (
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            {ALL_QUESTION_TYPES.map((t) => (
              <button key={t} onClick={() => { setType(t); setStep("statement"); }} className={`min-h-tap px-3 py-4 rounded-lg border text-sm ${wizardType === t ? "border-primary bg-surface-alt" : "border-border bg-surface"}`}>{t.replace("_", " ")}</button>
            ))}
          </div>
        )}

        {wizardStep === "statement" && (
          <div className="flex flex-col gap-3">
            <input placeholder="Title" value={draft.title ?? ""} onChange={(e) => setDraft({ title: e.target.value })} className="min-h-tap px-4 rounded-lg border border-border bg-surface" />
            <textarea placeholder="Description (Markdown)" value={draft.description ?? ""} onChange={(e) => setDraft({ description: e.target.value })} className="min-h-[80px] p-3 rounded-lg border border-border bg-surface" />
            <div className="flex gap-3">
              <label className="text-sm flex-1">Time limit (s)
                <input type="number" value={draft.timeLimitSec ?? 30} onChange={(e) => setDraft({ timeLimitSec: Number(e.target.value) })} className="min-h-tap px-3 w-full rounded-lg border border-border bg-surface mt-1" />
              </label>
              <label className="text-sm flex-1">Base points
                <input type="number" value={draft.pointsBase ?? 100} onChange={(e) => setDraft({ pointsBase: Number(e.target.value) })} className="min-h-tap px-3 w-full rounded-lg border border-border bg-surface mt-1" />
              </label>
            </div>
            <button onClick={() => setStep("config")} className="min-h-tap px-4 rounded-lg bg-primary text-white font-medium hover:bg-primary-dark">Next: configure</button>
          </div>
        )}

        {wizardStep === "config" && (
          <div>
            <ConfigForm />
            <button onClick={() => setStep("preview")} className="min-h-tap mt-4 px-4 rounded-lg bg-primary text-white font-medium hover:bg-primary-dark">Preview</button>
          </div>
        )}

        {wizardStep === "preview" && (
          <div>
            <div className="bg-surface-alt border border-border rounded-lg p-4">
              <QuestionRendererHost question={previewQuestion} onResponse={() => {}} />
            </div>
            <div className="flex gap-2 mt-4">
              <button onClick={() => setStep("config")} className="min-h-tap px-4 rounded-lg border border-border bg-surface hover:border-primary">Back</button>
              <button onClick={() => saveQuestion()} className="min-h-tap px-4 rounded-lg bg-success text-white font-medium hover:opacity-90">Save question</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
