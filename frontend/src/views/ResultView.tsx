import { useEffect, useRef, useState } from "react";
import { motion as fm } from "framer-motion";
import { Card, CardContent, Button } from "@heroui/react";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { motion } from "../services/MotionService";
import { Confetti } from "../components/Confetti";
import { KAHOOT_COLORS } from "../design/kahoot";
import { GameReview } from "../types";

type ReviewTab = "podium" | "answers" | "students" | "analysis";

export function ResultView() {
    const leaderboard = useGameStore((s) => s.leaderboard);
    const review = useGameStore((s) => s.review) as GameReview | null;
    const wsError = useGameStore((s) => s.error);
    const setView = useUIStore((s) => s.setView);
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
        <div className="pattern-exam min-h-screen py-10">
            <Confetti fireKey="final" />
            <div className="page-shell max-w-4xl">
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

                {/* Tab bar */}
                <nav
                    role="tablist"
                    aria-label="Result views"
                    className="flex gap-1 mb-6 overflow-x-auto"
                >
                    {tabs.map((t) => (
                        <button
                            key={t.id}
                            role="tab"
                            aria-selected={tab === t.id}
                            onClick={() => setTab(t.id)}
                            className={
                                "px-4 py-2 min-h-[44px] text-sm font-bold border-b-2 transition-colors whitespace-nowrap " +
                                (tab === t.id
                                    ? "border-[var(--oq-accent)] text-[var(--oq-accent)]"
                                    : "border-transparent text-[var(--oq-ink-soft)] hover:text-[var(--oq-ink)]")
                            }
                        >
                            {t.label}
                        </button>
                    ))}
                </nav>

                {/* Podium tab */}
                {tab === "podium" && (
                    <Card className="bg-[var(--oq-surface)] pt-10 pb-6 px-6 md:px-10">
                        <CardContent className="gap-8">
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
                        </CardContent>
                    </Card>
                )}

                {/* Answer Key tab */}
                {tab === "answers" && (
                    <Card className="bg-[var(--oq-surface)]">
                        <CardContent className="p-6">
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
                                                className="h-16 animate-pulse rounded-[16px] bg-[var(--oq-border)] opacity-40"
                                            />
                                        ))}
                                    </div>
                                    <p className="text-[var(--oq-ink-soft)] text-sm mt-4">
                                        Loading review… no review data yet.
                                    </p>
                                </div>
                            ) : !review.questions || review.questions.length === 0 ? (
                                <p className="text-[var(--oq-ink-soft)]">No answers to show yet.</p>
                            ) : (
                                <div className="flex flex-col gap-4">
                                    {review.questions.map((q, i) => (
                                        <div
                                            key={q.questionId}
                                            className="border border-[var(--oq-border)] rounded-[16px] p-6"
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
                        </CardContent>
                    </Card>
                )}

                {/* Students tab */}
                {tab === "students" && (
                    <Card className="bg-[var(--oq-surface)]">
                        <CardContent className="p-6">
                            <div className="flex items-center justify-between mb-4">
                                <h3 className="font-extrabold text-lg">Student Results</h3>
                                <Button
                                    className="btn btn-secondary btn-sm"
                                    onPress={() => setNamesRevealed(!namesRevealed)}
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
                                                className="h-14 animate-pulse rounded-[16px] bg-[var(--oq-border)] opacity-40"
                                            />
                                        ))}
                                    </div>
                                    <p className="text-[var(--oq-ink-soft)] text-sm mt-4">
                                        Loading review… no student results yet.
                                    </p>
                                </div>
                            ) : !review.players || review.players.length === 0 ? (
                                <p className="text-[var(--oq-ink-soft)]">No student results yet.</p>
                            ) : (
                                <div className="flex flex-col gap-4">
                                    {[...review.players]
                                        .sort((a, b) => b.totalScore - a.totalScore)
                                        .map((p, i) => (
                                            <div
                                                key={p.playerUuid}
                                                className="border border-[var(--oq-border)] rounded-[16px] p-6 cursor-pointer hover:bg-[var(--oq-row-alt)] transition-colors min-h-[44px]"
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
                        </CardContent>
                    </Card>
                )}

                {/* Analysis tab */}
                {tab === "analysis" && (
                    <Card className="bg-[var(--oq-surface)]">
                        <CardContent className="p-6">
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
                                                className="h-20 animate-pulse rounded-[16px] bg-[var(--oq-border)] opacity-40"
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
                                        <div className="border border-[var(--oq-border)] rounded-[16px] p-6 text-center">
                                            <p className="label-caps mb-1">Players</p>
                                            <p className="stat-value">
                                                {review.classStats.totalPlayers}
                                            </p>
                                        </div>
                                        <div className="border border-[var(--oq-border)] rounded-[16px] p-6 text-center">
                                            <p className="label-caps mb-1">Avg Score</p>
                                            <p className="stat-value">
                                                {Math.round(review.classStats.avgScore)}
                                            </p>
                                        </div>
                                        <div className="border border-[var(--oq-border)] rounded-[16px] p-6 text-center">
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
                                                        className="w-32 truncate font-medium"
                                                        title={q.title}
                                                    >
                                                        {q.title}
                                                    </div>
                                                    <div className="flex-1 h-4 bg-[var(--oq-border)] rounded-full overflow-hidden">
                                                        <div
                                                            className="h-full rounded-full transition-all"
                                                            style={{
                                                                width: `${q.correctRate * 100}%`,
                                                                background:
                                                                    q.correctRate >= 0.7
                                                                        ? KAHOOT_COLORS.green
                                                                        : q.correctRate >= 0.4
                                                                          ? KAHOOT_COLORS.yellow
                                                                          : "var(--oq-danger)",
                                                            }}
                                                        />
                                                    </div>
                                                    <span className="mono text-xs w-12 text-right">
                                                        {Math.round(q.correctRate * 100)}%
                                                    </span>
                                                </div>
                                            ))}
                                    </div>
                                </>
                            )}
                        </CardContent>
                    </Card>
                )}

                <Button className="btn btn-secondary w-full mt-6" onPress={() => setView("join")}>
                    Back to lobby
                </Button>
            </div>
        </div>
    );
}
