import { useEffect, useState } from "react";
import { useAdminStore, WizardStep } from "../stores/useAdminStore";
import { ALL_QUESTION_TYPES, QuestionType } from "../types";
import { QuestionRendererHost } from "../components/QuestionRendererHost";
import { useEnter } from "../hooks/useMotion";
import { Card } from "../components/ui/Card";
import { Button } from "../components/ui/Button";
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
                <div className="flex flex-col gap-4">
                    {type === "OUTPUT_PRED" && (
                        <textarea
                            placeholder="Code snippet"
                            value={(config["code"] as string) ?? ""}
                            onChange={(e) => set({ code: e.target.value })}
                            className="mono w-full min-h-[100px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[10px]"
                        />
                    )}
                    {options.map((o, i) => (
                        <input
                            key={i}
                            value={o}
                            placeholder={`Option ${String.fromCharCode(65 + i)}`}
                            onChange={(e) => {
                                const n = [...options];
                                n[i] = e.target.value;
                                set({ options: n });
                            }}
                            className="input-underline"
                        />
                    ))}
                    <label className="text-sm text-[var(--oq-ink-soft)] mt-2">
                        Correct option index
                    </label>
                    <input
                        type="number"
                        min={0}
                        value={(config["correctIndex"] as number) ?? 0}
                        onChange={(e) => set({ correctIndex: Number(e.target.value) })}
                        className="input-underline w-24"
                    />
                </div>
            );
        }
        case "TRUE_FALSE":
            return (
                <label className="text-sm text-[var(--oq-ink-soft)]">
                    Correct answer
                    <select
                        value={String(config["correct"] ?? "true")}
                        onChange={(e) => set({ correct: e.target.value === "true" })}
                        className="input-underline ml-2 w-32"
                    >
                        <option value="true">True</option>
                        <option value="false">False</option>
                    </select>
                </label>
            );
        case "MULTIPLE_SELECT": {
            const options = (config["options"] as string[]) ?? ["", "", "", ""];
            return (
                <div className="flex flex-col gap-4">
                    {options.map((o, i) => (
                        <input
                            key={i}
                            value={o}
                            placeholder={`Option ${String.fromCharCode(65 + i)}`}
                            onChange={(e) => {
                                const n = [...options];
                                n[i] = e.target.value;
                                set({ options: n });
                            }}
                            className="input-underline"
                        />
                    ))}
                    <label className="text-sm text-[var(--oq-ink-soft)] mt-2">
                        Correct indices (comma sep)
                    </label>
                    <input
                        value={((config["correctIndices"] as number[]) ?? []).join(",")}
                        onChange={(e) =>
                            set({
                                correctIndices: e.target.value
                                    .split(",")
                                    .map((x) => Number(x.trim()))
                                    .filter((n) => !Number.isNaN(n)),
                            })
                        }
                        className="input-underline"
                    />
                </div>
            );
        }
        case "NUMERIC":
            return (
                <div className="flex gap-4 flex-wrap">
                    <label className="text-sm">
                        Answer
                        <input
                            type="number"
                            value={(config["answer"] as number) ?? 0}
                            onChange={(e) => set({ answer: Number(e.target.value) })}
                            className="input-underline ml-1 w-32"
                        />
                    </label>
                    <label className="text-sm">
                        Tolerance
                        <input
                            type="number"
                            step="0.01"
                            value={(config["tolerance"] as number) ?? 0}
                            onChange={(e) => set({ tolerance: Number(e.target.value) })}
                            className="input-underline ml-1 w-32"
                        />
                    </label>
                </div>
            );
        case "FILL_BLANK":
            return (
                <div className="flex flex-col gap-4">
                    <textarea
                        placeholder="Snippet (use ___ for the blank)"
                        value={(config["snippet"] as string) ?? ""}
                        onChange={(e) => set({ snippet: e.target.value })}
                        className="mono w-full min-h-[100px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[10px]"
                    />
                    <input
                        placeholder="Correct answer"
                        value={(config["answer"] as string) ?? ""}
                        onChange={(e) => set({ answer: e.target.value })}
                        className="input-underline"
                    />
                </div>
            );
        case "DRAG_SORT": {
            const lines = (config["lines"] as { id: string; text: string }[]) ?? [];
            return (
                <div className="flex flex-col gap-4">
                    {lines.length === 0 && (
                        <p className="text-[var(--oq-ink-soft)] text-sm">
                            No lines yet — add the first line below.
                        </p>
                    )}
                    {lines.map((l, i) => (
                        <input
                            key={i}
                            value={l.text}
                            placeholder={`Line ${i + 1}`}
                            onChange={(e) => {
                                const n = [...lines];
                                n[i] = { ...l, text: e.target.value };
                                set({ lines: n });
                            }}
                            className="input-underline"
                        />
                    ))}
                    <button
                        onClick={() =>
                            set({ lines: [...lines, { id: String(lines.length), text: "" }] })
                        }
                        className="text-[var(--oq-accent)] text-sm self-start min-h-[44px] px-2 inline-flex items-center font-bold"
                    >
                        Add line
                    </button>
                </div>
            );
        }
        case "CLICK_BUG": {
            const codeLines = (config["codeLines"] as string[]) ?? [];
            const empty = codeLines.length === 0 || codeLines.every((l) => !l.trim());
            return (
                <div className="flex flex-col gap-4">
                    {empty && (
                        <p className="text-[var(--oq-ink-soft)] text-sm">
                            No code lines yet — add one buggy line per row below.
                        </p>
                    )}
                    <textarea
                        placeholder="One buggy line per row"
                        value={codeLines.join("\n")}
                        onChange={(e) => set({ codeLines: e.target.value.split("\n") })}
                        className="mono w-full min-h-[120px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[10px]"
                    />
                    <label className="text-sm text-[var(--oq-ink-soft)]">
                        Bug line index (0-based)
                    </label>
                    <input
                        type="number"
                        value={(config["bugLine"] as number) ?? 0}
                        onChange={(e) => set({ bugLine: Number(e.target.value) })}
                        className="input-underline w-24"
                    />
                </div>
            );
        }
        case "CODE_COMPLETION":
            return (
                <textarea
                    placeholder="Skeleton / starter code (editable region)"
                    value={(config["skeleton"] as string) ?? ""}
                    onChange={(e) => set({ skeleton: e.target.value })}
                    className="mono w-full min-h-[140px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[10px]"
                />
            );
        case "OJ_FULL":
        case "OJ_PATCH": {
            const tc =
                (config["testCases"] as {
                    input: string;
                    expectedOutput: string;
                    isHidden: boolean;
                }[]) ?? [];
            return (
                <div className="flex flex-col gap-4">
                    {type === "OJ_PATCH" && (
                        <textarea
                            placeholder="Buggy function"
                            value={(config["buggyFunction"] as string) ?? ""}
                            onChange={(e) => set({ buggyFunction: e.target.value })}
                            className="mono w-full min-h-[120px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[10px]"
                        />
                    )}
                    {type === "OJ_FULL" && (
                        <textarea
                            placeholder="Starter code"
                            value={(config["starter"] as string) ?? ""}
                            onChange={(e) => set({ starter: e.target.value })}
                            className="mono w-full min-h-[120px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[10px]"
                        />
                    )}
                    <p className="text-sm text-[var(--oq-ink-soft)] mt-1">Test cases</p>
                    {tc.map((c, i) => (
                        <div key={i} className="tc-row flex gap-4">
                            <input
                                placeholder="input"
                                value={c.input}
                                onChange={(e) => {
                                    const n = [...tc];
                                    n[i] = { ...c, input: e.target.value };
                                    set({ testCases: n });
                                }}
                                className="input-underline flex-1 text-sm"
                            />
                            <input
                                placeholder="expected"
                                value={c.expectedOutput}
                                onChange={(e) => {
                                    const n = [...tc];
                                    n[i] = { ...c, expectedOutput: e.target.value };
                                    set({ testCases: n });
                                }}
                                className="input-underline flex-1 text-sm"
                            />
                        </div>
                    ))}
                    <button
                        onClick={() =>
                            set({
                                testCases: [
                                    ...tc,
                                    { input: "", expectedOutput: "", isHidden: false },
                                ],
                            })
                        }
                        className="text-[var(--oq-accent)] text-sm self-start min-h-[44px] px-2 inline-flex items-center font-bold"
                    >
                        Add test case
                    </button>
                </div>
            );
        }
        default:
            return <p className="text-[var(--oq-ink-soft)]">No config editor for this type.</p>;
    }
}

