import { useState, useEffect } from "react";
import { Card, CardContent, Button } from "@heroui/react";
import { useGameStore } from "../stores/useGameStore";
import { useTimerStore } from "../stores/useTimerStore";
import { QuestionRendererHost } from "../components/QuestionRendererHost";
import { CircularTimer } from "../components/Timer/CircularTimer";
import { isCoding } from "../services/ScoringService";
import { useEnter, useStaggerIn } from "../hooks/useMotion";
import { Confetti } from "../components/Confetti";
import { QuestionDto } from "../types";

export function QuestionView() {
    const q = useGameStore((s) => s.currentQuestion) as QuestionDto | null;
    const status = useGameStore((s) => s.status);
    const submit = useGameStore((s) => s.submit);
    const end = useTimerStore((s) => s.endEpochMs);
    const [response, setResponse] = useState<unknown>(null);
    const [submitted, setSubmitted] = useState(false);

    // Reset lock-in and answer when a new question arrives. Without this, locking
    // in Q1 leaves `submitted` true forever and disables the button for every
    // following question ("all questions don't work").
    useEffect(() => {
        setSubmitted(false);
        setResponse(null);
    }, [q?.id]);

    const cardRef = useEnter<HTMLDivElement>("card", [q?.id]);
    const optionsRef = useStaggerIn<HTMLDivElement>(".renderer-host button", [q?.id], 0.05);
    const barRef = useEnter<HTMLButtonElement>("bar", [q?.id]);

    if (status === "REVIEW") {
        return (
            <div className="pattern-exam min-h-screen flex items-center justify-center p-4">
                <Confetti fireKey={q?.id ?? "review"} />
                <Card className="bg-content1 text-center max-w-md w-full">
                    <CardContent className="p-8 gap-2">
                        <p className="label-caps mb-2">Round complete</p>
                        <h2 className="text-2xl font-extrabold">Answers locked.</h2>
                        <p className="text-default-500 mt-2">
                            The host is preparing the next round.
                        </p>
                    </CardContent>
                </Card>
            </div>
        );
    }

    if (!q || !end) {
        return (
            <div className="pattern-exam min-h-screen flex items-center justify-center">
                <div className="text-center">
                    <p className="label-caps mb-2">Standby</p>
                    <p className="text-default-500">
                        Waiting for the host to start the next question.
                    </p>
                </div>
            </div>
        );
    }

    const doSubmit = () => {
        submit(
            q.id,
            response,
            isCoding(q.type) ? (response as { language?: string })?.language : undefined,
        );
        setSubmitted(true);
        try {
            localStorage.removeItem(`sprintjudge_code_${q.id}`);
        } catch {
            /* ignore */
        }
    };

    return (
        <div className="pattern-exam min-h-screen flex flex-col items-center p-4 md:p-8">
            <div className="page-shell w-full max-w-3xl">
                <div
                    className="flex items-end justify-between border-b-2 pb-3"
                    style={{ borderColor: "var(--oq-red)" }}
                >
                    <span className="label-caps">{q.type.replace(/_/g, " ")}</span>
                    <span className="mono text-sm text-default-500">{q.pointsBase} pts</span>
                </div>

                <div ref={cardRef}>
                    <Card className="bg-content1 mt-6 relative pb-8">
                        <CardContent className="pt-6 relative">
                            <div className="absolute -top-4 -right-4 md:-top-6 md:-right-6 z-10">
                                <CircularTimer
                                    endEpochMs={end}
                                    totalSec={q.timeLimitSec}
                                    onExpire={() => !submitted && doSubmit()}
                                />
                            </div>

                            <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight pr-24 md:pr-28">
                                {q.title}
                            </h1>
                            {q.description && (
                                <p className="text-default-500 mt-3 whitespace-pre-wrap leading-relaxed pr-16">
                                    {q.description}
                                </p>
                            )}
                            <div className="h-px bg-default-200 my-5" />

                            <div ref={optionsRef}>
                                <QuestionRendererHost question={q} onResponse={setResponse} />
                            </div>

                            <Button
                                ref={barRef}
                                onPress={doSubmit}
                                isDisabled={submitted}
                                variant="primary"
                                size="lg"
                                className="w-full mt-8 font-bold bg-[var(--oq-red)] text-white"
                            >
                                {submitted
                                    ? "Answer locked in"
                                    : isCoding(q.type)
                                      ? "Compile and submit"
                                      : "Lock in answer"}
                            </Button>
                        </CardContent>
                    </Card>
                </div>
            </div>
        </div>
    );
}
