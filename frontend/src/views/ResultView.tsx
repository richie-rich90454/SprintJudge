import { useEffect, useRef } from "react";
import { motion as fm } from "framer-motion";
import { Card, CardContent, Button } from "@heroui/react";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { motion } from "../services/MotionService";
import { Confetti } from "../components/Confetti";

export function ResultView() {
    const leaderboard = useGameStore((s) => s.leaderboard);
    const setView = useUIStore((s) => s.setView);

    const podium = leaderboard.slice(0, 3);
    const rest = leaderboard.slice(3);
    const listRef = useRef<HTMLOListElement>(null);

    useEffect(() => {
        motion.countUp(listRef.current);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [rest.length]);

    const heights = [128, 88, 64];
    const order = [1, 0, 2];
    const medal = ["1", "2", "3"];

    return (
        <div className="pattern-exam min-h-screen py-10">
            <Confetti fireKey="final" />
            <div className="page-shell max-w-3xl">
                <div className="text-center mb-10">
                    <p className="label-caps mb-2">Final standings</p>
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

                <Card className="bg-content1 pt-10 pb-8 px-6 md:px-10">
                    <CardContent className="gap-8">
                        <div className="flex items-end justify-center gap-4 mb-10 min-h-[190px]">
                            {podium.length === 0 && <p className="text-default-500">No results.</p>}
                            {order.map((idx) => {
                                if (idx >= podium.length) return null;
                                const p = podium[idx];
                                return (
                                    <fm.div
                                        key={p.uuid}
                                        layout
                                        initial={{ opacity: 0, y: 30 }}
                                        animate={{ opacity: 1, y: 0 }}
                                        transition={{ type: "spring", stiffness: 260, damping: 22 }}
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
                                        <span className="mono font-semibold" data-score={r.score}>
                                            {r.score}
                                        </span>
                                    </fm.li>
                                ))}
                            </fm.ol>
                        )}

                        <Button
                            variant="outline"
                            className="w-full mt-2"
                            onPress={() => setView("join")}
                        >
                            Back to lobby
                        </Button>
                    </CardContent>
                </Card>
            </div>
        </div>
    );
}
