import { useEffect, useRef, useState } from "react";
import { Link, useSearch } from "@tanstack/react-router";
import { motion as fm } from "framer-motion";
import { Card } from "../components/ui/Card";
import { Button } from "../components/ui/Button";
import { TextInput } from "../components/ui/TextInput";
import { Chip } from "../components/ui/Primitives";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { useTimerStore } from "../stores/useTimerStore";
import { webSocketService, type WsMessage } from "../services/WebSocketService";
import { CircularTimer } from "../components/Timer/CircularTimer";
import { RoomQr } from "../components/RoomQr";
import { ThemeToggle } from "../components/ThemeToggle";
import { useStaggerIn } from "../hooks/useMotion";
import { useVirtualWindow } from "../hooks/useVirtualWindow";

const ROW_H = 44;
const PROJECTOR_ROW_H = 64;

interface TeamRow {
    id: string;
    name: string;
    memberUuids: string[];
    score: number;
}

function LeaderboardPanel({ projector = false }: { projector?: boolean }) {
    const leaderboard = useGameStore((s) => s.leaderboard);
    const room = useGameStore((s) => s.room);
    const wsError = useGameStore((s) => s.error);

    const rows = leaderboard.length
        ? leaderboard
        : (room?.players ?? []).map((p, i) => ({
              uuid: p.uuid,
              name: p.name,
              score: p.score,
              rank: i + 1,
          }));

    const rh = projector ? PROJECTOR_ROW_H : ROW_H;
    const maxH = projector ? 640 : 460;
    const listRef = useStaggerIn<HTMLDivElement>(".lb-row", [rows.length], 0.05);
    const { ref, start, end } = useVirtualWindow(rows.length, rh);
    const slice = rows.slice(start, end);

    return (
        <Card className="bg-[var(--oq-surface)] p-0 overflow-hidden">
            <div className="flex items-center justify-between px-6 pt-6 pb-6 border-b border-[var(--oq-border)]">
                <h3 className="header-double" style={{ marginBottom: 0 }}>
                    Leaderboard
                </h3>
                <span className="label-caps">{rows.length} players</span>
            </div>
            {wsError && (
                <p role="alert" className="text-[var(--oq-danger)] text-sm px-6 pt-4">
                    Connection error: {wsError}
                </p>
            )}

            <div ref={listRef} className="px-0 relative">
                <div
                    ref={ref}
                    aria-label="Leaderboard"
                    className="overflow-y-auto"
                    style={{ maxHeight: maxH }}
                >
                    {rows.length > 0 && (
                        <>
                            <div style={{ height: start * rh }} aria-hidden="true" />
                            {slice.map((r) => {
                                const podium = r.rank <= 3;
                                return (
                                    <fm.div
                                        key={r.uuid}
                                        layout
                                        className={`lb-row flex items-center gap-4 px-6 border-b border-dotted border-[var(--oq-border)] ${podium ? "bg-[var(--oq-row-alt)]" : ""} ${projector ? "text-2xl" : ""}`}
                                        style={{ height: rh, minHeight: 44 }}
                                    >
                                        <span
                                            className="mono font-bold min-w-8 text-right tabular-nums"
                                            style={
                                                podium
                                                    ? {
                                                          color: "var(--oq-accent)",
                                                          fontSize: projector ? 26 : 18,
                                                      }
                                                    : { color: "var(--oq-border-strong)" }
                                            }
                                        >
                                            {r.rank}
                                        </span>
                                        <span
                                            className="truncate font-semibold flex-1"
                                            title={r.name}
                                        >
                                            {r.name}
                                        </span>
                                        <span
                                            className="mono font-bold tabular-nums"
                                            style={{ fontSize: projector ? 24 : 17 }}
                                        >
                                            {(r.score ?? 0).toLocaleString()}
                                        </span>
                                    </fm.div>
                                );
                            })}
                            <div
                                style={{ height: Math.max(0, (rows.length - end) * rh) }}
                                aria-hidden="true"
                            />
                        </>
                    )}
                    {rows.length === 0 && (
                        <p className="text-[var(--oq-ink-soft)] text-sm p-6">
                            No players yet — share the PIN.
                        </p>
                    )}
                </div>
                {rows.length * rh > maxH && (
                    <div
                        className="pointer-events-none absolute bottom-0 inset-x-0 h-8"
                        style={{
                            background:
                                "linear-gradient(to bottom, transparent, var(--oq-surface))",
                        }}
                        aria-hidden="true"
                    />
                )}
            </div>
        </Card>
    );
}

