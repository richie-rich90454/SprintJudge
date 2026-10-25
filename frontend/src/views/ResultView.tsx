import { useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { Card } from "../components/ui/Card";
import { Button } from "../components/ui/Button";
import { Tabs, TabPanel } from "../components/ui/Tabs";
import { useGameStore } from "../stores/useGameStore";
import { formatAnswer } from "../services/reviewFormat";
import { SoundToggle } from "../components/SoundToggle";
import { MotionToggle } from "../components/MotionToggle";
import { useCountUp } from "../motion/useStageMotion";
import { GameReview } from "../types";

type ReviewTab = "podium" | "answers" | "students" | "teams" | "analysis";

export function ResultView() {
    const leaderboard = useGameStore((s) => s.leaderboard);
    const review = useGameStore((s) => s.review) as GameReview | null;
    const wsError = useGameStore((s) => s.error);
    const navigate = useNavigate();
    const [tab, setTab] = useState<ReviewTab>("podium");
    const [selectedStudent, setSelectedStudent] = useState<string | null>(null);
    const [selectedQuestion, setSelectedQuestion] = useState<string | null>(null);
    const [namesRevealed, setNamesRevealed] = useState(false);
    const [exporting, setExporting] = useState(false);
    const [exportError, setExportError] = useState<string | null>(null);

    const exportCsv = async () => {
        if (!review?.sessionId || exporting) return;
        setExporting(true);
        setExportError(null);
        try {
            const response = await fetch(
                `/api/admin/export/room/${review.sessionId}?format=csv`,
                { credentials: "include" },
            );
            if (!response.ok) throw new Error("export failed");
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement("a");
            anchor.href = url;
            anchor.download = `sprintjudge-${review.sessionId}.csv`;
            anchor.click();
            URL.revokeObjectURL(url);
        } catch {
            setExportError("Could not export the room. Sign in again and retry.");
        } finally {
            setExporting(false);
        }
    };

    const podium = leaderboard.slice(0, 3);
    const rest = leaderboard.slice(3);
    const podiumRef = useCountUp<HTMLDivElement>(true, [podium.length]);
    const order = podium.length >= 3 ? [1, 0, 2] : podium.map((_, i) => i);

    const tabs: { id: ReviewTab; label: string }[] = [
        { id: "podium", label: "Podium" },
        { id: "answers", label: "Answer Key" },
        { id: "students", label: "Students" },
        ...(review?.teams && review.teams.length > 0
            ? [{ id: "teams" as ReviewTab, label: "Teams" }]
            : []),
        { id: "analysis", label: "Analysis" },
    ];
    return (
        <div className="pattern-exam min-h-[100dvh] py-10">
            <div className="page-shell max-w-5xl">
                <div className="flex items-start justify-between gap-4">
                    <div>
                        <p className="label-caps mb-2">Final</p>
                        <h1 className="text-4xl font-black tracking-[-0.03em] md:text-5xl">
                            Results
                        </h1>
                        <p className="mt-2 text-[var(--oq-ink-soft)]">
                            {review?.classStats
                                ? `${review.classStats.totalPlayers} ${
                                      review.classStats.totalPlayers === 1 ? "student" : "students"
                                  }, ${review.classStats.totalQuestions} ${
                                      review.classStats.totalQuestions === 1 ? "question" : "questions"
                                  }`
                                : "Class summary"}
                        </p>
                    </div>
                    <div className="flex gap-1">
                        <SoundToggle />
                        <MotionToggle />
                    </div>
                </div>
                <div className="mt-6 border-b border-[var(--oq-border-strong)]" />

                <Tabs
                    value={tab}
                    onValueChange={(v) => setTab(v as ReviewTab)}
                    tabs={tabs}
                    label="Result views"
                >
                    <TabPanel value="podium">
                        <Card className="p-6 md:p-10">
                            <div ref={podiumRef} className="flex flex-col gap-8">
                                <div className="flex min-h-[240px] items-end justify-center gap-4 overflow-x-auto pb-2 md:gap-8">
                                    {podium.length === 0 && (
                                        <p className="text-[var(--oq-ink-soft)]">No results yet.</p>
                                    )}
                                    {order.map((idx) => {
                                        if (idx >= podium.length) return null;
                                        const p = podium[idx];
                                        const height =
                                            p.rank === 1
                                                ? "h-40"
                                                : p.rank === 2
                                                  ? "h-32"
                                                  : "h-24";
                                        return (
                                            <div
                                                key={p.uuid}
                                                className="flex w-28 shrink-0 flex-col items-center md:w-40"
                                            >
                                                <p
                                                    className="font-mono text-2xl font-semibold tabular-nums md:text-3xl"
                                                    data-count={p.score}
                                                >
                                                    {p.score}
                                                </p>
                                                <p
                                                    className="mt-1 max-w-full truncate text-sm font-semibold"
                                                    title={p.name}
                                                >
                                                    {p.name}
                                                </p>
                                                <div
                                                    className={`mt-3 flex w-full ${height} items-start justify-center border border-[var(--oq-border-strong)] bg-[var(--oq-row-alt)] pt-3`}
                                                >
                                                    <span className="font-mono text-4xl font-semibold text-[var(--oq-accent)]">
                                                        {p.rank}
                                                    </span>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>

                                {rest.length > 0 && (
                                    <ol className="flex flex-col gap-2">
                                        {rest.map((r) => (
                                            <li
                                                key={r.uuid}
                                                className="flex min-h-[44px] items-center justify-between border border-[var(--oq-border)] bg-[var(--oq-row-alt)] px-4 py-3"
                                            >
                                                <span className="flex min-w-0 items-center gap-4">
                                                    <span className="flex h-8 min-w-8 shrink-0 items-center justify-center border border-[var(--oq-border)] px-1 font-mono text-sm tabular-nums">
                                                        {r.rank}
                                                    </span>
                                                    <span className="truncate" title={r.name}>
                                                        {r.name}
                                                    </span>
                                                </span>
                                                <span
                                                    className="font-mono font-semibold tabular-nums"
                                                    data-count={r.score}
                                                >
                                                    {r.score}
                                                </span>
                                            </li>
                                        ))}
                                    </ol>
                                )}

                                <div className="flex flex-col items-center gap-3 border-t border-[var(--oq-border)] pt-6">
                                    <div className="flex flex-wrap justify-center gap-3">
                                        <Button
                                            variant="secondary"
                                            onClick={exportCsv}
                                            disabled={!review?.sessionId || exporting}
                                        >
                                            {exporting ? "Exporting" : "Export CSV"}
                                        </Button>
                                        <Button
                                            variant="secondary"
                                            onClick={() => navigate({ to: "/" })}
                                        >
                                            Leave results
                                        </Button>
                                        <Button
                                            variant="primary"
                                            onClick={() => navigate({ to: "/admin" })}
                                        >
                                            New game
                                        </Button>
                                    </div>
                                    {exportError && (
                                        <p role="alert" className="text-sm text-[var(--oq-danger)]">
                                            {exportError}
                                        </p>
                                    )}
                                </div>
                            </div>
                        </Card>
                    </TabPanel>

                    <TabPanel value="answers">
                        <Card>
                            <div className="p-6">
                                <h3 className="font-extrabold text-lg mb-4">Answer Key</h3>
                                {wsError && (
                                    <p
                                        role="alert"
                                        className="text-[var(--oq-danger)] text-sm mb-4"
                                    >
                                        {wsError}
                                    </p>
                                )}
                                {!review ? (
                                    <div>
                                        <div className="flex flex-col gap-4" aria-hidden="true">
                                            {[0, 1, 2].map((i) => (
                                                <div
                                                    key={i}
                                                    className="h-16 animate-pulse rounded-[8px] bg-[var(--oq-border)] opacity-40"
                                                />
                                            ))}
                                        </div>
                                        <p className="text-[var(--oq-ink-soft)] text-sm mt-4">
                                            Loading review… no review data yet.
                                        </p>
                                    </div>
                                ) : !review.questions || review.questions.length === 0 ? (
                                    <p className="text-[var(--oq-ink-soft)]">
                                        No answers to show yet.
                                    </p>
                                ) : (
                                    <div className="flex flex-col gap-4">
                                        {review.questions.map((q, i) => (
                                            <button
                                                key={q.questionId}
                                                type="button"
                                                aria-expanded={selectedQuestion === q.questionId}
                                                onClick={() =>
                                                    setSelectedQuestion(
                                                        selectedQuestion === q.questionId
                                                            ? null
                                                            : q.questionId,
                                                    )
                                                }
                                                className="border border-[var(--oq-border)] rounded-[8px] p-6 hover:bg-[var(--oq-row-alt)] transition-colors min-h-[44px] w-full text-left"
                                            >
                                                <div className="flex items-start justify-between gap-4">
                                                    <div className="flex-1">
                                                        <p className="font-bold">
                                                            {i + 1}. {q.title}
                                                        </p>
                                                        <p className="text-sm text-[var(--oq-ink-soft)] mt-1">
                                                            {q.questionType.replace(/_/g, " ")},{" "}
                                                            {q.pointsBase} pts
                                                        </p>
                                                    </div>
                                                    <div className="text-right">
                                                        <p className="mono text-sm">
                                                            <span
                                                                className={
                                                                    q.correctRate >= 0.7
                                                                        ? "text-[var(--oq-success)]"
                                                                        : q.correctRate >= 0.4
                                                                          ? "text-[var(--oq-warning)]"
                                                                          : "text-[var(--oq-danger)]"
                                                                }
                                                            >
                                                                {Math.round(q.correctRate * 100)}%
                                                            </span>{" "}
                                                            correct
                                                        </p>
                                                        <p className="text-xs text-[var(--oq-ink-soft)]">
                                                            {q.totalAttempts} attempts
                                                        </p>
                                                    </div>
                                                </div>
                                                {q.answer != null && (
                                                    <div className="mt-3 p-3 rounded-[6px] bg-[var(--oq-row-alt)] text-sm mono whitespace-pre-wrap">
                                                        {formatAnswer(
                                                            q.questionType,
                                                            q.answer,
                                                            q.options ?? null,
                                                        )}
                                                    </div>
                                                )}
                                                {selectedQuestion === q.questionId && (
                                                    <div className="mt-3 border-t border-[var(--oq-border)] pt-3 flex flex-col gap-2">
                                                        {(review.players ?? []).map((p, pi) => {
                                                            const a = p.answers.find(
                                                                (x) =>
                                                                    x.questionId === q.questionId,
                                                            );
                                                            if (!a) return null;
                                                            return (
                                                                <div
                                                                    key={p.playerUuid}
                                                                    className="flex items-center justify-between text-sm"
                                                                >
                                                                    <span
                                                                        className={
                                                                            a.correct
                                                                                ? "text-[var(--oq-success)]"
                                                                                : "text-[var(--oq-danger)]"
                                                                        }
                                                                    >
                                                                        {a.correct ? "✓" : "✗"}{" "}
                                                                        {namesRevealed
                                                                            ? p.playerName
                                                                            : `Player ${pi + 1}`}
                                                                    </span>
                                                                    <span className="mono text-[var(--oq-ink-soft)]">
                                                                        +{a.scoreEarned} points{" "}
                                                                        {a.attemptCount}{" "}
                                                                        {a.attemptCount === 1
                                                                            ? "try"
                                                                            : "tries"}
                                                                    </span>
                                                                </div>
                                                            );
                                                        })}
                                                    </div>
                                                )}
                                            </button>
                                        ))}
                                    </div>
                                )}
                            </div>
                        </Card>
                    </TabPanel>

                    <TabPanel value="students">
                        <Card>
                            <div className="p-6">
                                <div className="flex items-center justify-between mb-4">
                                    <h3 className="font-extrabold text-lg">Student Results</h3>
                                    <Button
                                        variant="secondary"
                                        size="sm"
                                        onClick={() => setNamesRevealed(!namesRevealed)}
                                    >
                                        {namesRevealed ? "Hide names" : "Reveal names"}
                                    </Button>
                                </div>
                                {wsError && (
                                    <p
                                        role="alert"
                                        className="text-[var(--oq-danger)] text-sm mb-4"
                                    >
                                        {wsError}
                                    </p>
                                )}
                                {!review ? (
                                    <div>
                                        <div className="flex flex-col gap-4" aria-hidden="true">
                                            {[0, 1, 2].map((i) => (
                                                <div
                                                    key={i}
                                                    className="h-14 animate-pulse rounded-[8px] bg-[var(--oq-border)] opacity-40"
                                                />
                                            ))}
                                        </div>
                                        <p className="text-[var(--oq-ink-soft)] text-sm mt-4">
                                            Loading review… no student results yet.
                                        </p>
                                    </div>
                                ) : !review.players || review.players.length === 0 ? (
                                    <p className="text-[var(--oq-ink-soft)]">
                                        No student results yet.
                                    </p>
                                ) : (
                                    <div className="flex flex-col gap-4">
                                        {[...review.players]
                                            .sort((a, b) => b.totalScore - a.totalScore)
                                            .map((p, i) => (
                                                <button
                                                    key={p.playerUuid}
                                                    type="button"
                                                    aria-expanded={selectedStudent === p.playerUuid}
                                                    className="border border-[var(--oq-border)] rounded-[8px] p-6 hover:bg-[var(--oq-row-alt)] transition-colors min-h-[44px] w-full text-left"
                                                    onClick={() =>
                                                        setSelectedStudent(
                                                            selectedStudent === p.playerUuid
                                                                ? null
                                                                : p.playerUuid,
                                                        )
                                                    }
                                                >
                                                    <div className="flex items-center justify-between">
                                                        <span className="font-bold">
                                                            {i + 1}.{" "}
                                                            {namesRevealed
                                                                ? p.playerName
                                                                : `Player ${i + 1}`}
                                                        </span>
                                                        <span className="mono font-semibold">
                                                            {p.totalScore} pts
                                                        </span>
                                                    </div>
                                                    {selectedStudent === p.playerUuid &&
                                                        namesRevealed && (
                                                            <div className="mt-3 flex flex-col gap-4">
                                                                {p.answers.map((a) => (
                                                                    <div
                                                                        key={a.questionId}
                                                                        className="flex items-center justify-between text-sm"
                                                                    >
                                                                        <span
                                                                            className={
                                                                                a.correct
                                                                                    ? "text-[var(--oq-success)]"
                                                                                    : "text-[var(--oq-danger)]"
                                                                            }
                                                                        >
                                                                            {a.correct ? "✓" : "✗"}{" "}
                                                                            {a.questionId}
                                                                        </span>
                                                                        <span className="mono text-[var(--oq-ink-soft)]">
                                                                            +{a.scoreEarned}
                                                                        </span>
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        )}
                                                </button>
                                            ))}
                                    </div>
                                )}
                            </div>
                        </Card>
                    </TabPanel>

                    <TabPanel value="teams">
                        <Card className="p-6">
                            <h3 className="font-extrabold text-lg mb-4">Team standings</h3>
                            {!review || !review.teams || review.teams.length === 0 ? (
                                <p className="text-[var(--oq-ink-soft)]">
                                    This game was not played in team mode.
                                </p>
                            ) : (
                                <ol className="flex flex-col gap-2">
                                    {review.teams.map((t, i) => (
                                        <li
                                            key={t.id}
                                            className="flex min-h-[44px] items-center justify-between gap-4 border border-[var(--oq-border)] bg-[var(--oq-row-alt)] px-4 py-3"
                                        >
                                            <span className="flex min-w-0 items-center gap-4">
                                                <span className="flex h-8 min-w-8 shrink-0 items-center justify-center border border-[var(--oq-border)] px-1 font-mono text-sm tabular-nums">
                                                    {i + 1}
                                                </span>
                                                <span className="min-w-0">
                                                    <span className="block truncate font-semibold">
                                                        {t.name}
                                                    </span>
                                                    <span className="text-xs text-[var(--oq-ink-soft)]">
                                                        {t.memberUuids.length}{" "}
                                                        {t.memberUuids.length === 1
                                                            ? "member"
                                                            : "members"}
                                                    </span>
                                                </span>
                                            </span>
                                            <span className="font-mono font-semibold tabular-nums">
                                                {t.score}
                                            </span>
                                        </li>
                                    ))}
                                </ol>
                            )}
                        </Card>
                    </TabPanel>

                    <TabPanel value="analysis">
                        <Card>
                            <div className="p-6">
                                <h3 className="font-extrabold text-lg mb-4">Class Analysis</h3>
                                {wsError && (
                                    <p
                                        role="alert"
                                        className="text-[var(--oq-danger)] text-sm mb-4"
                                    >
                                        {wsError}
                                    </p>
                                )}
                                {!review || !review.classStats || !review.questions ? (
                                    <div>
                                        <div
                                            className="grid sm:grid-cols-3 gap-4 mb-6"
                                            aria-hidden="true"
                                        >
                                            {[0, 1, 2].map((i) => (
                                                <div
                                                    key={i}
                                                    className="h-20 animate-pulse rounded-[8px] bg-[var(--oq-border)] opacity-40"
                                                />
                                            ))}
                                        </div>
                                        <p className="text-[var(--oq-ink-soft)] text-sm">
                                            Loading analysis… no class stats yet.
                                        </p>
                                    </div>
                                ) : review.questions.length === 0 ? (
                                    <p className="text-[var(--oq-ink-soft)]">
                                        No analysis to show yet.
                                    </p>
                                ) : (
                                    <>
                                        <div className="grid sm:grid-cols-3 gap-4 mb-6">
                                            <div className="stat-block border border-[var(--oq-border)] rounded-[8px] p-6 text-center">
                                                <p className="label-caps mb-1">Players</p>
                                                <p className="stat-value">
                                                    {review.classStats.totalPlayers}
                                                </p>
                                            </div>
                                            <div className="stat-block border border-[var(--oq-border)] rounded-[8px] p-6 text-center">
                                                <p className="label-caps mb-1">Avg Score</p>
                                                <p className="stat-value">
                                                    {Math.round(review.classStats.avgScore)}
                                                </p>
                                            </div>
                                            <div className="stat-block border border-[var(--oq-border)] rounded-[8px] p-6 text-center">
                                                <p className="label-caps mb-1">Correct Rate</p>
                                                <p className="stat-value">
                                                    {review.classStats.totalAttempts > 0
                                                        ? Math.round(
                                                              (review.classStats.totalCorrect /
                                                                  review.classStats.totalAttempts) *
                                                                  100,
                                                          )
                                                        : 0}
                                                    %
                                                </p>
                                            </div>
                                        </div>

                                        <h4 className="font-bold mb-2">Question Difficulty</h4>
                                        <div className="flex flex-col gap-4">
                                            {[...review.questions]
                                                .sort((a, b) => a.correctRate - b.correctRate)
                                                .map((q) => (
                                                    <div
                                                        key={q.questionId}
                                                        className="flex items-center gap-4 text-sm"
                                                    >
                                                        <div
                                                            className="flex-1 truncate font-medium"
                                                            title={q.title}
                                                        >
                                                            {q.title}
                                                        </div>
                                                        <span
                                                            className={`mono text-sm w-12 text-right ${
                                                                q.correctRate >= 0.7
                                                                    ? "text-[var(--oq-success)]"
                                                                    : q.correctRate >= 0.4
                                                                      ? "text-[var(--oq-warning)]"
                                                                      : "text-[var(--oq-danger)]"
                                                            }`}
                                                        >
                                                            {Math.round(q.correctRate * 100)}%
                                                        </span>
                                                    </div>
                                                ))}
                                        </div>
                                    </>
                                )}
                            </div>
                        </Card>
                    </TabPanel>
                </Tabs>
            </div>
        </div>
    );
}
