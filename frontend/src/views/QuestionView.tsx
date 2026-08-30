import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Card, CardContent, Button } from "@heroui/react";
import { useGameStore } from "../stores/useGameStore";
import { useTimerStore } from "../stores/useTimerStore";
import { QuestionRendererHost } from "../components/QuestionRendererHost";
import { CircularTimer } from "../components/Timer/CircularTimer";
import { isCoding } from "../services/ScoringService";
import { Confetti } from "../components/Confetti";
import { audio } from "../services/AudioEngine";
import { QuestionDto } from "../types";

export function QuestionView() {
    const q = useGameStore((s) => s.currentQuestion) as QuestionDto | null;
    const status = useGameStore((s) => s.status);
    const submit = useGameStore((s) => s.submit);
    const lastResult = useGameStore((s) => s.lastResult) as any;
    const end = useTimerStore((s) => s.endEpochMs);
    const [response, setResponse] = useState<unknown>(null);
    const [submitted, setSubmitted] = useState(false);
    const [feedback, setFeedback] = useState<null | { ok: boolean; text: string }>(null);
    const [shake, setShake] = useState(false);

    useEffect(() => {
        setSubmitted(false);
        setResponse(null);
        setFeedback(null);
    }, [q?.id]);

    // React to submission result: play sfx + show feedback, no forced lock-in.
    useEffect(() => {
        if (!q) return;
        const sub = lastResult?.submission;
        if (!sub || sub.questionId !== q.id) return;
        const ok = sub.allPassed === true;
        const aiNote = sub.aiFeedback ? ` — ${sub.aiFeedback}` : "";
        setFeedback({
            ok,
            text: isCoding(q.type)
                ? `${sub.passed}/${sub.totalTests} tests passed · +${sub.score}${aiNote}`
                : ok
                  ? `+${sub.score} pts`
                  : "Not quite",
        });
        if (ok) {
            audio.play("correct");
        } else {
            audio.play("wrong");
            setShake(true);
            setTimeout(() => setShake(false), 400);
        }
    }, [lastResult, q?.id]);

    if (status === "REVIEW") {
        return (
            <div className="pattern-exam min-h-screen flex items-center justify-center p-4">
                <Confetti fireKey={q?.id ?? "review"} />
                <Card className="bg-content1 text-center max-w-md w-full">
                    <CardContent className="p-8 gap-2">
                        <p className="label-caps mb-2">Round complete</p>
                        <h2 className="text-2xl font-extrabold">Answers locked.</h2>
                        <p className="text-default-500 mt-2">The host is preparing the next round.</p>
                    </CardContent>
                </Card>
            </div>
        );
    }

    if (!q || end === null) {
        return (
            <div className="pattern-exam min-h-screen flex items-center justify-center">
                <div className="text-center">
                    <p className="label-caps mb-2">Standby</p>
                    <p className="text-default-500">Waiting for the host to start the next question.</p>
                </div>
            </div>
        );
    }

    const untimed = !isFinite(end);
    const coding = isCoding(q.type);

    const doSubmit = () => {
        if (submitted) return;
        submit(
            q.id,
            response,
            coding ? (response as { language?: string })?.language : undefined,
        );
        setSubmitted(true);
        audio.play("click");
        try {
            localStorage.removeItem(`sprintjudge_code_${q.id}`);
        } catch {
            /* ignore */
        }
    };

    return (
        <div className="pattern-exam h-screen flex flex-col overflow-hidden">
            {/* Top bar: type + points + timer */}
            <div className="flex items-center justify-between px-4 md:px-6 py-3 border-b-2 border-[var(--oq-red)]">
                <span className="label-caps">{q.type.replace(/_/g, " ")}</span>
                <span className="mono text-sm text-default-500">{q.pointsBase} pts</span>
                {!untimed && (
                    <div className="scale-90">
                        <CircularTimer
                            endEpochMs={end}
                            totalSec={q.timeLimitSec}
                            onExpire={() => !submitted && doSubmit()}
                        />
                    </div>
                )}
                {untimed && (
                    <span className="chip chip-neutral">Untimed</span>
                )}
            </div>

            <motion.div
                animate={shake ? { x: [0, -10, 10, -8, 8, 0] } : { x: 0 }}
                transition={{ duration: 0.4 }}
                className="flex-1 flex flex-col lg:flex-row gap-0 overflow-hidden"
            >
                {/* LEFT: question */}
                <div className="w-full lg:w-1/2 p-4 md:p-6 overflow-y-auto flex flex-col">
                    <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight">
                        {q.title}
                    </h1>
                    {q.description && (
                        <p className="text-default-500 mt-3 whitespace-pre-wrap leading-relaxed">
                            {q.description}
                        </p>
                    )}
                    {coding && (
                        <div className="mt-4 chip chip-neutral">
                            Write code that passes the hidden test cases. Run it to see output.
                        </div>
                    )}
                    {/* Feedback banner (live, non-blocking) */}
                    <AnimatePresence>
                        {feedback && (
                            <motion.div
                                initial={{ opacity: 0, y: -8 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0 }}
                                className="mt-4 rounded-xl px-4 py-3 font-bold"
                                style={{
                                    background: feedback.ok ? "var(--color-kahoot-green)" : "var(--oq-red)",
                                    color: "#fff",
                                    boxShadow: feedback.ok
                                        ? "0 0 24px rgba(31,190,107,0.5)"
                                        : "0 0 24px rgba(255,46,99,0.4)",
                                }}
                            >
                                {feedback.text}
                            </motion.div>
                        )}
                    </AnimatePresence>
                </div>

                {/* RIGHT: answer area / editor */}
                <div
                    className={
                        "w-full lg:w-1/2 p-4 md:p-6 flex flex-col gap-3 border-t-2 lg:border-t-0 lg:border-l-2 border-[var(--oq-border)] " +
                        (coding ? "overflow-hidden" : "overflow-y-auto")
                    }
                >
                    <QuestionRendererHost
                        question={q}
                        onResponse={setResponse}
                        revealSignal={0}
                    />

                    <Button
                        onPress={doSubmit}
                        isDisabled={submitted}
                        variant="primary"
                        size="lg"
                        className="w-full mt-2 font-bold bg-[var(--oq-red)] text-white"
                    >
                        {submitted
                            ? "Answer locked in"
                            : coding
                              ? "Compile and submit"
                              : "Lock in answer"}
                    </Button>
                </div>
            </motion.div>
        </div>
    );
}
