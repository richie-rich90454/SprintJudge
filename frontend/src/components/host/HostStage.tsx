import { QRCodeSVG } from "qrcode.react";
import { useGameStore } from "../../stores/useGameStore";
import { useTimerStore } from "../../stores/useTimerStore";
import { CircularTimer } from "../Timer/CircularTimer";
import { QuestionPreview } from "./QuestionPreview";
import { FormattedText } from "./FormattedText";
import { useRevealMotion, useStageEnter } from "../../motion/useStageMotion";
import { formatAnswer } from "../../game/reviewFormat";
import { stageThemeStyle, useStageTheme } from "./StageTheme";
import type { RoundResult } from "../../types";
import { useI18n } from "../../i18n";

type OptionMeta = { id?: string; misconception?: string };

function answerBars(round: RoundResult | null, optionCount = 0) {
    if (!round) return [];
    if (round.answerCounts && Object.keys(round.answerCounts).length > 0) {
        const total = Object.values(round.answerCounts).reduce((a, b) => a + b, 0) || 1;
        const keys =
            optionCount > 0
                ? Array.from({ length: optionCount }, (_, i) => String.fromCharCode(65 + i))
                : Object.keys(round.answerCounts).sort();
        return keys.map((key) => {
            const count = round.answerCounts?.[key] ?? 0;
            return { key, count, ratio: count / total };
        });
    }
    const total = round.scores.length || 1;
    const correct = round.scores.filter((s) => s.correct).length;
    return [
        { key: "Correct", count: correct, ratio: correct / total },
        { key: "Incorrect", count: total - correct, ratio: (total - correct) / total },
    ];
}

function topMisconception(
    round: RoundResult | null,
    options: OptionMeta[],
): string | null {
    if (!round?.answerCounts) return null;
    const meta = new Map<string, string>();
    for (const option of options) {
        if (option.id && option.misconception) meta.set(option.id, option.misconception);
    }
    const worst = Object.entries(round.answerCounts)
        .filter(([key]) => meta.has(key))
        .sort(([, a], [, b]) => b - a)[0];
    return worst ? meta.get(worst[0]) ?? null : null;
}

