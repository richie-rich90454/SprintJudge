import { useI18n } from "../i18n";
import { useEffect, useRef, useState } from "react";
import { useAdminStore, WizardStep } from "../stores/useAdminStore";
import { QuestionType } from "../types";
import { QuestionRendererHost } from "../components/QuestionRendererHost";
import { ErrorBoundary } from "../components/ErrorBoundary";
import { Card } from "../ui/Card";
import { Button } from "../ui/Button";
import { Dialog } from "../ui/Dialog";
import { QuestionDto } from "../types";
import {
    ExtraQuestionType,
    extraDefaultConfig,
    isExtraQuestionType,
    WIZARD_QUESTION_TYPES,
} from "../components/authoring/authoringTypes";
import { ExtraTypeConfigForm, ExtraTypePreview } from "../components/authoring/ExtraTypeConfigForm";
import { RichStemEditor, RichStemPreview } from "../components/authoring/RichStemFields";
import { readRichStem, RichStem } from "../components/authoring/richStem";
import { ItemQualityPanel } from "../components/authoring/ItemQualityPanel";

const STEPS: WizardStep[] = ["type", "statement", "config", "preview"];

function ConfigForm() {
    const { t } = useI18n();
    const draft = useAdminStore((s) => s.draft);
    const setDraft = useAdminStore((s) => s.setDraft);
    const type = draft.questionType as QuestionType;
    const config = (draft.config as Record<string, unknown>) ?? {};
    const set = (patch: Record<string, unknown>) => setDraft({ config: { ...config, ...patch } });

    if (isExtraQuestionType(type as string)) {
        return (
            <ExtraTypeConfigForm type={type as ExtraQuestionType} config={config} onChange={set} />
        );
    }

    switch (type) {
        case "MCQ":
        case "OUTPUT_PRED":
        case "COMPLEXITY": {
            const options = (config["options"] as string[]) ?? ["", "", "", ""];
            return (
                <div className="flex flex-col gap-4">
                    {type === "OUTPUT_PRED" && (
                        <textarea
                            placeholder={t("ui.codeSnippet")}
                            value={(config["code"] as string) ?? ""}
                            onChange={(e) => set({ code: e.target.value })}
                            className="mono w-full min-h-[100px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[2px]"
                        />
                    )}
                    {options.map((o, i) => (
                        <input
                            key={i}
                            value={o}
                            placeholder={t("ui.optionLabel", {
                                letter: String.fromCharCode(65 + i),
                            })}
                            onChange={(e) => {
                                const n = [...options];
                                n[i] = e.target.value;
                                set({ options: n });
                            }}
                            className="input-underline"
                        />
                    ))}
                    <label className="text-sm text-[var(--oq-ink-soft)] mt-2">
                        {t("ui.correctOptionIndex")}
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
                    {t("ui.correctAnswer")}
                    <select
                        value={String(config["correct"] ?? "true")}
                        onChange={(e) => set({ correct: e.target.value === "true" })}
                        className="input-underline ml-2 w-32"
                    >
                        <option value="true">{t("ui.true")}</option>
                        <option value="false">{t("ui.false")}</option>
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
                            placeholder={t("ui.optionLabel", {
                                letter: String.fromCharCode(65 + i),
                            })}
                            onChange={(e) => {
                                const n = [...options];
                                n[i] = e.target.value;
                                set({ options: n });
                            }}
                            className="input-underline"
                        />
                    ))}
                    <label className="text-sm text-[var(--oq-ink-soft)] mt-2">
                        {t("ui.correctIndicesCommaSep")}
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
                        {t("ui.answer")}
                        <input
                            type="number"
                            value={(config["answer"] as number) ?? 0}
                            onChange={(e) => set({ answer: Number(e.target.value) })}
                            className="input-underline ml-1 w-32"
                        />
                    </label>
                    <label className="text-sm">
                        {t("ui.tolerance")}
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
                        placeholder={t("ui.snippetUseForTheBlank")}
                        value={(config["snippet"] as string) ?? ""}
                        onChange={(e) => set({ snippet: e.target.value })}
                        className="mono w-full min-h-[100px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[2px]"
                    />
                    <input
                        placeholder={t("ui.correctAnswer")}
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
                            {t("ui.noLinesYetAddTheFirstLineBelow")}
                        </p>
                    )}
                    {lines.map((l, i) => (
                        <input
                            key={i}
                            value={l.text}
                            placeholder={t("ui.lineLabel", { n: i + 1 })}
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
                        {t("ui.addLine")}
                    </button>
                    <label className="text-sm text-[var(--oq-ink-soft)]">
                        {t("ui.correctOrderLineNumbersCommaSeparated")}
                    </label>
                    <input
                        placeholder={t("ui.eg201")}
                        value={
                            (config["correctOrder"] as string) ?? lines.map((_, i) => i).join(",")
                        }
                        onChange={(e) => set({ correctOrder: e.target.value })}
                        className="input-underline"
                    />
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
                            {t("ui.noCodeLinesYetAddOneBuggyLinePerRowBelow")}
                        </p>
                    )}
                    <textarea
                        placeholder={t("ui.oneBuggyLinePerRow")}
                        value={codeLines.join("\n")}
                        onChange={(e) => set({ codeLines: e.target.value.split("\n") })}
                        className="mono w-full min-h-[120px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[2px]"
                    />
                    <label className="text-sm text-[var(--oq-ink-soft)]">
                        {t("ui.bugLineIndex0based")}
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
                <div className="flex flex-col gap-4">
                    <textarea
                        placeholder={t("ui.skeletonStarterCodeEditableRegion")}
                        value={(config["skeleton"] as string) ?? ""}
                        onChange={(e) => set({ skeleton: e.target.value })}
                        className="mono w-full min-h-[140px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[2px]"
                    />
                    <textarea
                        placeholder={t("ui.expectedCompletedCodeUsedToGrade")}
                        value={(config[t("ui.expected")] as string) ?? ""}
                        onChange={(e) => set({ expected: e.target.value })}
                        className="mono w-full min-h-[140px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[2px]"
                    />
                </div>
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
                            placeholder={t("ui.buggyFunction")}
                            value={(config["buggyFunction"] as string) ?? ""}
                            onChange={(e) => set({ buggyFunction: e.target.value })}
                            className="mono w-full min-h-[120px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[2px]"
                        />
                    )}
                    {type === "OJ_FULL" && (
                        <textarea
                            placeholder={t("ui.starterCode")}
                            value={(config["starter"] as string) ?? ""}
                            onChange={(e) => set({ starter: e.target.value })}
                            className="mono w-full min-h-[120px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[2px]"
                        />
                    )}
                    <p className="text-sm text-[var(--oq-ink-soft)] mt-1">{t("ui.testCases")}</p>
                    {tc.map((c, i) => (
                        <div key={i} className="tc-row flex gap-4">
                            <input
                                placeholder={t("ui.input")}
                                value={c.input}
                                onChange={(e) => {
                                    const n = [...tc];
                                    n[i] = { ...c, input: e.target.value };
                                    set({ testCases: n });
                                }}
                                className="input-underline flex-1 text-sm"
                            />
                            <input
                                placeholder={t("ui.expected")}
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
                        {t("ui.addTestCase")}
                    </button>
                </div>
            );
        }
        default:
            return <p className="text-[var(--oq-ink-soft)]">{t("ui.noConfigEditorForThisType")}</p>;
    }
}

