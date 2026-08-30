import { useEffect, useRef, useState } from "react";
import { motion as fm } from "framer-motion";
import { Card, CardContent, Button } from "@heroui/react";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { motion } from "../services/MotionService";
import { Confetti } from "../components/Confetti";
import { GameReview } from "../types";

type ReviewTab = "podium" | "answers" | "students" | "analysis";

export function ResultView() {
    const leaderboard = useGameStore((s) => s.leaderboard);
    const review = useGameStore((s) => s.review) as GameReview | null;
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
                        style={{ background: "var(--oq-red)" }}
                    />
                </div>

                {/* Tab bar */}
                <nav className="flex gap-1 mb-6 overflow-x-auto">
                    {tabs.map((t) => (
                        <button
                            key={t.id}
                            onClick={() => setTab(t.id)}
                            className={
                                "px-4 py-2 text-sm font-bold border-b-2 transition-colors whitespace-nowrap " +
                                (tab === t.id
                                    ? "border-[var(--oq-red)] text-[var(--oq-red)]"
                                    : "border-transparent text-default-500 hover:text-default-700")
                            }
                        >
                            {t.label}
                        </button>
                    ))}
                </nav>

                {/* Podium tab */}
                {tab === "podium" && (
                    <Card className="bg-content1 pt-10 pb-8 px-6 md:px-10">
                        <CardContent className="gap-8">
                            <div className="flex items-end justify-center gap-4 mb-10 min-h-[190px]">
                                {podium.length === 0 && (
                                    <p className="text-default-500">No results.</p>
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
                                            className="flex flex-col items-center w-28 md:w-36"
                                        >
                                            <span
                                                className="mono font-extrabold mb-2"
                                                style={{
                                                    fontSize: idx === 0 ? 30 : 22,
                                                    color: "var(--oq-red)",
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
                                                    borderColor: "var(--oq-red)",
                                                    background: "var(--oq-row-alt)",
                                                    borderRadius: "12px 12px 0 0",
                                                }}
                                            >
                                                <span
                                                    className="mono font-extrabold"
                                                    style={{
                                                        fontSize: idx === 0 ? 44 : 30,
                                                        color: "var(--oq-red)",
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
                                <fm.ol ref={listRef} className="flex flex-col gap-2">
                                    {rest.map((r) => (
                                        <fm.li
                                            key={r.uuid}
                                            layout
                                            className="flex items-center justify-between px-4 py-3 border border-default-200 rounded-xl bg-default-100"
                                        >
                                            <span className="flex items-center gap-3">
                                                <span className="w-7 h-7 mono text-sm flex items-center justify-center border border-default-200 rounded-md">
                                                    {r.rank}
                                                </span>
                                                {r.name}
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
                {tab === "answers" && review?.questions && (
                    <Card className="bg-content1">
                        <CardContent className="p-6">
                            <h3 className="font-extrabold text-lg mb-4">Answer Key</h3>
                            <div className="flex flex-col gap-3">
                                {review.questions.map((q, i) => (
                                    <div
                                        key={q.questionId}
                                        className="border border-default-200 rounded-xl p-4"
                                    >
                                        <div className="flex items-start justify-between gap-4">
                                            <div className="flex-1">
                                                <p className="font-bold">
                                                    {i + 1}. {q.title}
                                                </p>
                                                <p className="text-sm text-default-500 mt-1">
                                                    {q.questionType.replace(/_/g, " ")} ·{" "}
                                                    {q.pointsBase} pts
                                                </p>
                                            </div>
                                            <div className="text-right">
                                                <p className="mono text-sm">
                                                    <span
                                                        className={
                                                            q.correctRate >= 0.7
                                                                ? "text-green-600"
                                                                : q.correctRate >= 0.4
                                                                  ? "text-yellow-600"
                                                                  : "text-red-600"
                                                        }
                                                    >
                                                        {Math.round(q.correctRate * 100)}%
                                                    </span>{" "}
                                                    correct
                                                </p>
                                                <p className="text-xs text-default-500">
                                                    {q.totalAttempts} attempts
                                                </p>
                                            </div>
                                        </div>
                                        {q.answer != null && (
                                            <div className="mt-3 p-3 rounded-lg bg-default-100 text-sm mono">
                                                Answer: {String(JSON.stringify(q.answer))}
                                            </div>
                                        )}
                                    </div>
                                ))}
                            </div>
                        </CardContent>
                    </Card>
                )}

                {/* Students tab */}
                {tab === "students" && review?.players && (
                    <Card className="bg-content1">
                        <CardContent className="p-6">
                            <div className="flex items-center justify-between mb-4">
                                <h3 className="font-extrabold text-lg">Student Results</h3>
                                <Button
                                    size="sm"
                                    variant="outline"
                                    onPress={() => setNamesRevealed(!namesRevealed)}
                                >
                                    {namesRevealed ? "Hide names" : "Reveal names"}
                                </Button>
                            </div>
                            <div className="flex flex-col gap-2">
                                {[...review.players]
                                    .sort((a, b) => b.totalScore - a.totalScore)
                                    .map((p, i) => (
                                        <div
                                            key={p.playerUuid}
                                            className="border border-default-200 rounded-xl p-4 cursor-pointer hover:bg-default-100 transition-colors"
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
                                            {selectedStudent === p.playerUuid && namesRevealed && (
                                                <div className="mt-3 flex flex-col gap-1">
                                                    {p.answers.map((a) => (
                                                        <div
                                                            key={a.questionId}
                                                            className="flex items-center justify-between text-sm"
                                                        >
                                                            <span
                                                                className={
                                                                    a.correct
                                                                        ? "text-green-600"
                                                                        : "text-red-600"
                                                                }
                                                            >
                                                                {a.correct ? "✓" : "✗"}{" "}
                                                                {a.questionId}
                                                            </span>
                                                            <span className="mono text-default-500">
                                                                +{a.scoreEarned}
                                                            </span>
                                                        </div>
                                                    ))}
                                                </div>
                                            )}
                                        </div>
                                    ))}
                            </div>
                        </CardContent>
                    </Card>
                )}

                {/* Analysis tab */}
                {tab === "analysis" && review?.classStats && review?.questions && (
                    <Card className="bg-content1">
                        <CardContent className="p-6">
                            <h3 className="font-extrabold text-lg mb-4">Class Analysis</h3>
                            <div className="grid sm:grid-cols-3 gap-4 mb-6">
                                <div className="border border-default-200 rounded-xl p-4 text-center">
                                    <p className="label-caps mb-1">Players</p>
                                    <p className="stat-value">{review.classStats.totalPlayers}</p>
                                </div>
                                <div className="border border-default-200 rounded-xl p-4 text-center">
                                    <p className="label-caps mb-1">Avg Score</p>
                                    <p className="stat-value">
                                        {Math.round(review.classStats.avgScore)}
                                    </p>
                                </div>
                                <div className="border border-default-200 rounded-xl p-4 text-center">
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
                            <div className="flex flex-col gap-2">
                                {[...review.questions]
                                    .sort((a, b) => a.correctRate - b.correctRate)
                                    .map((q) => (
                                        <div
                                            key={q.questionId}
                                            className="flex items-center gap-3 text-sm"
                                        >
                                            <div className="w-32 truncate font-medium">
                                                {q.title}
                                            </div>
                                            <div className="flex-1 h-4 bg-default-200 rounded-full overflow-hidden">
                                                <div
                                                    className="h-full rounded-full transition-all"
                                                    style={{
                                                        width: `${q.correctRate * 100}%`,
                                                        background:
                                                            q.correctRate >= 0.7
                                                                ? "var(--color-kahoot-green)"
                                                                : q.correctRate >= 0.4
                                                                  ? "var(--color-kahoot-yellow)"
                                                                  : "var(--oq-red)",
                                                    }}
                                                />
                                            </div>
                                            <span className="mono text-xs w-12 text-right">
                                                {Math.round(q.correctRate * 100)}%
                                            </span>
                                        </div>
                                    ))}
                            </div>
                        </CardContent>
                    </Card>
                )}

                <Button variant="outline" className="w-full mt-6" onPress={() => setView("join")}>
                    Back to lobby
                </Button>
            </div>
        </div>
    );
}
