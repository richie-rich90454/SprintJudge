import { useI18n } from "../i18n";
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
    const { t } = useI18n();
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
            setMessage(
                t("ui.createdInDashboard", {
                    kind: `${suffix[0].toUpperCase()}${suffix.slice(1)}`,
                }),
            );
        } catch {
            setMessage(t("ui.couldNotCopyThisQuizSignInAsAdminAndRetry"));
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
                title={quizId ? undefined : t("ui.openAQuizToDuplicateOrRemixIt")}
                onClick={() => void run("duplicate")}
            >
                {busy === "duplicate" ? t("ui.copying") : t("ui.duplicate")}
            </Button>
            <Button
                variant="secondary"
                size="sm"
                disabled={!quizId || busy !== null}
                title={quizId ? undefined : t("ui.openAQuizToDuplicateOrRemixIt")}
                onClick={() => void run("remix")}
            >
                {busy === "remix" ? t("ui.remixing") : t("ui.remix")}
            </Button>
            {message && (
                <span role="status" className="text-xs text-[var(--oq-ink-soft)]">
                    {message}
                </span>
            )}
        </span>
    );
}
