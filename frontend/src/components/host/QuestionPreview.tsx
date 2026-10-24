import { FormattedText } from "./FormattedText";
import type { QuestionDto, RoundResult } from "../../types";

type Config = Record<string, unknown>;

function stringList(value: unknown): string[] {
    return Array.isArray(value) ? value.map((v) => String(v)) : [];
}

function numberAt(value: unknown): number | null {
    return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function CodeLines({
    lines,
    highlight,
    reveal,
}: {
    lines: string[];
    highlight: number | null;
    reveal: boolean;
}) {
    if (!lines.length) return null;
    return (
        <pre className="overflow-x-auto rounded-[2px] border border-[var(--stage-border)] bg-[var(--stage-bg)] p-4 font-mono text-[0.95rem] leading-7">
            <code className="block">
                {lines.map((line, i) => {
                    const isBug = reveal && highlight === i;
                    return (
                        <span
                            key={i}
                            className={`flex gap-4 px-2 ${
                                isBug ? "bg-[color-mix(in_oklab,var(--stage-accent)_18%,transparent)]" : ""
                            }`}
                        >
                            <span
                                aria-hidden="true"
                                className="w-6 shrink-0 select-none text-right text-[var(--stage-ink-soft)]"
                            >
                                {i + 1}
                            </span>
                            <span className="whitespace-pre">{line || " "}</span>
                        </span>
                    );
                })}
            </code>
        </pre>
    );
}

export function QuestionPreview({
    question,
    round,
    className = "",
}: {
    question: QuestionDto;
    round: RoundResult | null;
    className?: string;
}) {
    const config = (question.config ?? {}) as Config;
    const options = stringList(config.options);
    const lines = stringList(config.lines);
    const correctIndex = numberAt(config.correctIndex);
    const correctIndices = Array.isArray(config.correctIndices)
        ? config.correctIndices.filter((v): v is number => typeof v === "number")
        : [];
    const correctBoolean = typeof config.correct === "boolean" ? config.correct : null;
    const bugLine = numberAt(config.bugLine);
    const reveal = round?.revealed === true;
    const answer = (round?.correctAnswer ?? {}) as Config;
    const revealIndex = numberAt(answer.correctIndex) ?? correctIndex;
    const revealIndices = Array.isArray(answer.correctIndices)
        ? answer.correctIndices.filter((v): v is number => typeof v === "number")
        : correctIndices;
    const revealBoolean =
        typeof answer.correct === "boolean" ? answer.correct : correctBoolean;

    const showDescription = question.description && question.description !== question.title;

    const optionRows =
        question.type === "MCQ" ||
        question.type === "OUTPUT_PRED" ||
        question.type === "COMPLEXITY" ||
        question.type === "MULTIPLE_SELECT";

    return (
        <div className={className}>
            <p className="label-caps mb-3">
                {String(
                    config.csaUnit ??
                        (config.topic && config.topic !== question.title
                            ? config.topic
                            : question.type.replace(/_/g, " ")),
                )}
            </p>
            <h2 className="text-3xl font-black leading-[1.08] tracking-[-0.03em] md:text-4xl">
                {question.title}
            </h2>
            {showDescription && (
                <FormattedText
                    text={question.description}
                    className="mt-4 text-[var(--stage-ink-soft)] md:text-lg"
                />
            )}
            {lines.length > 0 && (
                <div className="mt-6">
                    <CodeLines lines={lines} highlight={bugLine} reveal={reveal} />
                </div>
            )}
            {question.type === "OJ_FULL" || question.type === "OJ_PATCH" ? (
                <div className="mt-6 rounded-[2px] border border-[var(--stage-border)] bg-[var(--stage-surface)] p-4">
                    <p className="label-caps mb-2">Starter</p>
                    <pre className="overflow-x-auto font-mono text-sm leading-6 text-[var(--stage-ink-soft)]">
                        {String(config.starter ?? "Write your solution in the editor.")}
                    </pre>
                </div>
            ) : null}
            {optionRows && options.length > 0 && (
                <div className="mt-6 grid gap-3 md:grid-cols-2">
                    {options.map((option, i) => {
                        const isCorrect = reveal
                            ? question.type === "MULTIPLE_SELECT"
                                ? revealIndices.includes(i)
                                : revealIndex === i
                            : false;
                        return (
                            <div
                                key={i}
                                className={`flex items-center gap-4 rounded-[2px] border px-4 py-4 text-left transition-colors ${
                                    isCorrect
                                        ? "border-[var(--stage-accent)] bg-[color-mix(in_oklab,var(--stage-accent)_14%,transparent)]"
                                        : "border-[var(--stage-border)] bg-[var(--stage-surface)]"
                                }`}
                            >
                                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[2px] border border-[var(--stage-border)] font-mono text-sm text-[var(--stage-ink-soft)]">
                                    {String.fromCharCode(65 + i)}
                                </span>
                                <span className="text-lg leading-snug">{option}</span>
                            </div>
                        );
                    })}
                </div>
            )}
            {question.type === "TRUE_FALSE" && (
                <div className="mt-6 grid gap-3 md:grid-cols-2">
                    {[true, false].map((value, i) => {
                        const isCorrect = reveal && revealBoolean === value;
                        return (
                            <div
                                key={String(value)}
                                className={`flex items-center gap-4 rounded-[2px] border px-4 py-4 ${
                                    isCorrect
                                        ? "border-[var(--stage-accent)] bg-[color-mix(in_oklab,var(--stage-accent)_14%,transparent)]"
                                        : "border-[var(--stage-border)] bg-[var(--stage-surface)]"
                                }`}
                            >
                                <span className="font-mono text-sm text-[var(--stage-ink-soft)]">
                                    {i === 0 ? "A" : "B"}
                                </span>
                                <span className="text-lg">{value ? "True" : "False"}</span>
                            </div>
                        );
                    })}
                </div>
            )}
            {question.type === "NUMERIC" && (
                <div className="mt-6 flex items-center gap-4 rounded-[2px] border border-[var(--stage-border)] bg-[var(--stage-surface)] px-4 py-4">
                    <span className="font-mono text-sm text-[var(--stage-ink-soft)]">Answer</span>
                    <span className="font-mono text-2xl">
                        {reveal
                            ? String(answer.answer ?? config.answer ?? "?")
                            : "Enter a number"}
                    </span>
                </div>
            )}
            {question.type === "FILL_BLANK" && (
                <div className="mt-6 flex items-center gap-4 rounded-[2px] border border-[var(--stage-border)] bg-[var(--stage-surface)] px-4 py-4">
                    <span className="font-mono text-sm text-[var(--stage-ink-soft)]">Blank</span>
                    <span className="font-mono text-xl">
                        {reveal ? String(answer.answer ?? config.answer ?? "?") : "One answer"}
                    </span>
                </div>
            )}
        </div>
    );
}