export function QuestionWizard() {
    const { t } = useI18n();
    const { wizardStep, wizardType, draft, setStep, setType, setDraft, saveQuestion, closeWizard } =
        useAdminStore();
    const [validationError, setValidationError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
    const stepButtons = useRef<Partial<Record<WizardStep, HTMLButtonElement | null>>>({});
    useEffect(() => {
        stepButtons.current[wizardStep]?.focus();
    }, [wizardStep]);
    const config = (draft.config as Record<string, unknown>) ?? {};
    const rich = readRichStem(config);
    const setRich = (next: RichStem) => setDraft({ config: { ...config, rich: next } });
    const previewExtra = isExtraQuestionType(draft.questionType ?? "");
    const previewQuestion: QuestionDto = {
        id: "preview",
        type: (draft.questionType as QuestionType) ?? "MCQ",
        title: draft.title ?? t("ui.previewQuestion"),
        description: draft.description ?? "",
        timeLimitSec: draft.timeLimitSec ?? 30,
        pointsBase: draft.pointsBase ?? 100,
        languagesAllowed: (draft.languagesAllowed as string[]) ?? null,
        config: draft.config ?? {},
    };

    const doSave = async () => {
        if (!(draft.title ?? "").trim()) {
            setValidationError(t("ui.titleIsRequiredAddATitleBeforeSaving"));
            setStep("statement");
            return;
        }
        setValidationError(null);
        setSaving(true);
        try {
            await saveQuestion();
        } catch (error) {
            const detail = (error as { response?: { data?: { error?: string } } })?.response?.data
                ?.error;
            setValidationError(detail ? t("ui.saveFailedDetail", { detail }) : t("ui.saveFailed"));
        } finally {
            setSaving(false);
        }
    };

    return (
        <Dialog
            open
            onOpenChange={(open) => {
                if (!open) closeWizard();
            }}
            title={t("ui.questionWizard")}
            size="lg"
        >
            <div>
                <div className="flex gap-4 mb-5 flex-wrap">
                    {STEPS.map((s) => (
                        <Button
                            key={s}
                            ref={(el) => {
                                stepButtons.current[s] = el;
                            }}
                            variant={wizardStep === s ? "primary" : "secondary"}
                            size="sm"
                            aria-current={wizardStep === s ? "step" : undefined}
                            onClick={() => setStep(s)}
                        >
                            {s.charAt(0).toUpperCase() + s.slice(1)}
                        </Button>
                    ))}
                </div>

                {wizardStep === "type" && (
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
                        {WIZARD_QUESTION_TYPES.map((type) => (
                            <Button
                                key={type}
                                variant={wizardType === type ? "primary" : "secondary"}
                                size="sm"
                                onClick={() => {
                                    setType(type as QuestionType);
                                    if (isExtraQuestionType(type as string)) {
                                        setDraft({
                                            config: extraDefaultConfig(type as ExtraQuestionType),
                                        });
                                    }
                                    setStep("statement");
                                }}
                            >
                                {type.replace(/_/g, " ")}
                            </Button>
                        ))}
                    </div>
                )}

                {wizardStep === "statement" && (
                    <div className="flex flex-col gap-4">
                        <input
                            placeholder={t("ui.title")}
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
                            placeholder={t("ui.descriptionMarkdown")}
                            value={draft.description ?? ""}
                            onChange={(e) => setDraft({ description: e.target.value })}
                            className="min-h-[80px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] rounded-[2px]"
                        />
                        <RichStemEditor value={rich} onChange={setRich} />
                        <div className="border border-[var(--oq-border)] p-3 rounded-[2px]">
                            <p className="text-xs text-[var(--oq-ink-soft)] mb-2">
                                {t("ui.liveStemPreview")}
                            </p>
                            <RichStemPreview value={rich} description={draft.description} />
                        </div>
                        <div className="flex gap-4">
                            <label className="text-sm flex-1">
                                {t("ui.timeLimitS")}
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
                                {t("ui.basePoints")}
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
                            {t("ui.nextConfigure")}
                        </Button>
                    </div>
                )}

                {wizardStep === "config" && (
                    <div>
                        <ConfigForm />
                        <ItemQualityPanel
                            type={draft.questionType ?? ""}
                            title={draft.title ?? ""}
                            description={draft.description ?? ""}
                            config={config}
                        />
                        <Button
                            variant="primary"
                            className="mt-4"
                            onClick={() => setStep("preview")}
                        >
                            {t("ui.preview")}
                        </Button>
                    </div>
                )}

                {wizardStep === "preview" && (
                    <div>
                        <Card>
                            <div className="p-6">
                                {previewExtra ? (
                                    <div className="flex flex-col gap-4">
                                        <RichStemPreview
                                            value={rich}
                                            description={draft.description}
                                        />
                                        <ExtraTypePreview
                                            type={draft.questionType as ExtraQuestionType}
                                            config={config}
                                        />
                                        <p className="text-xs text-[var(--oq-ink-soft)]">
                                            {t(
                                                "ui.authoringPreviewOnlyRuntimeAndServerValidationForThisTypeAreOwnedByTheEngineTrack",
                                            )}
                                        </p>
                                    </div>
                                ) : (
                                    <>
                                        {/* Key on content: the host only remounts on
                                            type/id, so statement/config edits need
                                            an explicit remount signal. */}
                                        <ErrorBoundary
                                            fallback={
                                                <p className="text-[var(--oq-ink-soft)] text-sm">
                                                    {t(
                                                        "ui.previewUnavailableForThisConfigurationCheckTheFieldsAbove",
                                                    )}
                                                </p>
                                            }
                                        >
                                            <QuestionRendererHost
                                                key={JSON.stringify([
                                                    previewQuestion.type,
                                                    previewQuestion.title,
                                                    previewQuestion.description,
                                                    previewQuestion.config,
                                                ])}
                                                question={previewQuestion}
                                                onResponse={() => {}}
                                            />
                                        </ErrorBoundary>
                                        {(rich.imageUrl ||
                                            rich.audioUrl ||
                                            rich.latex ||
                                            rich.code) && (
                                            <div className="mt-4 border-t border-[var(--oq-border)] pt-4">
                                                <p className="text-xs text-[var(--oq-ink-soft)] mb-2">
                                                    {t("ui.richStemMediaAuthorPreview")}
                                                </p>
                                                <RichStemPreview value={rich} />
                                            </div>
                                        )}
                                    </>
                                )}
                            </div>
                        </Card>
                        {validationError && (
                            <p role="alert" className="text-[var(--oq-danger)] text-sm mt-4">
                                {validationError}
                            </p>
                        )}
                        <div className="flex gap-4 mt-4">
                            <Button variant="secondary" onClick={() => setStep("config")}>
                                {t("ui.back")}
                            </Button>
                            <Button variant="primary" disabled={saving} onClick={doSave}>
                                {saving ? (
                                    <span className="inline-flex items-center gap-2">
                                        <span className="oq-spin" aria-hidden="true" />
                                        {t("ui.savingEllipsis")}
                                    </span>
                                ) : (
                                    t("ui.saveQuestion")
                                )}
                            </Button>
                        </div>
                    </div>
                )}
            </div>
        </Dialog>
    );
}