function ControlsPanel() {
    const hostCommand = useGameStore((s) => s.hostCommand);
    const extendTimer = useGameStore((s) => s.extendTimer);
    const kickPlayer = useGameStore((s) => s.kick);
    const status = useGameStore((s) => s.status);
    const room = useGameStore((s) => s.room);
    const wsError = useGameStore((s) => s.error);
    const [teamName, setTeamName] = useState("");
    const [teamsLoading, setTeamsLoading] = useState(false);
    const [teamsError, setTeamsError] = useState<string | null>(null);
    const [teams, setTeams] = useState<TeamRow[]>([]);

    const players = room?.players ?? [];
    const active = status === "ACTIVE";

    useEffect(() => {
        const sub = webSocketService.onMessage().subscribe((m: WsMessage) => {
            if (m.type === "TEAM_LIST") {
                setTeams((m.teams as typeof teams) ?? []);
                setTeamsLoading(false);
                setTeamsError(null);
            }
            if (m.type === "ERROR") {
                setTeamsLoading(false);
                setTeamsError(String(m.message ?? "Failed to load teams."));
            }
        });
        return () => sub.unsubscribe();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const send = (msg: WsMessage) => webSocketService.send(msg);

    const requestTeams = () => {
        setTeamsLoading(true);
        setTeamsError(null);
        send({ type: "GET_TEAMS" });
    };

    const createTeam = () => {
        if (!teamName.trim()) return;
        send({ type: "CREATE_TEAM", name: teamName.trim() });
        setTeamName("");
    };

    const confirmEnd = () => {
        if (window.confirm("End the game for all players?")) hostCommand("END_GAME");
    };

    return (
        <Card className="bg-[var(--oq-surface)] overflow-hidden">
            <div className="pb-4 border-b border-[var(--oq-border)]">
                <h3 className="font-extrabold text-lg">Controls</h3>
                <p className="label-caps mt-1">{active ? "Round live" : "Awaiting start"}</p>
            </div>

            <div className="py-4 flex flex-col gap-4">
                {active ? (
                    <>
                        <Button
                            variant="primary"
                            className="w-full"
                            onClick={() => hostCommand("FORCE_SUBMIT")}
                        >
                            Force submit
                        </Button>
                        <Button
                            variant="secondary"
                            className="w-full"
                            onClick={() => extendTimer(30)}
                        >
                            +30 seconds
                        </Button>
                    </>
                ) : (
                    <>
                        <Button
                            variant="primary"
                            className="w-full"
                            onClick={() => hostCommand("NEXT_QUESTION")}
                        >
                            {status === "LOBBY" ? "Start round" : "Next question"}
                        </Button>
                        {status === "LOBBY" && (
                            <Button
                                variant="secondary"
                                className="w-full"
                                onClick={() => send({ type: "START_BATTLE" })}
                            >
                                Start battle
                            </Button>
                        )}
                    </>
                )}
                <Button variant="danger" className="w-full" onClick={confirmEnd}>
                    End game
                </Button>
            </div>

            <div className="py-4 border-t border-[var(--oq-border)]">
                <div className="flex items-center justify-between mb-3">
                    <p className="label-caps">Teams</p>
                    <button
                        type="button"
                        onClick={requestTeams}
                        className="text-[var(--oq-accent)] text-sm font-bold min-h-[44px] px-2 inline-flex items-center gap-2"
                    >
                        {teamsLoading && <span className="oq-spin" aria-hidden="true" />}
                        Retry
                    </button>
                </div>
                {teamsError && (
                    <p role="alert" className="text-[var(--oq-danger)] text-sm mb-2">
                        {teamsError}
                    </p>
                )}
                {wsError && (
                    <p role="alert" className="text-[var(--oq-danger)] text-sm mb-2">
                        {wsError}
                    </p>
                )}
                <div className="flex gap-2 mb-3">
                    <TextInput
                        value={teamName}
                        onChange={(e) => setTeamName(e.target.value)}
                        placeholder="Team name"
                        aria-label="Team name"
                        className="flex-1 text-sm"
                        onKeyDown={(e) => e.key === "Enter" && createTeam()}
                    />
                    <Button variant="secondary" size="sm" onClick={createTeam}>
                        Create
                    </Button>
                </div>
                <ul className="flex flex-col gap-4 max-h-48 overflow-y-auto">
                    {teams.map((t) => (
                        <li
                            key={t.id}
                            className="flex items-center justify-between text-sm min-h-[44px] px-3 rounded-[10px] hover:bg-[var(--oq-row-alt)] transition-colors"
                        >
                            <span className="font-medium">{t.name}</span>
                            <span className="mono text-xs text-[var(--oq-ink-soft)]">
                                {t.memberUuids.length} members · {t.score} pts
                            </span>
                        </li>
                    ))}
                    {teamsLoading && (
                        <li className="text-[var(--oq-ink-soft)] text-sm inline-flex items-center gap-2 min-h-[44px]">
                            <span className="oq-spin" aria-hidden="true" /> Loading teams…
                        </li>
                    )}
                    {!teamsLoading && teams.length === 0 && !teamsError && (
                        <li className="text-[var(--oq-ink-soft)] text-sm">No teams yet.</li>
                    )}
                </ul>
            </div>

            <div className="pt-4 border-t border-[var(--oq-border)]">
                <p className="label-caps mb-3">{players.length} players</p>
                {!room && (
                    <p className="text-[var(--oq-ink-soft)] text-sm inline-flex items-center gap-2 min-h-[44px]">
                        <span className="oq-spin" aria-hidden="true" /> Loading players…
                    </p>
                )}
                <ul className="flex flex-col gap-4 max-h-48 overflow-y-auto">
                    {players.map((p) => (
                        <li
                            key={p.uuid}
                            className="flex items-center justify-between gap-2 text-sm min-h-[44px] px-3 rounded-[10px] hover:bg-[var(--oq-row-alt)] transition-colors"
                        >
                            <span className="font-medium truncate" title={p.name}>
                                {p.name}
                            </span>
                            <span className="flex items-center gap-2 shrink-0">
                                <span className="mono text-xs text-[var(--oq-ink-soft)]">
                                    {p.score}
                                </span>
                                <button
                                    type="button"
                                    onClick={() => kickPlayer(p.uuid)}
                                    aria-label={`Kick ${p.name}`}
                                    className="text-[var(--oq-danger)] text-xs font-bold min-h-[44px] px-2"
                                >
                                    Kick
                                </button>
                            </span>
                        </li>
                    ))}
                    {room && players.length === 0 && (
                        <li className="text-[var(--oq-ink-soft)] text-sm">
                            Share the PIN to invite players.
                        </li>
                    )}
                </ul>
            </div>
        </Card>
    );
}

export function HostView() {
    const search = useSearch({ strict: false }) as unknown as {
        pin?: string;
        projector?: boolean | string;
    };
    const storePin = useUIStore((s) => s.pin);
    const connect = useGameStore((s) => s.connect);
    const join = useGameStore((s) => s.join);
    const room = useGameStore((s) => s.room);
    const wsError = useGameStore((s) => s.error);
    const q = useGameStore((s) => s.currentQuestion);
    const end = useTimerStore((s) => s.endEpochMs);
    const [copied, setCopied] = useState(false);
    const copyTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    const pin =
        (typeof search.pin === "string" && search.pin.length > 0 ? search.pin : storePin) ??
        null;
    const projector = search.projector === true || search.projector === "1";

    useEffect(() => {
        if (!pin) return;
        const proto = window.location.protocol === "https:" ? "wss" : "ws";
        connect(`${proto}://${window.location.host}/ws`);
        join(pin, "Host", "host");
        return () => webSocketService.disconnect();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [pin]);

    useEffect(() => {
        return () => {
            if (copyTimer.current) clearTimeout(copyTimer.current);
        };
    }, []);

    if (!pin) {
        return (
            <div className="min-h-screen flex items-center justify-center">
                <p style={{ color: "var(--oq-ink-soft)" }}>
                    Create a game from the Admin dashboard first.
                </p>
            </div>
        );
    }

    const playerCount = room?.players.length ?? 0;
    const statusLabel =
        room?.status === "ACTIVE" ? "Live" : room?.status === "REVIEW" ? "Reviewing" : "Lobby open";
    const tone: "success" | "accent" | "neutral" =
        room?.status === "ACTIVE" ? "success" : room?.status === "REVIEW" ? "accent" : "neutral";
    const joinUrl = `${window.location.origin}/j/${pin}`;

    const copyLink = () => {
        void navigator.clipboard.writeText(joinUrl).then(
            () => {
                setCopied(true);
                if (copyTimer.current) clearTimeout(copyTimer.current);
                copyTimer.current = setTimeout(() => setCopied(false), 2000);
            },
            () => setCopied(false),
        );
    };

    if (projector) {
        return (
            <div className="pattern-exam min-h-screen flex flex-col">
                <header className="border-b border-[var(--oq-border)] bg-[var(--oq-surface)]">
                    <div className="page-shell py-6 flex items-center gap-8">
                        <p
                            className="mono font-extrabold tracking-[.2em] leading-none"
                            style={{ fontSize: "clamp(48px,8vw,96px)" }}
                        >
                            {pin}
                        </p>
                        <div className="flex-1">
                            <p className="label-caps mb-1">{statusLabel}</p>
                            <p className="font-bold text-2xl">{playerCount} players</p>
                        </div>
                        {room?.status === "ACTIVE" && end && isFinite(end) && q && (
                            <CircularTimer endEpochMs={end} totalSec={q.timeLimitSec} />
                        )}
                    </div>
                </header>
                <main className="page-shell flex-1 py-6 w-full">
                    <LeaderboardPanel projector />
                </main>
            </div>
        );
    }

    return (
        <div className="pattern-exam min-h-screen flex flex-col">
            <header className="border-b border-[var(--oq-border)] bg-[var(--oq-surface)]">
                <div className="page-shell py-4">
                    <Card className="bg-[var(--oq-surface)]">
                        <div className="flex items-center gap-6 flex-wrap">
                            <div>
                                <p className="label-caps mb-1">Game PIN</p>
                                <p className="mono font-extrabold text-4xl tracking-[.2em] leading-none">
                                    {pin}
                                </p>
                            </div>
                            <div className="h-12 w-px bg-[var(--oq-border)] hidden sm:block" />
                            <div>
                                <div className="mb-1">
                                    <Chip tone={tone}>{statusLabel}</Chip>
                                </div>
                                <p className="font-bold text-lg">{playerCount} players</p>
                            </div>
                            <div className="flex-1" />
                            {room?.status === "ACTIVE" && end && isFinite(end) && q && (
                                <CircularTimer endEpochMs={end} totalSec={q.timeLimitSec} />
                            )}
                            {(!room || room.status === "LOBBY") && <RoomQr pin={pin} />}
                            <div className="flex items-center gap-2">
                                <Button variant="secondary" size="sm" onClick={copyLink}>
                                    {copied ? "Copied" : "Copy join link"}
                                </Button>
                                <Link
                                    to="/host"
                                    search={{
                                        pin,
                                        projector: true,
                                    }}
                                    className="btn btn-secondary btn-sm"
                                >
                                    Projector
                                </Link>
                            </div>
                            <ThemeToggle />
                        </div>
                        <p className="mono text-xs text-[var(--oq-ink-soft)] mt-3 break-all">
                            {joinUrl}
                        </p>
                    </Card>
                </div>
            </header>

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
                    <LeaderboardPanel />
                )}
                {wsError && room && (
                    <p role="alert" className="text-[var(--oq-danger)] text-sm md:col-span-2">
                        Connection error: {wsError}
                    </p>
                )}
                <ControlsPanel />
            </main>
        </div>
    );
}
