import { useEffect } from "react";
import { Link, useParams } from "@tanstack/react-router";
import { HostStage } from "../components/host/HostStage";
import { useGameStore } from "../stores/useGameStore";
import { useJoinUrl } from "../hooks/useJoinUrl";

/**
 * Read-only projector. Joins as a spectator so it never takes the host seat
 * (a second host connection is rejected by design) and never scores.
 */
export function SpectatorView() {
    const { pin } = useParams({ strict: false }) as { pin: string };
    const connect = useGameStore((s) => s.connect);
    const join = useGameStore((s) => s.join);
    const room = useGameStore((s) => s.room);
    const wsError = useGameStore((s) => s.error);
    const joinUrl = useJoinUrl(pin);

    useEffect(() => {
        if (!pin) return;
        const proto = window.location.protocol === "https:" ? "wss" : "ws";
        connect(`${proto}://${window.location.host}/ws`);
        join(pin, "Projector", "spectator");
    }, [pin, connect, join]);

    if (!room) {
        return (
            <div className="pattern-exam flex min-h-[100dvh] items-center justify-center p-6">
                <div className="text-center">
                    <p className="label-caps mb-2">Projector</p>
                    <p className="text-[var(--oq-ink-soft)]">Connecting to room {pin}…</p>
                    {wsError && (
                        <p role="alert" className="mt-3 text-sm text-[var(--oq-danger)]">
                            {wsError}
                        </p>
                    )}
                    <p className="mt-6 text-sm">
                        <Link to="/" className="underline underline-offset-4">
                            Back
                        </Link>
                    </p>
                </div>
            </div>
        );
    }

    return (
        <div className="pattern-exam min-h-[100dvh] p-4 md:p-8">
            <HostStage pin={pin} joinUrl={joinUrl} projector />
            <p className="mt-4 text-center font-mono text-xs text-[var(--oq-ink-soft)]">
                Projector view. Read only.
            </p>
        </div>
    );
}
