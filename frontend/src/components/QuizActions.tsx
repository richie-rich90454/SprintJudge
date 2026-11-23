import { useState } from "react";
import { Button } from "../ui/Button";
import { adminApi, type QuestionPayload } from "../services/AdminApiService";

type Mode = "duplicate" | "remix";

function shuffled<T>(items: T[]): T[] {
    const out = [...items];
    for (let i = out.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [out[i], out[j]] = [out[j], out[i]];
    }
    return out;
}

/** Copy a quiz into a new one; remix also reorders its questions. */
export function QuizActions({
    quizId,
    title,
    description,
}: {
    quizId?: string | null;
    title: string;
    description?: string | null;
}) {
    const [busy, setBusy] = useState<Mode | null>(null);
    const [message, setMessage] = useState<string | null>(null);

    const run = async (mode: Mode) => {
        if (!quizId || busy) return;
        setBusy(mode);
        setMessage(null);
        try {
            const questions = await adminApi.getQuestions(quizId);
            const ordered =
                mode === "remix"
                    ? shuffled(questions)
                    : [...questions].sort((a, b) => a.orderIndex - b.orderIndex);
            const suffix = mode === "remix" ? "remix" : "copy";
            const built = await adminApi.createQuiz({
                title: `${title} (${suffix})`,
                description: description ?? "",
            });
            for (const q of ordered) {
                const payload: QuestionPayload = { ...q, quizId: built.id, id: undefined };
                await adminApi.addQuestion(payload);
            }
            setMessage(`${suffix[0].toUpperCase()}${suffix.slice(1)} created in the dashboard.`);
        } catch {
            setMessage("Could not copy this quiz. Sign in as admin and retry.");
        } finally {
            setBusy(null);
        }
    };

    return (
        <span className="inline-flex flex-wrap items-center gap-2">
            <Button
                variant="secondary"
                size="sm"
                disabled={!quizId || busy !== null}
                title={quizId ? undefined : "Open a quiz to duplicate or remix it"}
                onClick={() => void run("duplicate")}
            >
                {busy === "duplicate" ? "Copying" : "Duplicate"}
            </Button>
            <Button
                variant="secondary"
                size="sm"
                disabled={!quizId || busy !== null}
                title={quizId ? undefined : "Open a quiz to duplicate or remix it"}
                onClick={() => void run("remix")}
            >
                {busy === "remix" ? "Remixing" : "Remix"}
            </Button>
            {message && (
                <span role="status" className="text-xs text-[var(--oq-ink-soft)]">
                    {message}
                </span>
            )}
        </span>
    );
}
