import { useEffect, useRef } from "react";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { useTimerStore } from "../stores/useTimerStore";
import { HostControlsView } from "./HostControlsView";
import { HostLeaderboardView } from "./HostLeaderboardView";
import { CircularTimer } from "../components/Timer/CircularTimer";
import { ThemeToggle } from "../components/ThemeToggle";
import { webSocketService } from "../services/WebSocketService";

export function HostLobbyView() {
    const pin = useUIStore((s) => s.pin);
    const connect = useGameStore((s) => s.connect);
    const join = useGameStore((s) => s.join);
    const room = useGameStore((s) => s.room);
    const wsError = useGameStore((s) => s.error);
    const q = useGameStore((s) => s.currentQuestion);
    const end = useTimerStore((s) => s.endEpochMs);

    const playerCount = room?.players.length ?? 0;
    const prevCount = useRef(0);

    useEffect(() => {
        if (!pin) return;
        const proto = location.protocol === "https:" ? "wss" : "ws";
        connect(`${proto}://${location.host}/ws`);
        join(pin, "Host", "host");
        return () => webSocketService.disconnect();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [pin]);

    useEffect(() => {
        prevCount.current = playerCount;
    }, [playerCount]);

    if (!pin) {
        return (
            <div className="min-h-screen flex items-center justify-center">
                <p style={{ color: "var(--oq-ink-soft)" }}>
                    Create a game from the Admin dashboard first.
                </p>
            </div>
        );
    }

    const statusLabel =
        room?.status === "ACTIVE" ? "Live" : room?.status === "REVIEW" ? "Reviewing" : "Lobby open";

    return (
        <div className="pattern-exam min-h-screen flex flex-col">
            {/* Compact top bar: PIN + status + timer */}
            <header className="border-b border-[var(--oq-border)] bg-[var(--oq-surface)]">
                <div className="page-shell py-4 flex items-center gap-8">
                    <div>
                        <p className="label-caps mb-1">Game PIN</p>
                        <p className="mono font-extrabold text-4xl tracking-[.2em] leading-none">
                            {pin}
                        </p>
                    </div>
                    <div className="h-12 w-px bg-[var(--oq-border)] hidden sm:block" />
                    <div className="flex-1">
                        <p className="label-caps mb-1">{statusLabel}</p>
                        <p className="font-bold text-lg">{playerCount} players</p>
                    </div>
                    {room?.status === "ACTIVE" && end && isFinite(end) && q && (
                        <CircularTimer endEpochMs={end} totalSec={q.timeLimitSec} />
                    )}
                    <ThemeToggle />
                </div>
            </header>

            {/* Main: leaderboard left (wide), controls right (slim rail) */}
            <main className="page-shell flex-1 grid md:grid-cols-[1fr_280px] gap-6 py-6 items-start w-full">
                {!room ? (
                    <div
                        className="rounded-[16px] border border-[var(--oq-border)] bg-[var(--oq-surface)] p-6"
                        aria-label="Loading room"
                    >
                        <div className="h-5 w-40 animate-pulse rounded-[10px] bg-[var(--oq-border)] mb-4" />
                        <div className="flex flex-col gap-4" aria-hidden="true">
                            {[0, 1, 2, 3].map((i) => (
                                <div
                                    key={i}
                                    className="h-11 animate-pulse rounded-[10px] bg-[var(--oq-border)] opacity-40"
                                />
                            ))}
                        </div>
                        <p className="text-[var(--oq-ink-soft)] text-sm mt-4">Loading room…</p>
                        {wsError && (
                            <p role="alert" className="text-[var(--oq-danger)] text-sm mt-2">
                                Connection error: {wsError}
                            </p>
                        )}
                    </div>
                ) : (
                    <HostLeaderboardView />
                )}
                {wsError && room && (
                    <p role="alert" className="text-[var(--oq-danger)] text-sm md:col-span-2">
                        Connection error: {wsError}
                    </p>
                )}
                <HostControlsView />
            </main>
        </div>
    );
}
