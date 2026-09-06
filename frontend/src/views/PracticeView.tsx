import { useState, type FormEvent } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { Shell } from "../components/Shell";
import { Card } from "../components/ui/Card";
import { Button } from "../components/ui/Button";
import { TextInput } from "../components/ui/TextInput";
import { Field, EmptyState, Skeleton } from "../components/ui/Primitives";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { audio } from "../services/AudioEngine";
import { adminApi } from "../services/AdminApiService";

/**
 * Solo practice: pick a question bank, type a name, drill. The server
 * spins up a PRACTICE-mode room on the chosen bank — no PIN to hunt down,
 * no admin area involved.
 */
export function PracticeView() {
    const [name, setName] = useState("");
    const [bankId, setBankId] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const connect = useGameStore((s) => s.connect);
    const join = useGameStore((s) => s.join);
    const setPin = useUIStore((s) => s.setPin);
    const navigate = useNavigate();
    const { data: banks, isPending, isError, refetch } = useQuery({
        queryKey: ["banks"],
        queryFn: () => adminApi.listBanks(),
    });

    const canSubmit = name.trim().length > 0 && bankId !== null && !busy;

    const onSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (!canSubmit || bankId === null) return;
        setBusy(true);
        setError(null);
        try {
            const { pinCode } = await adminApi.startPractice(bankId);
            const proto = location.protocol === "https:" ? "wss" : "ws";
            connect(`${proto}://${location.host}/ws`);
            join(pinCode, name.trim());
            setPin(pinCode);
            audio.play("start");
            navigate({ to: "/play" });
        } catch {
            setError("Could not start a practice set. Try again.");
            setBusy(false);
        }
    };

    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6 max-w-xl">
                <div>
                    <p className="label-caps mb-3">Solo practice</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">
                        Drill at your own pace
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        Pick a question bank first. Untimed, with instant feedback after
                        every answer and automatic advance to the next question. No host,
                        no waiting.
                    </p>
                </div>
                <Card className="p-6">
                    <form onSubmit={onSubmit} className="flex flex-col gap-5 text-left">
                        <fieldset>
                            <legend className="label-caps mb-3">Question bank</legend>
                            {isPending && (
                                <div className="flex flex-col gap-2" aria-label="Loading banks">
                                    <Skeleton className="h-14" />
                                    <Skeleton className="h-14" />
                                </div>
                            )}
                            {isError && (
                                <EmptyState
                                    title="Could not load banks"
                                    hint="Check your connection and try again."
                                    action={
                                        <Button
                                            variant="secondary"
                                            onClick={() => refetch()}
                                        >
                                            Retry
                                        </Button>
                                    }
                                />
                            )}
                            {banks && banks.length === 0 && (
                                <EmptyState
                                    title="No question banks yet"
                                    hint="Ask your teacher to publish one."
                                />
                            )}
                            {banks && banks.length > 0 && (
                                <div className="flex flex-col gap-2" role="radiogroup">
                                    {banks.map((bank) => (
                                        <label
                                            key={bank.id}
                                            className={`flex items-start gap-3 p-3 border-2 cursor-pointer ${
                                                bankId === bank.id
                                                    ? "border-[var(--oq-accent)]"
                                                    : "border-[var(--oq-border)]"
                                            }`}
                                        >
                                            <input
                                                type="radio"
                                                name="bank"
                                                value={bank.id}
                                                checked={bankId === bank.id}
                                                onChange={() => setBankId(bank.id)}
                                                className="mt-1"
                                            />
                                            <span>
                                                <span className="block font-bold">{bank.title}</span>
                                                {bank.description && (
                                                    <span className="block text-sm text-[var(--oq-ink-soft)]">
                                                        {bank.description}
                                                    </span>
                                                )}
                                            </span>
                                        </label>
                                    ))}
                                </div>
                            )}
                        </fieldset>
                        <Field label="Your name" htmlFor="practice-name">
                            <TextInput
                                id="practice-name"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                placeholder="Alice"
                                maxLength={20}
                                autoComplete="nickname"
                            />
                        </Field>
                        {error && (
                            <p role="alert" className="text-[var(--oq-danger)] text-sm">
                                {error}
                            </p>
                        )}
                        <Button
                            type="submit"
                            size="lg"
                            disabled={!canSubmit}
                            className="w-full font-bold btn-kahoot"
                        >
                            {busy ? "Starting…" : "Start practicing"}
                        </Button>
                    </form>
                </Card>
            </div>
        </Shell>
    );
}
