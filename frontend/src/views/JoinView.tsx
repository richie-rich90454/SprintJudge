import { useEffect, useState } from "react";
import { Card, CardContent, Button } from "@heroui/react";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { useEnter, useStaggerIn } from "../hooks/useMotion";
import { LogoMark } from "../components/LogoMark";
import { ThemeToggle } from "../components/ThemeToggle";

export function JoinView() {
    const [pin, setPin] = useState("");
    const [name, setName] = useState("");
    const join = useGameStore((s) => s.join);
    const connect = useGameStore((s) => s.connect);
    const error = useGameStore((s) => s.error);
    const setView = useUIStore((s) => s.setView);

    const cardRef = useEnter<HTMLFormElement>("card");
    const fieldsRef = useStaggerIn<HTMLDivElement>(".oq-field", []);

    useEffect(() => {
        const el = cardRef.current;
        if (!el) return;
        if (error) {
            el.classList.add("animate-pulse");
        } else {
            el.classList.remove("animate-pulse");
        }
    }, [error]);

    const submit = (e: React.FormEvent) => {
        e.preventDefault();
        if (!pin.trim() || !name.trim()) return;
        const proto = location.protocol === "https:" ? "wss" : "ws";
        connect(`${proto}://${location.host}/ws`);
        join(pin.trim(), name.trim());
        setView("play");
    };

    return (
        <div className="pattern-exam min-h-screen flex flex-col">
            <header className="border-b border-[var(--oq-border)]">
                <div className="page-shell py-3 flex items-center justify-between">
                    <div className="flex items-center gap-2.5">
                        <LogoMark size={28} />
                        <span className="font-extrabold tracking-tight">SprintJudge</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <ThemeToggle />
                    </div>
                </div>
            </header>

            <main className="flex-1 flex items-center justify-center p-6">
                <form ref={cardRef} onSubmit={submit} className="w-full max-w-lg text-center">
                    <p className="label-caps mb-4">Join a live game</p>
                    <h1
                        className="font-extrabold tracking-tight leading-none mb-10"
                        style={{ fontSize: "clamp(40px, 8vw, 88px)" }}
                    >
                        Sprint<span style={{ color: "var(--oq-accent)" }}>Judge</span>
                    </h1>

                    <Card className="bg-[var(--oq-surface)]">
                        <CardContent className="p-6">
                            <div ref={fieldsRef} className="flex flex-col gap-5">
                                <div className="oq-field text-left">
                                    <label className="label-caps block mb-1" htmlFor="jq-name">
                                        Your name
                                    </label>
                                    <input
                                        id="jq-name"
                                        value={name}
                                        onChange={(e) => setName(e.target.value)}
                                        placeholder="Alice"
                                        maxLength={20}
                                        className="input-underline"
                                    />
                                </div>
                                <div className="oq-field text-left">
                                    <label className="label-caps block mb-1" htmlFor="jq-pin">
                                        Game PIN
                                    </label>
                                    <input
                                        id="jq-pin"
                                        value={pin}
                                        onChange={(e) =>
                                            setPin(e.target.value.replace(/\D/g, "").slice(0, 6))
                                        }
                                        inputMode="numeric"
                                        autoComplete="off"
                                        className="input-underline text-center mono font-bold tracking-[0.25em] text-2xl"
                                        placeholder="000000"
                                    />
                                </div>
                            </div>

                            {error && (
                                <p
                                    role="alert"
                                    className="text-[var(--oq-danger)] text-sm text-left mt-4"
                                >
                                    {error}
                                </p>
                            )}

                            <Button
                                type="submit"
                                size="lg"
                                className="btn btn-primary w-full mt-4 font-bold"
                                isDisabled={pin.length !== 6 || !name.trim()}
                            >
                                Let us go
                            </Button>
                        </CardContent>
                    </Card>
                </form>
            </main>

            <footer className="border-t border-[var(--oq-border)] py-3">
                <div className="page-shell flex justify-between text-xs text-[var(--oq-ink-soft)]">
                    <span>GPLv3</span>
                    <span>Self-hosted · Real-time code judge</span>
                </div>
            </footer>
        </div>
    );
}
