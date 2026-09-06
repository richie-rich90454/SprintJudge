import { useState, type FormEvent } from "react";
import { useNavigate } from "@tanstack/react-router";
import { Shell } from "../components/Shell";
import { Card } from "../components/ui/Card";
import { Button } from "../components/ui/Button";
import { TextInput } from "../components/ui/TextInput";
import { Field } from "../components/ui/Primitives";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { audio } from "../services/AudioEngine";
import { adminApi } from "../services/AdminApiService";

/**
 * One-click solo practice. The server hands out a PRACTICE-mode room on
 * the practice set — no PIN to hunt down, no admin area involved.
 */
export function PracticeView() {
    const [name, setName] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const connect = useGameStore((s) => s.connect);
    const join = useGameStore((s) => s.join);
    const setPin = useUIStore((s) => s.setPin);
    const navigate = useNavigate();

    const onSubmit = async (e: FormEvent) => {
        e.preventDefault();
        const cleanName = name.trim();
        if (!cleanName || busy) return;
        setBusy(true);
        setError(null);
        try {
            const { pinCode } = await adminApi.startPractice();
            const proto = location.protocol === "https:" ? "wss" : "ws";
            connect(`${proto}://${location.host}/ws`);
            join(pinCode, cleanName);
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
                        Untimed, with instant feedback after every answer and automatic
                        advance to the next question. No host, no waiting.
                    </p>
                </div>
                <Card className="p-6">
                    <form onSubmit={onSubmit} className="flex flex-col gap-5 text-left">
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
                            disabled={!name.trim() || busy}
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
