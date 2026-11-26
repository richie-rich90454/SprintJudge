import { useI18n } from "../i18n";
import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "@tanstack/react-router";
import { Button } from "../ui/Button";
import { TextInput } from "../ui/TextInput";
import { Field } from "../ui/Primitives";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { audio } from "../services/AudioEngine";

interface JoinFormProps {
    initialPin?: string;
    heading?: string;
    submitClassName?: string;
}

function sanitizePin(value: string): string {
    return value.replace(/\D/g, "").slice(0, 6);
}

export function JoinForm({ initialPin = "", heading, submitClassName = "" }: JoinFormProps) {
    const { t } = useI18n();
    const [name, setName] = useState("");
    const [pin, setPinState] = useState(() => sanitizePin(initialPin));
    const [joining, setJoining] = useState(false);

    const error = useGameStore((s) => s.error);
    const playerUuid = useGameStore((s) => s.playerUuid);
    const clearError = useGameStore((s) => s.clearError);
    const connect = useGameStore((s) => s.connect);
    const join = useGameStore((s) => s.join);
    const setPin = useUIStore((s) => s.setPin);
    const navigate = useNavigate();

    // Only leave the form once the server confirms the seat. Navigating early
    // stranded users on a Standby screen with an error and no way back.
    useEffect(() => {
        if (joining && playerUuid) navigate({ to: "/play" });
    }, [joining, playerUuid, navigate]);

    const canSubmit = pin.length === 6 && name.trim().length > 0;

    const onSubmit = (e: FormEvent) => {
        e.preventDefault();
        if (!canSubmit) return;
        const cleanName = name.trim();
        clearError();
        setJoining(true);
        const proto = location.protocol === "https:" ? "wss" : "ws";
        connect(`${proto}://${location.host}/ws`);
        join(pin, cleanName);
        setPin(pin);
        audio.play("start");
    };

    return (
        <form onSubmit={onSubmit} className="flex flex-col gap-5 text-left">
            {heading && <p className="label-caps">{heading}</p>}
            <Field label={t("ui.yourName")} htmlFor="join-name">
                <TextInput
                    id="join-name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder={t("ui.alice")}
                    maxLength={20}
                    autoComplete="nickname"
                />
            </Field>
            <Field label={t("ui.gamePIN")} htmlFor="join-pin">
                <TextInput
                    id="join-pin"
                    value={pin}
                    onChange={(e) => setPinState(sanitizePin(e.target.value))}
                    inputMode="numeric"
                    autoComplete="off"
                    placeholder={t("ui.pinExample")}
                    maxLength={6}
                    className="text-center mono font-bold tracking-[0.25em] text-2xl"
                />
            </Field>
            {error && (
                <p role="alert" className="text-[var(--oq-danger)] text-sm">
                    {error}
                </p>
            )}
            {!canSubmit && (pin.length > 0 || name.length > 0) && (
                <p className="text-[var(--oq-ink-soft)] text-sm" role="status">
                    {!name.trim()
                        ? t("ui.enterYourNameToJoin")
                        : t("ui.pinDigits", { count: pin.length })}
                </p>
            )}
            <Button
                type="submit"
                size="lg"
                disabled={!canSubmit}
                className={`w-full font-bold ${submitClassName}`.trim()}
            >
                {t("ui.joinGame")}
            </Button>
        </form>
    );
}