export function QuestionWizard() {
    const { wizardStep, wizardType, draft, setStep, setType, setDraft, saveQuestion, closeWizard } =
        useAdminStore();
    const [validationError, setValidationError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
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

    const panelRef = useEnter<HTMLDivElement>("modal");

    useEffect(() => {
        const onKey = (e: KeyboardEvent) => {
            if (e.key === "Escape") closeWizard();
        };
        window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, [closeWizard]);

    const doSave = async () => {
        if (!(draft.title ?? "").trim()) {
            setValidationError("Title is required — add a title before saving.");
            setStep("statement");
            return;
        }
        setValidationError(null);
        setSaving(true);
        try {
            await saveQuestion();
        } finally {
            setSaving(false);
        }
    };

    return (
        <div
            className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50"
            role="dialog"
            aria-modal="true"
            aria-label="Question wizard"
        >
            <div ref={panelRef}>
                <Card className="modal-topbar bg-[var(--oq-surface)] w-full max-w-2xl max-h-[90vh] overflow-auto">
                    <div className="p-6">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-xl font-bold">Question wizard</h2>
                            <Button variant="ghost" className="min-h-[44px]" onClick={closeWizard}>
                                Close
                            </Button>
                        </div>

                        <div className="flex gap-4 mb-5 flex-wrap">
                            {STEPS.map((s) => (
                                <Button
                                    key={s}
                                    variant={wizardStep === s ? "primary" : "secondary"}
                                    size="sm"
                                    className="capitalize"
                                    onClick={() => setStep(s)}
                                >
                                    {s}
                                </Button>
                            ))}
                        </div>

                        {wizardStep === "type" && (
                            <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
                                {ALL_QUESTION_TYPES.map((t) => (
                                    <Button
                                        key={t}
                                        variant={wizardType === t ? "primary" : "secondary"}
                                        size="sm"
                                        onClick={() => {
                                            setType(t);
                                            setStep("statement");
                                        }}
                                    >
                                        {t.replace("_", " ")}
                                    </Button>
                                ))}
                            </div>
                        )}

                        {wizardStep === "statement" && (
                            <div className="flex flex-col gap-4">
                                <input
                                    placeholder="Title"
                                    value={draft.title ?? ""}
                                    onChange={(e) => setDraft({ title: e.target.value })}
                                    aria-required="true"
                                    aria-invalid={!(draft.title ?? "").trim()}
                                    className="input-underline"
                                />
                                {validationError && (
                                    <p role="alert" className="text-[var(--oq-danger)] text-sm">
                                        {validationError}
                                    </p>
                                )}
                                <textarea
                                    placeholder="Description (Markdown)"
                                    value={draft.description ?? ""}
                                    onChange={(e) => setDraft({ description: e.target.value })}
                                    className="min-h-[80px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] rounded-[10px]"
                                />
                                <div className="flex gap-4">
                                    <label className="text-sm flex-1">
                                        Time limit (s)
                                        <input
                                            type="number"
                                            value={draft.timeLimitSec ?? 30}
                                            onChange={(e) =>
                                                setDraft({ timeLimitSec: Number(e.target.value) })
                                            }
                                            className="input-underline w-full mt-1"
                                        />
                                    </label>
                                    <label className="text-sm flex-1">
                                        Base points
                                        <input
                                            type="number"
                                            value={draft.pointsBase ?? 100}
                                            onChange={(e) =>
                                                setDraft({ pointsBase: Number(e.target.value) })
                                            }
                                            className="input-underline w-full mt-1"
                                        />
                                    </label>
                                </div>
                                <Button variant="primary" onClick={() => setStep("config")}>
                                    Next: configure
                                </Button>
                            </div>
                        )}

                        {wizardStep === "config" && (
                            <div>
                                <ConfigForm />
                                <Button
                                    variant="primary"
                                    className="mt-4"
                                    onClick={() => setStep("preview")}
                                >
                                    Preview
                                </Button>
                            </div>
                        )}

                        {wizardStep === "preview" && (
                            <div>
                                <Card className="bg-[var(--oq-surface)]">
                                    <div className="p-6">
                                        <QuestionRendererHost
                                            question={previewQuestion}
                                            onResponse={() => {}}
                                        />
                                    </div>
                                </Card>
                                {validationError && (
                                    <p
                                        role="alert"
                                        className="text-[var(--oq-danger)] text-sm mt-4"
                                    >
                                        {validationError}
                                    </p>
                                )}
                                <div className="flex gap-4 mt-4">
                                    <Button
                                        variant="secondary"
                                        onClick={() => setStep("config")}
                                    >
                                        Back
                                    </Button>
                                    <Button
                                        variant="primary"
                                        disabled={saving}
                                        onClick={doSave}
                                    >
                                        {saving ? (
                                            <span className="inline-flex items-center gap-2">
                                                <span className="oq-spin" aria-hidden="true" />
                                                Saving…
                                            </span>
                                        ) : (
                                            "Save question"
                                        )}
                                    </Button>
                                </div>
                            </div>
                        )}
                    </div>
                </Card>
            </div>
        </div>
    );
}
