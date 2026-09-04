import { useEffect, useRef, useState } from "react";
import { motion as fm } from "framer-motion";
import { useNavigate } from "@tanstack/react-router";
import { Card } from "../components/ui/Card";
import { Button } from "../components/ui/Button";
import { Tabs, TabPanel } from "../components/ui/Tabs";
import { useGameStore } from "../stores/useGameStore";
import { motionReduced } from "../stores/useUIStore";
import { motion } from "../services/MotionService";
import { Confetti } from "../components/Confetti";
import { ThemeToggle } from "../components/ThemeToggle";
import { SoundToggle } from "../components/SoundToggle";
import { MotionToggle } from "../components/MotionToggle";
import { GameReview } from "../types";

type ReviewTab = "podium" | "answers" | "students" | "analysis";

export function ResultView() {
    const leaderboard = useGameStore((s) => s.leaderboard);
    const review = useGameStore((s) => s.review) as GameReview | null;
    const wsError = useGameStore((s) => s.error);
    const navigate = useNavigate();
    const [tab, setTab] = useState<ReviewTab>("podium");
    const [selectedStudent, setSelectedStudent] = useState<string | null>(null);
    const [namesRevealed, setNamesRevealed] = useState(false);

    const podium = leaderboard.slice(0, 3);
    const rest = leaderboard.slice(3);
    const listRef = useRef<HTMLOListElement>(null);

    useEffect(() => {
        motion.killFor(listRef.current);
        motion.countUp(listRef.current);
        return () => motion.killFor(listRef.current);
    }, [rest.length]);

    const heights = [128, 88, 64];
    const order = [1, 0, 2];
    const medal = ["1", "2", "3"];

    const tabs: { id: ReviewTab; label: string }[] = [
        { id: "podium", label: "Podium" },
        { id: "answers", label: "Answer Key" },
        { id: "students", label: "Students" },
        { id: "analysis", label: "Analysis" },
    ];

    return (
        <div className="pattern-exam min-h-[100dvh] py-10">
            {!motionReduced() && <Confetti fireKey="final" />}
            <div className="page-shell max-w-4xl">
                <div className="flex justify-end gap-1 mb-2">
                    <SoundToggle />
                    <MotionToggle />
                    <ThemeToggle />
                </div>
                <div className="text-center mb-8">
                    <p className="label-caps mb-2">Game complete</p>
                    <h1
                        className="font-extrabold tracking-tight"
                        style={{ fontSize: "clamp(34px,5vw,56px)" }}
                    >
                        Results
                    </h1>
                    <div
                        className="mt-4 h-[3px] w-20 mx-auto"
                        style={{ background: "var(--oq-accent)" }}
                    />
                </div>

                <Tabs
                    value={tab}
                    onValueChange={(v) => setTab(v as ReviewTab)}
                    tabs={tabs}
                    label="Result views"
                >
                    <TabPanel value="podium">
                        <Card className="pt-10 pb-6 px-6 md:px-10">
                            <div className="flex flex-col gap-8">
                                <div className="flex items-end justify-start sm:justify-center gap-4 mb-10 min-h-[190px] overflow-x-auto pb-2">
                                    {podium.length === 0 && (
                                        <p className="text-[var(--oq-ink-soft)]">No results.</p>
                                    )}
                                    {order.map((idx) => {
                                        if (idx >= podium.length) return null;
                                        const p = podium[idx];
                                        return (
                                            <fm.div
                                                key={p.uuid}
                                                layout
                                                initial={{ opacity: 0, y: 30 }}
                                                animate={{ opacity: 1, y: 0 }}
                                                transition={{
                                                    type: "spring",
                                                    stiffness: 260,
                                                    damping: 22,
                                                }}
                                                className="flex flex-col items-center w-28 md:w-36 shrink-0"
                                            >
                                                <span
                                                    className="mono font-extrabold mb-2"
                                                    style={{
                                                        fontSize: idx === 0 ? 30 : 22,
                                                        color: "var(--oq-accent)",
                                                    }}
                                                >
                                                    {p.score.toLocaleString()}
                                                </span>
                                                <span
                                                    className={`font-bold truncate max-w-full ${idx === 0 ? "text-lg" : ""}`}
                                                    title={p.name}
                                                >
                                                    {p.name}
                                                </span>
                                                <div
                                                    className="w-full mt-2 border-x-2 border-t-2 flex items-start justify-center pt-2"
                                                    style={{
                                                        height: heights[idx],
                                                        borderColor: "var(--oq-accent)",
                                                        background: "var(--oq-row-alt)",
                                                        borderRadius: "12px 12px 0 0",
                                                    }}
                                                >
                                                    <span
                                                        className="mono font-extrabold"
                                                        style={{
                                                            fontSize: idx === 0 ? 44 : 30,
                                                            color: "var(--oq-accent)",
                                                            lineHeight: 1,
                                                        }}
                                                    >
                                                        {medal[idx]}
                                                    </span>
                                                </div>
                                            </fm.div>
                                        );
                                    })}
                                </div>

                                {rest.length > 0 && (
                                    <fm.ol ref={listRef} className="flex flex-col gap-4">
                                        {rest.map((r) => (
                                            <fm.li
                                                key={r.uuid}
                                                layout
                                                className="flex items-center justify-between px-4 py-3 min-h-[44px] border border-[var(--oq-border)] rounded-[10px] bg-[var(--oq-row-alt)]"
                                            >
                                                <span className="flex items-center gap-4 min-w-0">
                                                    <span className="min-w-8 h-8 px-1 mono text-sm flex items-center justify-center border border-[var(--oq-border)] rounded-[10px] tabular-nums shrink-0">
                                                        {r.rank}
                                                    </span>
                                                    <span className="truncate" title={r.name}>
                                                        {r.name}
                                                    </span>
                                                </span>
                                                <span
                                                    className="mono font-semibold"
                                                    data-score={r.score}
                                                >
                                                    {r.score}
                                                </span>
                                            </fm.li>
                                        ))}
                                    </fm.ol>
                                )}
                            </div>
                        </Card>
                    </TabPanel>

                    <TabPanel value="answers">
                        <Card>
                            <div className="p-6">
                                <h3 className="font-extrabold text-lg mb-4">Answer Key</h3>
                                {wsError && (
                                    <p role="alert" className="text-[var(--oq-danger)] text-sm mb-4">
                                        {wsError}
                                    </p>
                                )}
                                {!review ? (
                                    <div>
                                        <div className="flex flex-col gap-4" aria-hidden="true">
                                            {[0, 1, 2].map((i) => (
                                                <div
                                                    key={i}
                                                    className="h-16 animate-pulse rounded-[14px] bg-[var(--oq-border)] opacity-40"
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
                                            <div
                                                key={q.questionId}
                                                className="border border-[var(--oq-border)] rounded-[14px] p-6"
                                            >
                                                <div className="flex items-start justify-between gap-4">
                                                    <div className="flex-1">
                                                        <p className="font-bold">
                                                            {i + 1}. {q.title}
                                                        </p>
                                                        <p className="text-sm text-[var(--oq-ink-soft)] mt-1">
                                                            {q.questionType.replace(/_/g, " ")} ·{" "}
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
                                                    <div className="mt-3 p-3 rounded-[10px] bg-[var(--oq-row-alt)] text-sm mono">
                                                        Answer: {String(JSON.stringify(q.answer))}
                                                    </div>
                                                )}
                                            </div>
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
                                    <p role="alert" className="text-[var(--oq-danger)] text-sm mb-4">
                                        {wsError}
                                    </p>
                                )}
                                {!review ? (
                                    <div>
                                        <div className="flex flex-col gap-4" aria-hidden="true">
                                            {[0, 1, 2].map((i) => (
                                                <div
                                                    key={i}
                                                    className="h-14 animate-pulse rounded-[14px] bg-[var(--oq-border)] opacity-40"
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
                                                <div
                                                    key={p.playerUuid}
                                                    className="border border-[var(--oq-border)] rounded-[14px] p-6 cursor-pointer hover:bg-[var(--oq-row-alt)] transition-colors min-h-[44px]"
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
                                                </div>
                                            ))}
                                    </div>
                                )}
                            </div>
                        </Card>
                    </TabPanel>

                    <TabPanel value="analysis">
                        <Card>
                            <div className="p-6">
                                <h3 className="font-extrabold text-lg mb-4">Class Analysis</h3>
                                {wsError && (
                                    <p role="alert" className="text-[var(--oq-danger)] text-sm mb-4">
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
                                                    className="h-20 animate-pulse rounded-[14px] bg-[var(--oq-border)] opacity-40"
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
                                            <div className="stat-block border border-[var(--oq-border)] rounded-[14px] p-6 text-center">
                                                <p className="label-caps mb-1">Players</p>
                                                <p className="stat-value">
                                                    {review.classStats.totalPlayers}
                                                </p>
                                            </div>
                                            <div className="stat-block border border-[var(--oq-border)] rounded-[14px] p-6 text-center">
                                                <p className="label-caps mb-1">Avg Score</p>
                                                <p className="stat-value">
                                                    {Math.round(review.classStats.avgScore)}
                                                </p>
                                            </div>
                                            <div className="stat-block border border-[var(--oq-border)] rounded-[14px] p-6 text-center">
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

                <Button
                    variant="secondary"
                    className="w-full mt-6"
                    onClick={() => navigate({ to: "/" })}
                >
                    Back to lobby
                </Button>
            </div>
        </div>
    );
}
