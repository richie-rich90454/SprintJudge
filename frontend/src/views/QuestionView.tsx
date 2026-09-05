import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Navigate } from "@tanstack/react-router";
import { useGameStore } from "../stores/useGameStore";
import { useTimerStore } from "../stores/useTimerStore";
import { motionReduced, useUIStore } from "../stores/useUIStore";
import { QuestionRendererHost } from "../components/QuestionRendererHost";
import { CircularTimer } from "../components/Timer/CircularTimer";
import { isCoding } from "../services/ScoringService";
import { boardDelayedForMode } from "../design/kahoot";
import { Confetti } from "../components/Confetti";
import { audio } from "../services/AudioEngine";
import { ErrorBoundary } from "../components/ErrorBoundary";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { Chip } from "../components/ui/Primitives";

export function QuestionView() {
    const q = useGameStore((s) => s.currentQuestion);
    const status = useGameStore((s) => s.status);
    const gameMode = useGameStore((s) => s.gameMode);
    const submit = useGameStore((s) => s.submit);
    const wsError = useGameStore((s) => s.error);
    const lastResult = useGameStore((s) => s.lastResult);
    const end = useTimerStore((s) => s.endEpochMs);
    const totalSec = useTimerStore((s) => s.totalSec);
    const [response, setResponse] = useState<unknown>(null);
    const [submitted, setSubmitted] = useState(false);
    // Ref mirror: a click and the timer expiry in the same tick both read
    // stale state, so the ref (not the state) owns the once-only guard.
    const submittedRef = useRef(false);
    const [feedback, setFeedback] = useState<null | { ok: boolean; text: string }>(null);
    const [shake, setShake] = useState(false);
    const shakeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    const reduced = motionReduced();
    // Subscribe so a motion-preference flip re-renders immediately.
    useUIStore((s) => s.motion);
    const boardLocked = boardDelayedForMode(gameMode) && status === "ACTIVE";

    useEffect(() => {
        setSubmitted(false);
        submittedRef.current = false;
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
        const aiNote = sub.aiFeedback ? ` - ${sub.aiFeedback}` : "";
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
            if (shakeTimer.current) clearTimeout(shakeTimer.current);
            shakeTimer.current = setTimeout(() => {
                setShake(false);
                shakeTimer.current = null;
            }, 400);
        }
    }, [lastResult, q?.id]);

    if (status === "ENDED") {
        return <Navigate to="/results" />;
    }

    if (status === "REVIEW") {
        return (
            <div className="pattern-exam min-h-[100dvh] flex items-center justify-center p-4">
                {!reduced && <Confetti fireKey={q?.id ?? "review"} />}
                <Card className="text-center max-w-md w-full">
                    <div className="p-6">
                        <p className="label-caps mb-2">Round complete</p>
                        <h2 className="text-2xl font-extrabold">Answers locked.</h2>
                        {feedback && (
                            <p
                                aria-live="polite"
                                className={`mt-3 font-bold ${feedback.ok ? "text-[var(--oq-success)]" : "text-[var(--oq-danger)]"}`}
                            >
                                {feedback.text}
                            </p>
                        )}
                        <p className="text-[var(--oq-ink-soft)] mt-2">
                            {q
                                ? "The host is preparing the next round."
                                : "No question was active. The host is preparing the next round."}
                        </p>
                    </div>
                </Card>
            </div>
        );
    }

    if (!q || end === null) {
        return (
            <div className="pattern-exam min-h-[100dvh] flex items-center justify-center p-4">
                <div className="text-center w-full max-w-sm">
                    <p className="label-caps mb-2">Standby</p>
                    <div
                        className="mx-auto mb-4 h-3 w-40 animate-pulse rounded-[6px] bg-[var(--oq-border)]"
                        aria-hidden="true"
                    />
                    <div
                        className="mx-auto mb-4 h-3 w-28 animate-pulse rounded-[6px] bg-[var(--oq-border)]"
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
        if (submittedRef.current) return;
        submittedRef.current = true;
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
        <div className="pattern-exam min-h-[100dvh] flex flex-col overflow-hidden">
            {/* Top bar: type + points + timer */}
            <div className="flex items-center justify-between flex-wrap gap-2 px-4 md:px-6 py-3 border-b-2 border-[var(--oq-accent)] min-w-0">
                <span className="label-caps min-w-0 truncate">{q.type.replace(/_/g, " ")}</span>
                <span className="mono text-sm text-[var(--oq-ink-soft)]">{q.pointsBase} pts</span>
                {!untimed && (
                    <div className="scale-90">
                        <CircularTimer
                            endEpochMs={end}
                            totalSec={totalSec}
                            onExpire={() => doSubmit()}
                        />
                    </div>
                )}
                {untimed && <span className="chip chip-neutral">Untimed</span>}
            </div>
            {boardLocked && (
                <div className="px-4 md:px-6 pt-2">
                    <Chip tone="neutral">Board locked - updates at round end</Chip>
                </div>
            )}

            <motion.div
                animate={shake && !reduced ? { x: [0, -10, 10, -8, 8, 0] } : { x: 0 }}
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
                                className={`feedback-banner mt-4 ${feedback.ok ? "ok" : "bad"}`}
                            >
                                {feedback.text}
                            </motion.div>
                        )}
                    </AnimatePresence>
                </div>

                {/* RIGHT: answer area / editor */}
                <div className="w-full lg:w-1/2 p-4 md:p-6 flex flex-col gap-4 border-t-2 lg:border-t-0 lg:border-l-2 border-[var(--oq-border)] overflow-y-auto min-h-0">
                    {/* Key per question: a crashed renderer resets instead of
                        bricking the answer area for the rest of the game. */}
                    <ErrorBoundary key={q.id}>
                        <QuestionRendererHost
                            question={q}
                            onResponse={setResponse}
                            revealSignal={0}
                        />
                    </ErrorBoundary>

                    <Button
                        onClick={doSubmit}
                        disabled={submitted}
                        className="w-full mt-2 font-bold shrink-0"
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
