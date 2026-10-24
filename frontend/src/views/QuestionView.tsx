import { useEffect, useRef, useState } from "react";
import { Link, Navigate } from "@tanstack/react-router";
import { useGameStore } from "../stores/useGameStore";
import { useTimerStore } from "../stores/useTimerStore";
import { QuestionRendererHost } from "../components/QuestionRendererHost";
import { CircularTimer } from "../components/Timer/CircularTimer";
import { isCoding } from "../services/ScoringService";
import { boardDelayedForMode } from "../design/kahoot";
import { audio } from "../services/AudioEngine";
import { ErrorBoundary } from "../components/ErrorBoundary";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { Chip } from "../components/ui/Primitives";
import { QuestionPreview } from "../components/host/QuestionPreview";
import { FormattedText } from "../components/host/FormattedText";
import { useStageEnter } from "../motion/useStageMotion";
import type { RoundResult } from "../types";

export function QuestionView() {
    const q = useGameStore((s) => s.currentQuestion);
    const status = useGameStore((s) => s.status);
    const gameMode = useGameStore((s) => s.gameMode);
    const roomMode = useGameStore((s) => s.room?.gameMode);
    const joined = useGameStore((s) => s.playerUuid !== null);
    const hasPin = useGameStore((s) => s.pin !== null);
    const resume = useGameStore((s) => s.resume);
    const submit = useGameStore((s) => s.submit);
    const wsError = useGameStore((s) => s.error);
    const lastResult = useGameStore((s) => s.lastResult);
    const round =
        lastResult && "scores" in lastResult ? (lastResult as unknown as RoundResult) : null;
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
    const resumeTried = useRef(false);
    const stageRef = useStageEnter<HTMLDivElement>([q?.id, status]);

    const boardLocked = boardDelayedForMode(gameMode) && status === "ACTIVE";

    useEffect(() => {
        if (resumeTried.current || hasPin) return;
        resumeTried.current = true;
        resume();
    }, [hasPin, resume]);

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
                ? `${sub.passed}/${sub.totalTests} tests passed, +${sub.score}${aiNote}`
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
            <div className="pattern-exam min-h-[100dvh] flex flex-col">
                <header className="border-b border-[var(--oq-border)] bg-[var(--oq-surface)]">
                    <div className="page-shell flex items-center justify-between gap-4 py-3">
                        <p className="label-caps">Round complete</p>
                        {feedback && (
                            <p
                                aria-live="polite"
                                className={`text-sm font-bold ${feedback.ok ? "text-[var(--oq-success)]" : "text-[var(--oq-danger)]"}`}
                            >
                                {feedback.text}
                            </p>
                        )}
                    </div>
                </header>
                <main ref={stageRef} className="page-shell w-full max-w-5xl flex-1 py-8">
                    {q ? (
                        <div className="stage arch p-8 md:p-10">
                            <QuestionPreview question={q} round={round} />
                            {round?.correctAnswer &&
                                typeof round.correctAnswer.explanation === "string" && (
                                    <div className="mt-8 border-t border-[var(--stage-border)] pt-6">
                                        <p className="label-caps mb-2 text-[var(--stage-ink-soft)]">
                                            Why
                                        </p>
                                        <FormattedText
                                            text={round.correctAnswer.explanation}
                                            className="text-[var(--stage-ink-soft)]"
                                        />
                                    </div>
                                )}
                            <p className="mt-8 border-t border-[var(--stage-border)] pt-5 text-sm text-[var(--stage-ink-soft)]">
                                {roomMode === "PRACTICE" || gameMode === "PRACTICE"
                                    ? "Next question coming up."
                                    : "The host will bring the next question when the room is ready."}
                            </p>
                        </div>
                    ) : (
                        <Card className="p-6 text-center">
                            <p className="text-[var(--oq-ink-soft)]">
                                The host is preparing the next round.
                            </p>
                        </Card>
                    )}
                </main>
            </div>
        );
    }

    if (!q || end === null) {
        // Pin-less arrival (refresh wiped the session): infinite
        // "connecting" helps nobody — point back to an entry point.
        if (!joined && !hasPin) {
            return (
                <div className="pattern-exam min-h-[100dvh] flex items-center justify-center p-4">
                    <div className="text-center w-full max-w-sm">
                        <p className="label-caps mb-2">No game</p>
                        <p className="text-[var(--oq-ink-soft)] mb-4">
                            You haven&apos;t joined a game yet.
                        </p>
                        <p className="text-sm font-bold">
                            <Link to="/join" className="underline underline-offset-4">
                                Join a game
                            </Link>
                            <span aria-hidden="true" className="mx-3 opacity-60">
                                |
                            </span>
                            <Link to="/practice" className="underline underline-offset-4">
                                Practice solo
                            </Link>
                        </p>
                    </div>
                </div>
            );
        }
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
                        {!joined
                            ? "Connecting to the game…"
                            : roomMode === "PRACTICE" || gameMode === "PRACTICE"
                              ? "Preparing your first question…"
                              : "Waiting for the host to start the next question."}
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
    const description = q.description && q.description !== q.title ? q.description : null;
    const hasResponse = coding || response !== null;

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
        <div className="pattern-exam min-h-[100dvh] flex flex-col">
            <header className="border-b border-[var(--oq-border)] bg-[var(--oq-surface)]">
                <div className="page-shell flex min-w-0 flex-wrap items-center gap-4 py-3">
                    <span className="label-caps min-w-0 truncate">
                        {q.type.replace(/_/g, " ")}
                    </span>
                    <span className="mono text-sm text-[var(--oq-ink-soft)]">
                        {q.pointsBase} points
                    </span>
                    <div className="flex-1" />
                    {boardLocked && gameMode === "EXAM" && (
                        <Chip tone="neutral">Board hidden until the end</Chip>
                    )}
                    {untimed ? (
                        <span className="chip chip-neutral">Untimed</span>
                    ) : (
                        <CircularTimer
                            endEpochMs={end}
                            totalSec={totalSec}
                            onExpire={() => doSubmit()}
                        />
                    )}
                </div>
            </header>

            <main
                ref={stageRef}
                className={`page-shell w-full flex-1 py-6 md:py-10 ${
                    coding
                        ? "grid min-h-0 gap-8 overflow-hidden lg:grid-cols-2"
                        : "flex max-w-4xl flex-col"
                }`}
            >
                <section
                    className={`min-w-0 ${coding ? "flex flex-col overflow-y-auto" : ""}`}
                    aria-live="polite"
                >
                    <p className="label-caps mb-3">Question</p>
                    <h1 className="text-3xl font-black leading-[1.08] tracking-[-0.03em] md:text-4xl">
                        {q.title}
                    </h1>
                    {description && (
                        <FormattedText
                            text={description}
                            className="mt-4 text-[var(--oq-ink-soft)] md:text-lg"
                        />
                    )}
                    {coding && (
                        <p className="mt-5 border-l-2 border-[var(--oq-accent)] pl-4 text-sm leading-relaxed text-[var(--oq-ink-soft)]">
                            Write code that passes the hidden tests. Run it below to check your
                            output before submitting.
                        </p>
                    )}
                    {feedback && (
                        <div
                            aria-live="polite"
                            className={`feedback-banner mt-5 ${feedback.ok ? "ok" : "bad"}`}
                        >
                            {feedback.text}
                        </div>
                    )}
                </section>

                <section
                    className={`min-w-0 flex flex-col gap-4 ${
                        coding ? "min-h-0 overflow-hidden" : "mt-8"
                    }`}
                >
                    <ErrorBoundary key={q.id}>
                        <QuestionRendererHost
                            question={q}
                            onResponse={setResponse}
                            revealSignal={0}
                        />
                    </ErrorBoundary>

                    <Button
                        onClick={doSubmit}
                        disabled={submitted || !hasResponse}
                        className={`w-full font-bold shrink-0 ${shake ? "question-shake" : ""}`}
                    >
                        {submitted
                            ? "Answer locked in"
                            : coding
                              ? "Compile and submit"
                              : "Lock in answer"}
                    </Button>
                </section>
            </main>
        </div>
    );
}