export function HostStage({
    pin,
    joinUrl,
    projector = false,
}: {
    pin: string;
    joinUrl: string;
    projector?: boolean;
}) {
    const status = useGameStore((s) => s.status);
    const question = useGameStore((s) => s.currentQuestion);
    const room = useGameStore((s) => s.room);
    const lastResult = useGameStore((s) => s.lastResult);
    const end = useTimerStore((s) => s.endEpochMs);
    const totalSec = useTimerStore((s) => s.totalSec);
    const [theme] = useStageTheme();
    const { t } = useI18n();

    const round =
        lastResult && "scores" in lastResult ? (lastResult as unknown as RoundResult) : null;
    const players = (room?.players ?? []).filter((p) => p.name !== "Host");
    const answerCount = round?.scores.length ?? 0;
    const correctCount = round?.scores.filter((s) => s.correct).length ?? 0;
    const studentsInRoom = () =>
        t("ui.studentsInRoom", {
            n: players.length,
            word: t(players.length === 1 ? "ui.studentSingular" : "ui.studentsPlural"),
        });
    const questionProgress =
        room && room.questionCount > 0
            ? t("ui.questionProgress", {
                  current: room.currentQuestionIndex + 1,
                  total: room.questionCount,
              })
            : null;
    const config = (question?.config ?? {}) as Record<string, unknown>;
    const optionCount = Array.isArray(config.options) ? config.options.length : 0;
    const bars = answerBars(round, optionCount);
    const reveal = (round?.correctAnswer ?? {}) as Record<string, unknown>;
    const optionMeta = Array.isArray(reveal.optionMeta)
        ? (reveal.optionMeta as OptionMeta[])
        : Array.isArray(config.optionMeta)
          ? (config.optionMeta as OptionMeta[])
          : [];
    const misconception = topMisconception(round, optionMeta);
    const explanation =
        typeof reveal.explanation === "string"
            ? reveal.explanation
            : typeof config.explanation === "string"
              ? config.explanation
              : null;
    const options = Array.isArray(config.options) ? (config.options as string[]) : null;
    const answerText =
        round && question && Object.keys(reveal).length > 0
            ? formatAnswer(question.type, reveal, options)
            : null;

    const stageRef = useStageEnter<HTMLDivElement>([question?.id, status, pin]);
    const revealRef = useRevealMotion<HTMLDivElement>(status === "REVIEW", [question?.id, status]);

    if (!question || status === "LOBBY" || status === "ENDED") {
        return (
            <section
                ref={stageRef}
                className={`stage arch relative flex min-h-[420px] flex-col justify-between overflow-hidden p-8 md:p-12 ${
                    projector ? "min-h-[62vh]" : ""
                }`}
                data-stage-theme={theme}
                style={stageThemeStyle(theme)}
            >
                <div>
                    <p className="label-caps text-[var(--stage-ink-soft)]">
                        {t(status === "ENDED" ? "ui.finalStandings" : "ui.lobbyOpen")}
                    </p>
                    <h2
                        className="mt-3 font-black leading-[0.95] tracking-[-0.04em]"
                        style={{ fontSize: projector ? "clamp(44px, 6vw, 88px)" : "clamp(36px, 5vw, 64px)" }}
                    >
                        {t(status === "ENDED" ? "ui.stageRoundEnded" : "ui.stageJoinRoom")}
                    </h2>
                    <p className="mt-4 max-w-xl text-lg leading-relaxed text-[var(--stage-ink-soft)]">
                        {t("ui.stageScanCode")}
                    </p>
                </div>

                <div className="mt-8 flex flex-col gap-8 md:flex-row md:items-end md:justify-between">
                    <div>
                        <p className="label-caps text-[var(--stage-ink-soft)]">{t("ui.gamePIN")}</p>
                        <p
                            className="font-mono font-semibold leading-none tracking-[0.16em]"
                            style={{ fontSize: projector ? "clamp(64px, 9vw, 132px)" : "clamp(48px, 7vw, 92px)" }}
                        >
                            {pin}
                        </p>
                        <p className="mt-3 break-all font-mono text-sm text-[var(--stage-ink-soft)]">
                            {joinUrl}
                        </p>
                        <p className="mt-6 text-sm text-[var(--stage-ink-soft)]">
                            {studentsInRoom()}
                        </p>
                    </div>
                    <div className="rounded-[2px] border border-[var(--stage-border)] bg-white p-3">
                        <QRCodeSVG
                            role="img" value={joinUrl} size={projector ? 196 : 148} level="M" aria-label={t("ui.qrLabel", { pin: pin ?? "" })} />
                    </div>
                </div>
            </section>
        );
    }

    const isReview = status === "REVIEW";

    return (
        <section
            ref={stageRef}
            className="stage arch relative overflow-hidden p-6 md:p-8"
            aria-live="polite"
            data-stage-theme={theme}
            style={stageThemeStyle(theme)}
        >
            <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                    <p className="label-caps text-[var(--stage-ink-soft)]">
                        {questionProgress ?? t(isReview ? "ui.answerReveal" : "ui.questionLive")}
                    </p>
                    <p className="mt-1 text-sm text-[var(--stage-ink-soft)]">
                        {isReview
                            ? t("ui.correctOfTotal", { correct: correctCount, total: answerCount })
                            : studentsInRoom()}
                    </p>
                </div>
                <div className="flex items-center gap-4">
                    {!isReview && end && Number.isFinite(end) && (
                        <CircularTimer endEpochMs={end} totalSec={totalSec} />
                    )}
                    {isReview && (
                        <div className="text-right">
                            <p className="font-mono text-3xl font-semibold">
                                {answerCount > 0 ? Math.round((correctCount / answerCount) * 100) : 0}%
                            </p>
                            <p className="label-caps text-[var(--stage-ink-soft)]">{t("status.correct")}</p>
                        </div>
                    )}
                </div>
            </div>

            <div ref={revealRef} className="mt-6">
                {isReview && answerText && (
                    <div className="reveal-correct reveal-card mb-6 border-2 border-[var(--stage-accent)] bg-[var(--stage-surface)] p-5 text-center">
                        <p className="label-caps text-[var(--stage-ink-soft)]">{t("ui.correctAnswer")}</p>
                        <p className="mt-1 whitespace-pre-wrap text-2xl font-black leading-tight text-[var(--stage-ink)] md:text-3xl">
                            {answerText}
                        </p>
                    </div>
                )}
                <QuestionPreview question={question} round={round} />
            </div>

            {isReview && (
                <div className="mt-6 grid gap-5 border-t border-[var(--stage-border)] pt-5 md:grid-cols-[1.2fr_0.8fr]">
                    <div className="reveal-card">
                        <p className="label-caps mb-3 text-[var(--stage-ink-soft)]">{t("ui.distribution")}</p>
                        <div className="flex flex-col gap-3">
                            {bars.map((bar) => (
                                <div key={bar.key} className="flex items-center gap-3">
                                    <span className="w-24 shrink-0 font-mono text-sm text-[var(--stage-ink-soft)]">
                                        {bar.key === "Correct"
                                            ? t("status.correct")
                                            : bar.key === "Incorrect"
                                              ? t("status.incorrect")
                                              : bar.key}
                                    </span>
                                    <div className="h-3 flex-1 overflow-hidden rounded-[1px] bg-[var(--stage-border)]">
                                        <div
                                            className="reveal-bar h-full origin-left bg-[var(--stage-accent)]"
                                            style={{ width: `${Math.round(bar.ratio * 100)}%` }}
                                        />
                                    </div>
                                    <span className="w-10 text-right font-mono text-sm">
                                        {bar.count}
                                    </span>
                                </div>
                            ))}
                        </div>
                        {misconception && (
                            <div className="mt-5 border-l-2 border-[var(--stage-accent)] pl-4">
                                <p className="label-caps text-[var(--stage-ink-soft)]">
                                    {t("ui.watchMisconception")}
                                </p>
                                <p className="mt-1 leading-relaxed">{misconception}</p>
                            </div>
                        )}
                    </div>
                    {explanation && (
                        <div className="reveal-card rounded-[2px] border border-[var(--stage-border)] bg-[var(--stage-surface)] p-4">
                            <p className="label-caps mb-2 text-[var(--stage-ink-soft)]">{t("ui.why")}</p>
                            <FormattedText
                                text={explanation}
                                className="text-sm text-[var(--stage-ink-soft)]"
                            />
                        </div>
                    )}
                </div>
            )}
        </section>
    );
}
