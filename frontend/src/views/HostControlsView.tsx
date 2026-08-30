import { useState, useEffect } from "react";
import { Button } from "@heroui/react";
import { useGameStore } from "../stores/useGameStore";
import { webSocketService, WsMessage } from "../services/WebSocketService";

export function HostControlsView() {
    const hostCommand = useGameStore((s) => s.hostCommand);
    const extendTimer = useGameStore((s) => s.extendTimer);
    const status = useGameStore((s) => s.status);
    const room = useGameStore((s) => s.room);
    const [teamName, setTeamName] = useState("");
    const [teams, setTeams] = useState<{ id: string; name: string; memberUuids: string[]; score: number }[]>([]);

    const players = room?.players ?? [];
    const active = status === "ACTIVE";

    useEffect(() => {
        const sub = webSocketService.onMessage().subscribe((m: any) => {
            if (m.type === "TEAM_LIST") setTeams(m.teams ?? []);
        });
        return () => sub.unsubscribe();
    }, []);

    const send = (msg: WsMessage) => webSocketService.send(msg);

    const createTeam = () => {
        if (!teamName.trim()) return;
        send({ type: "CREATE_TEAM", name: teamName.trim() });
        setTeamName("");
    };

    return (
        <div className="card !p-0 overflow-hidden bg-content1">
            <div className="px-5 pt-5 pb-4 border-b border-default-200">
                <h3 className="font-extrabold text-lg">Controls</h3>
                <p className="label-caps mt-1">{active ? "Round live" : "Awaiting start"}</p>
            </div>

            <div className="p-5 flex flex-col gap-3">
                {active ? (
                    <>
                        <Button
                            variant="primary"
                            className="w-full bg-[var(--oq-red)] text-white"
                            onPress={() => hostCommand("FORCE_SUBMIT")}
                        >
                            Force submit
                        </Button>
                        <Button
                            variant="outline"
                            className="w-full"
                            onPress={() => extendTimer(30)}
                        >
                            +30 seconds
                        </Button>
                    </>
                ) : (
                    <>
                        <Button
                            variant="primary"
                            className="w-full bg-[var(--oq-red)] text-white"
                            onPress={() => hostCommand("NEXT_QUESTION")}
                        >
                            {status === "LOBBY" ? "Start round" : "Next question"}
                        </Button>
                        {status === "LOBBY" && (
                            <Button
                                variant="outline"
                                className="w-full"
                                onPress={() => send({ type: "START_BATTLE" })}
                            >
                                Start battle
                            </Button>
                        )}
                    </>
                )}
                <Button variant="danger" className="w-full" onPress={() => hostCommand("END_GAME")}>
                    End game
                </Button>
            </div>

            <div className="px-5 pb-5 pt-4 border-t border-default-200">
                <p className="label-caps mb-3">Teams</p>
                <div className="flex gap-2 mb-3">
                    <input
                        value={teamName}
                        onChange={(e) => setTeamName(e.target.value)}
                        placeholder="Team name"
                        className="input-underline flex-1 text-sm min-h-[36px]"
                        onKeyDown={(e) => e.key === "Enter" && createTeam()}
                    />
                    <Button size="sm" variant="outline" onPress={createTeam}>
                        Create
                    </Button>
                </div>
                <ul className="flex flex-col gap-1 max-h-48 overflow-y-auto">
                    {teams.map((t) => (
                        <li
                            key={t.id}
                            className="flex items-center justify-between text-sm py-1.5 px-3 rounded-lg hover:bg-default-100 transition-colors"
                        >
                            <span className="font-medium">{t.name}</span>
                            <span className="mono text-xs text-default-500">
                                {t.memberUuids.length} members · {t.score} pts
                            </span>
                        </li>
                    ))}
                    {teams.length === 0 && (
                        <li className="text-default-500 text-sm">No teams yet.</li>
                    )}
                </ul>
            </div>

            <div className="px-5 pb-5 pt-4 border-t border-default-200">
                <p className="label-caps mb-3">{players.length} players</p>
                <ul className="flex flex-col gap-1 max-h-48 overflow-y-auto">
                    {players.map((p) => (
                        <li
                            key={p.uuid}
                            className="flex items-center justify-between text-sm py-1.5 px-3 rounded-lg hover:bg-default-100 transition-colors"
                        >
                            <span className="font-medium">{p.name}</span>
                            <span className="mono text-xs text-default-500">{p.score}</span>
                        </li>
                    ))}
                    {players.length === 0 && (
                        <li className="text-default-500 text-sm">
                            Share the PIN to invite players.
                        </li>
                    )}
                </ul>
            </div>
        </div>
    );
}
