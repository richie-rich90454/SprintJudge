import { useState, useEffect, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Card, CardContent, Button } from "@heroui/react";
import { useGameStore } from "../stores/useGameStore";
import { useTimerStore } from "../stores/useTimerStore";
import { QuestionRendererHost } from "../components/QuestionRendererHost";
import { CircularTimer } from "../components/Timer/CircularTimer";
import { isCoding } from "../services/ScoringService";
import { Confetti } from "../components/Confetti";
import { audio } from "../services/AudioEngine";
import { ErrorBoundary } from "../components/ErrorBoundary";
import { QuestionDto, SubmissionResult } from "../types";
import { KAHOOT_COLORS } from "../design/kahoot";

export function QuestionView() {
    const q = useGameStore((s) => s.currentQuestion) as QuestionDto | null;
    const status = useGameStore((s) => s.status);
    const submit = useGameStore((s) => s.submit);
    const wsError = useGameStore((s) => s.error);
    const lastResult = useGameStore((s) => s.lastResult) as SubmissionResult | null;
    const end = useTimerStore((s) => s.endEpochMs);
    const [response, setResponse] = useState<unknown>(null);
    const [submitted, setSubmitted] = useState(false);
    const [feedback, setFeedback] = useState<null | { ok: boolean; text: string }>(null);
    const [shake, setShake] = useState(false);
    const shakeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => {
        setSubmitted(false);
        setResponse(null);
        setFeedback(null);
        setShake(false);
        if (shakeTimer.current) {
            clearTimeout(shakeTimer.current);
            shakeTimer.current = null;
        }
    }, [q?.id]);

    // Cleanup shake timer on unmount
    useEffect(() => {
        return () => {
            if (shakeTimer.current) clearTimeout(shakeTimer.current);
        };
    }, []);

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
            shakeTimer.current = setTimeout(() => {
                setShake(false);
                shakeTimer.current = null;
            }, 400);
        }
    }, [lastResult, q?.id]);

    if (status === "REVIEW") {
        return (
            <div className="pattern-exam min-h-screen flex items-center justify-center p-4">
                <Confetti fireKey={q?.id ?? "review"} />
                <Card className="bg-[var(--oq-surface)] text-center max-w-md w-full">
                    <CardContent className="p-6 gap-2">
                        <p className="label-caps mb-2">Round complete</p>
                        <h2 className="text-2xl font-extrabold">Answers locked.</h2>
                        <p className="text-[var(--oq-ink-soft)] mt-2">
                            {q
                                ? "The host is preparing the next round."
                                : "No question was active. The host is preparing the next round."}
                        </p>
                    </CardContent>
                </Card>
            </div>
        );
    }

    if (!q || end === null) {
        return (
            <div className="pattern-exam min-h-screen flex items-center justify-center p-4">
                <div className="text-center w-full max-w-sm">
                    <p className="label-caps mb-2">Standby</p>
                    <div
                        className="mx-auto mb-4 h-3 w-40 animate-pulse rounded-[10px] bg-[var(--oq-border)]"
                        aria-hidden="true"
                    />
                    <div
                        className="mx-auto mb-4 h-3 w-28 animate-pulse rounded-[10px] bg-[var(--oq-border)]"
                        aria-hidden="true"
                    />
                    <p className="text-[var(--oq-ink-soft)]">
                        Waiting for the host to start the next question.
                    </p>
                    {wsError && (
                        <p role="alert" className="text-[var(--oq-danger)] text-sm mt-3">
                            {wsError}
                        </p>
                    )}
                </div>
            </div>
        );
    }

    const untimed = !isFinite(end);
    const coding = isCoding(q.type);

    const doSubmit = () => {
        if (submitted) return;
        const lang =
            coding && typeof response === "object" && response !== null && "language" in response
                ? (response as { language?: string }).language
                : undefined;
        submit(q.id, response, lang);
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
            <div className="flex items-center justify-between flex-wrap gap-2 px-4 md:px-6 py-3 border-b-2 border-[var(--oq-accent)] min-w-0">
                <span className="label-caps min-w-0 truncate">{q.type.replace(/_/g, " ")}</span>
                <span className="mono text-sm text-[var(--oq-ink-soft)]">{q.pointsBase} pts</span>
                {!untimed && (
                    <div className="scale-90">
                        <CircularTimer
                            endEpochMs={end}
                            totalSec={q.timeLimitSec}
                            onExpire={() => !submitted && doSubmit()}
                        />
                    </div>
                )}
                {untimed && <span className="chip chip-neutral">Untimed</span>}
            </div>

            <motion.div
                animate={shake ? { x: [0, -10, 10, -8, 8, 0] } : { x: 0 }}
                transition={{ duration: 0.4 }}
                className="flex-1 flex flex-col lg:flex-row gap-0 overflow-hidden"
            >
                {/* LEFT: question */}
                <div className="w-full lg:w-1/2 p-4 md:p-6 overflow-y-auto flex flex-col min-h-0">
                    <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight">
                        {q.title}
                    </h1>
                    {q.description && (
                        <p className="text-[var(--oq-ink-soft)] mt-3 whitespace-pre-wrap leading-relaxed">
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
                                aria-live="polite"
                                className="mt-4 rounded-[10px] px-4 py-3 font-bold"
                                style={{
                                    background: feedback.ok
                                        ? KAHOOT_COLORS.green
                                        : "var(--oq-danger)",
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
                        "w-full lg:w-1/2 p-4 md:p-6 flex flex-col gap-4 border-t-2 lg:border-t-0 lg:border-l-2 border-[var(--oq-border)] overflow-y-auto min-h-0"
                    }
                >
                    <ErrorBoundary>
                        <QuestionRendererHost
                            question={q}
                            onResponse={setResponse}
                            revealSignal={0}
                        />
                    </ErrorBoundary>

                    <Button
                        onPress={doSubmit}
                        isDisabled={submitted}
                        className="btn btn-primary w-full mt-2 font-bold shrink-0"
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
