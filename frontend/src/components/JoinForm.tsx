import { useState, type FormEvent } from "react";
import { useNavigate } from "@tanstack/react-router";
import { Button } from "./ui/Button";
import { TextInput } from "./ui/TextInput";
import { Field } from "./ui/Primitives";
import { AvatarPicker } from "./AvatarPicker";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { audio } from "../services/AudioEngine";

interface JoinFormProps {
    initialPin?: string;
    heading?: string;
}

function sanitizePin(value: string): string {
    return value.replace(/\D/g, "").slice(0, 6);
}

export function JoinForm({ initialPin = "", heading }: JoinFormProps) {
    const [name, setName] = useState("");
    const [pin, setPinState] = useState(() => sanitizePin(initialPin));
    const error = useGameStore((s) => s.error);
    const connect = useGameStore((s) => s.connect);
    const join = useGameStore((s) => s.join);
    const setPin = useUIStore((s) => s.setPin);
    const avatar = useUIStore((s) => s.avatar);
    const navigate = useNavigate();

    const canSubmit = pin.length === 6 && name.trim().length > 0;

    const onSubmit = (e: FormEvent) => {
        e.preventDefault();
        if (!canSubmit) return;
        const cleanName = name.trim();
        const proto = location.protocol === "https:" ? "wss" : "ws";
        connect(`${proto}://${location.host}/ws`);
        join(pin, cleanName);
        setPin(pin);
        audio.play("start");
        navigate({ to: "/play" });
    };

    return (
        <form onSubmit={onSubmit} className="flex flex-col gap-5 text-left">
            {heading && <p className="label-caps">{heading}</p>}
            <Field label="Your name" htmlFor="join-name">
                <TextInput
                    id="join-name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Alice"
                    maxLength={20}
                    autoComplete="nickname"
                />
            </Field>
            <Field label="Game PIN" htmlFor="join-pin">
                <TextInput
                    id="join-pin"
                    value={pin}
                    onChange={(e) => setPinState(sanitizePin(e.target.value))}
                    inputMode="numeric"
                    autoComplete="off"
                    placeholder="123456"
                    maxLength={6}
                    className="text-center mono font-bold tracking-[0.25em] text-2xl"
                />
            </Field>
            <div className="flex items-center gap-3">
                <span
                    className="avatar-disc"
                    style={{ width: 40, height: 40, fontSize: 20 }}
                    aria-hidden="true"
                >
                    {avatar}
                </span>
                <details>
                    <summary className="cursor-pointer text-sm font-semibold text-[var(--oq-ink-soft)]">
                        Change avatar
                    </summary>
                    <div className="mt-2">
                        <AvatarPicker />
                    </div>
                </details>
            </div>
            {error && (
                <p role="alert" className="text-[var(--oq-danger)] text-sm">
                    {error}
                </p>
            )}
            <Button type="submit" size="lg" disabled={!canSubmit} className="w-full font-bold">
                Join game
            </Button>
        </form>
    );
}
