import { useState, useEffect } from "react";
import { Button } from "@heroui/react";
import { useGameStore } from "../stores/useGameStore";
import { webSocketService, WsMessage } from "../services/WebSocketService";

export function HostControlsView() {
    const hostCommand = useGameStore((s) => s.hostCommand);
    const extendTimer = useGameStore((s) => s.extendTimer);
    const kickPlayer = useGameStore((s) => s.kick);
    const status = useGameStore((s) => s.status);
    const room = useGameStore((s) => s.room);
    const wsError = useGameStore((s) => s.error);
    const [teamName, setTeamName] = useState("");
    const [teamsLoading, setTeamsLoading] = useState(false);
    const [teamsError, setTeamsError] = useState<string | null>(null);
    const [teams, setTeams] = useState<
        { id: string; name: string; memberUuids: string[]; score: number }[]
    >([]);

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
        <div className="card !p-0 overflow-hidden bg-[var(--oq-surface)]">
            <div className="px-6 pt-6 pb-6 border-b border-[var(--oq-border)]">
                <h3 className="font-extrabold text-lg">Controls</h3>
                <p className="label-caps mt-1">{active ? "Round live" : "Awaiting start"}</p>
            </div>

            <div className="p-6 flex flex-col gap-4">
                {active ? (
                    <>
                        <Button
                            className="btn btn-primary w-full"
                            onPress={() => hostCommand("FORCE_SUBMIT")}
                        >
                            Force submit
                        </Button>
                        <Button
                            className="btn btn-secondary w-full"
                            onPress={() => extendTimer(30)}
                        >
                            +30 seconds
                        </Button>
                    </>
                ) : (
                    <>
                        <Button
                            className="btn btn-primary w-full"
                            onPress={() => hostCommand("NEXT_QUESTION")}
                        >
                            {status === "LOBBY" ? "Start round" : "Next question"}
                        </Button>
                        {status === "LOBBY" && (
                            <Button
                                className="btn btn-secondary w-full"
                                onPress={() => send({ type: "START_BATTLE" })}
                            >
                                Start battle
                            </Button>
                        )}
                    </>
                )}
                <Button className="btn btn-danger w-full" onPress={confirmEnd}>
                    End game
                </Button>
            </div>

            <div className="px-6 pb-6 pt-6 border-t border-[var(--oq-border)]">
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
                    <input
                        value={teamName}
                        onChange={(e) => setTeamName(e.target.value)}
                        placeholder="Team name"
                        aria-label="Team name"
                        className="input-underline flex-1 text-sm"
                        onKeyDown={(e) => e.key === "Enter" && createTeam()}
                    />
                    <Button className="btn btn-secondary btn-sm" onPress={createTeam}>
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

            <div className="px-6 pb-6 pt-6 border-t border-[var(--oq-border)]">
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
        </div>
    );
}
