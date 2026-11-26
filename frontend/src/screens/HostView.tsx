import { useI18n } from "../i18n";
import { useEffect, useRef, useState } from "react";
import { Link, useSearch } from "@tanstack/react-router";
import { Card } from "../ui/Card";
import { Button } from "../ui/Button";
import { Dialog } from "../ui/Dialog";
import { TextInput } from "../ui/TextInput";
import { Chip } from "../ui/Primitives";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { webSocketService, type WsMessage } from "../game/WebSocketService";
import { SoundToggle } from "../components/SoundToggle";
import { MusicToggle } from "../components/MusicToggle";
import { MotionToggle } from "../components/MotionToggle";
import { AboutDialog } from "../components/AboutDialog";
import { FairnessControls, readFairness } from "../components/FairnessControls";
import { StageThemePicker } from "../components/host/StageTheme";
import { useVirtualWindow } from "../hooks/useVirtualWindow";
import { useFlipList } from "../motion/useStageMotion";
import { HostStage } from "../components/host/HostStage";
import { useJoinUrl } from "../hooks/useJoinUrl";

const ROW_H = 44;

interface TeamRow {
    id: string;
    name: string;
    memberUuids: string[];
    score: number;
}

function TeamBoard() {
    const { t } = useI18n();
    const teams = useGameStore((s) => s.teams);
    const requestTeams = useGameStore((s) => s.requestTeams);

    useEffect(() => {
        requestTeams();
    }, [requestTeams]);

    return (
        <Card className="p-0 overflow-hidden">
            <div className="flex items-center justify-between px-6 py-5 border-b border-[var(--oq-border)]">
                <h3 className="font-extrabold text-lg">{t("ui.teams")}</h3>
                <span className="label-caps">
                    {teams.length} {teams.length === 1 ? t("ui.teamSingular") : t("ui.teamsPlural")}
                </span>
            </div>
            {teams.length === 0 ? (
                <p className="text-[var(--oq-ink-soft)] text-sm p-6">
                    {t("ui.noTeamsYetCreateThemInTheControlsPanelSoStudentsCanPickOne")}
                </p>
            ) : (
                <ol className="flex flex-col">
                    {teams.map((team, i) => (
                        <li
                            key={team.id}
                            className="flex items-center gap-4 px-6 py-3 border-b border-dotted border-[var(--oq-border)] last:border-b-0"
                        >
                            <span className="mono font-bold min-w-8 text-right tabular-nums text-[var(--oq-accent)]">
                                {i + 1}
                            </span>
                            <span className="truncate font-semibold flex-1" title={team.name}>
                                {team.name}
                            </span>
                            <span className="mono text-xs text-[var(--oq-ink-soft)]">
                                {team.memberUuids.length}{" "}
                                {team.memberUuids.length === 1
                                    ? t("ui.memberSingular")
                                    : t("ui.membersPlural")}
                            </span>
                            <span className="mono font-bold tabular-nums">
                                {(Number.isFinite(team.score) ? team.score : 0).toLocaleString()}
                            </span>
                        </li>
                    ))}
                </ol>
            )}
        </Card>
    );
}

function BracketPanel() {
    const { t } = useI18n();
    const bracket = useGameStore((s) => s.bracket);
    const champion = useGameStore((s) => s.champion);
    const requestBracket = useGameStore((s) => s.requestBracket);
    const room = useGameStore((s) => s.room);

    useEffect(() => {
        requestBracket();
    }, [requestBracket]);

    const nameOf = (uuid: string | null | undefined) =>
        uuid ? (room?.players.find((p) => p.uuid === uuid)?.name ?? t("ui.playerFallback")) : "";

    return (
        <Card className="p-0 overflow-hidden">
            <div className="flex items-center justify-between px-6 py-5 border-b border-[var(--oq-border)]">
                <h3 className="font-extrabold text-lg">{t("ui.bracket")}</h3>
                <span className="label-caps">
                    {bracket?.rounds.length ?? 0}{" "}
                    {(bracket?.rounds.length ?? 0) === 1
                        ? t("ui.roundSingular")
                        : t("ui.roundsPlural")}
                </span>
            </div>
            {!bracket || bracket.rounds.length === 0 ? (
                <p className="text-[var(--oq-ink-soft)] text-sm p-6">
                    {t("ui.startTheBattleToBuildTheFirstBracket")}
                </p>
            ) : (
                <div className="flex flex-col gap-5 p-6">
                    {bracket.rounds.map((r) => (
                        <div key={r.number}>
                            <p className="label-caps mb-2">Round {r.number}</p>
                            <ul className="flex flex-col gap-1 text-sm">
                                {r.pairs.map((p, i) => (
                                    <li
                                        key={i}
                                        className="flex items-center justify-between gap-3 border border-[var(--oq-border)] px-3 py-2"
                                    >
                                        <span
                                            className={p.winnerUuid === p.p1Uuid ? "font-bold" : ""}
                                        >
                                            {nameOf(p.p1Uuid)}
                                        </span>
                                        <span className="label-caps text-[var(--oq-ink-soft)]">
                                            {p.bye ? t("ui.byeLabel") : t("ui.vsLabel")}
                                        </span>
                                        <span
                                            className={p.winnerUuid === p.p2Uuid ? "font-bold" : ""}
                                        >
                                            {p.bye ? "" : nameOf(p.p2Uuid)}
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    ))}
                    {champion && (
                        <p className="border-t border-[var(--oq-border)] pt-4 font-bold text-[var(--oq-accent)]">
                            Champion: {champion.championName}
                        </p>
                    )}
                </div>
            )}
        </Card>
    );
}

function LeaderboardPanel() {
    const { t } = useI18n();
    const leaderboard = useGameStore((s) => s.leaderboard);
    const room = useGameStore((s) => s.room);
    const wsError = useGameStore((s) => s.error);

    const rows = leaderboard.length
        ? leaderboard
        : [...(room?.players ?? [])]
              .filter((p) => p.name !== "Host")
              .sort((a, b) => b.score - a.score)
              .map((p, i) => ({
                  uuid: p.uuid,
                  name: p.name,
                  score: p.score,
                  rank: i + 1,
              }));

    const rh = ROW_H;
    const maxH = 460;
    const listRef = useFlipList<HTMLDivElement>([rows.map((r) => r.uuid).join(",")]);
    const { ref, start, end } = useVirtualWindow(rows.length, rh, 6, maxH);
    const slice = rows.slice(start, end);

    return (
        <Card className="p-0 overflow-hidden">
            <div className="flex items-center justify-between px-6 pt-6 pb-6 border-b border-[var(--oq-border)]">
                <h3 className="header-double" style={{ marginBottom: 0 }}>
                    {t("ui.leaderboard")}
                </h3>
                <span className="label-caps">
                    {rows.length}{" "}
                    {rows.length === 1 ? t("ui.studentSingular") : t("ui.studentsPlural")}
                </span>
            </div>
            {wsError && (
                <p role="alert" className="text-[var(--oq-danger)] text-sm px-6 pt-4">
                    Connection error: {wsError}
                </p>
            )}

            <div ref={listRef} className="px-0 relative">
                <div
                    ref={ref}
                    aria-label={t("ui.leaderboard")}
                    className="overflow-y-auto"
                    style={{ maxHeight: maxH }}
                >
                    {rows.length > 0 && (
                        <>
                            <div style={{ height: start * rh }} aria-hidden="true" />
                            {slice.map((r) => {
                                const podium = r.rank <= 3;
                                return (
                                    <div
                                        key={r.uuid}
                                        className={`lb-row flip-row flex items-center gap-4 px-6 border-b border-dotted border-[var(--oq-border)] ${podium ? "bg-[var(--oq-row-alt)]" : ""}`}
                                        style={{ height: rh, minHeight: 44 }}
                                    >
                                        <span
                                            className="mono font-bold min-w-8 text-right tabular-nums"
                                            style={
                                                podium
                                                    ? {
                                                          color: "var(--oq-accent)",
                                                          fontSize: 18,
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
                                            style={{ fontSize: 17 }}
                                        >
                                            {(r.score ?? 0).toLocaleString()}
                                        </span>
                                    </div>
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
                            {t("ui.noPlayersYetShareThePIN")}
                        </p>
                    )}
                </div>
            </div>
        </Card>
    );
}

function ControlsPanel() {
    const { t } = useI18n();
    const advanceLock = useRef(false);
    const hostCommand = useGameStore((s) => s.hostCommand);
    const extendTimer = useGameStore((s) => s.extendTimer);
    const kickPlayer = useGameStore((s) => s.kick);
    const status = useGameStore((s) => s.status);
    const room = useGameStore((s) => s.room);
    const gameMode = useGameStore((s) => s.gameMode);
    const wsError = useGameStore((s) => s.error);
    const [teamName, setTeamName] = useState("");
    const [teamsLoading, setTeamsLoading] = useState(false);
    const [teamsError, setTeamsError] = useState<string | null>(null);
    const [teams, setTeams] = useState<TeamRow[]>([]);
    const [confirmEndOpen, setConfirmEndOpen] = useState(false);
    // True while a team round-trip is outstanding; unrelated ERRORs (submit
    // lockouts, rate limits) must not paint the Teams panel red.
    const teamOp = useRef(false);

    const players = (room?.players ?? []).filter((p) => p.name !== "Host");
    const active = status === "ACTIVE";
    const reviewing = status === "REVIEW";
    const statusCopy = active
        ? t("ui.roundLive")
        : reviewing
          ? t("ui.reviewingAnswers")
          : t("ui.awaitingStart");

    useEffect(() => {
        const sub = webSocketService.onMessage().subscribe((m: WsMessage) => {
            if (m.type === "TEAM_LIST") {
                setTeams((m.teams as typeof teams) ?? []);
                setTeamsLoading(false);
                setTeamsError(null);
                teamOp.current = false;
            }
            if (m.type === "TEAM_CREATED" || m.type === "TEAM_JOINED") {
                // Creation confirmed: reload the list instead of waiting for
                // a manual retry.
                teamOp.current = true;
                setTeamsLoading(true);
                webSocketService.send({ type: "GET_TEAMS" });
            }
            if (m.type === "ERROR" && teamOp.current) {
                setTeamsLoading(false);
                setTeamsError(String(m.message ?? t("ui.failedToLoadTeams")));
                teamOp.current = false;
            }
        });
        return () => sub.unsubscribe();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const send = (msg: WsMessage) => webSocketService.send(msg);

    const requestTeams = () => {
        setTeamsLoading(true);
        setTeamsError(null);
        teamOp.current = true;
        send({ type: "GET_TEAMS" });
    };

    const createTeam = () => {
        if (!teamName.trim()) return;
        teamOp.current = true;
        send({ type: "CREATE_TEAM", name: teamName.trim() });
        setTeamName("");
    };

    const confirmEnd = () => setConfirmEndOpen(true);
    const doEndGame = () => {
        setConfirmEndOpen(false);
        hostCommand("END_GAME");
    };

    return (
        <Card className="p-0 overflow-hidden">
            <div className="pb-4 border-b border-[var(--oq-border)]">
                <h3 className="font-extrabold text-lg">{t("ui.controls")}</h3>
                <p className="label-caps mt-1">{statusCopy}</p>
            </div>

            <div className="py-4 flex flex-col gap-4">
                {active ? (
                    <>
                        <Button
                            variant="primary"
                            className="w-full"
                            onClick={() => hostCommand("FORCE_SUBMIT")}
                        >
                            {t("ui.revealAnswer")}
                        </Button>
                        <Button
                            variant="secondary"
                            className="w-full"
                            onClick={() => extendTimer(30)}
                        >
                            {t("ui.30Seconds")}
                        </Button>
                    </>
                ) : (
                    <>
                        <Button
                            variant="primary"
                            className="w-full"
                            onClick={() => {
                                // A double-click would otherwise advance two
                                // rounds (each NEXT reveals and advances).
                                if (advanceLock.current) return;
                                advanceLock.current = true;
                                window.setTimeout(() => {
                                    advanceLock.current = false;
                                }, 600);
                                hostCommand("NEXT_QUESTION", { ...readFairness() });
                            }}
                        >
                            {status === "LOBBY" ? t("ui.startRound") : t("ui.nextQuestion")}
                        </Button>
                        {status === "LOBBY" && gameMode === "BATTLE" && (
                            <Button
                                variant="secondary"
                                className="w-full"
                                onClick={() => send({ type: "START_BATTLE", ...readFairness() })}
                            >
                                {t("ui.startBattle")}
                            </Button>
                        )}
                    </>
                )}
                <Button variant="danger" className="w-full" onClick={confirmEnd}>
                    {t("ui.endGame")}
                </Button>
                <Dialog
                    open={confirmEndOpen}
                    onOpenChange={setConfirmEndOpen}
                    title={t("ui.endTheGame")}
                >
                    <p className="text-[var(--oq-ink-soft)] text-sm">
                        {t("ui.thisEndsTheGameForAllPlayersImmediately")}
                    </p>
                    <div className="flex gap-2 mt-4">
                        <Button
                            variant="secondary"
                            className="flex-1"
                            onClick={() => setConfirmEndOpen(false)}
                        >
                            {t("ui.keepPlaying")}
                        </Button>
                        <Button variant="danger" className="flex-1" onClick={doEndGame}>
                            {t("ui.endGame")}
                        </Button>
                    </div>
                </Dialog>
            </div>

            {gameMode === "TEAM" && (
                <div className="py-4 border-t border-[var(--oq-border)]">
                    <div className="flex items-center justify-between mb-3">
                        <p className="label-caps">{t("ui.teams")}</p>
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
                            placeholder={t("ui.teamName")}
                            aria-label={t("ui.teamName")}
                            className="flex-1 text-sm"
                            onKeyDown={(e) => e.key === "Enter" && createTeam()}
                        />
                        <Button variant="secondary" size="sm" onClick={createTeam}>
                            {t("ui.create")}
                        </Button>
                    </div>
                    <ul className="flex flex-col gap-2 max-h-48 overflow-y-auto">
                        {teams.map((team) => (
                            <li
                                key={team.id}
                                className="flex items-center justify-between text-sm min-h-[44px] px-3 rounded-[2px] hover:bg-[var(--oq-row-alt)] transition-colors"
                            >
                                <span className="font-medium">{team.name}</span>
                                <span className="mono text-xs text-[var(--oq-ink-soft)]">
                                    {team.memberUuids.length} members, {team.score} pts
                                </span>
                            </li>
                        ))}
                        {teamsLoading && (
                            <li className="text-[var(--oq-ink-soft)] text-sm inline-flex items-center gap-2 min-h-[44px]">
                                <span className="oq-spin" aria-hidden="true" />{" "}
                                {t("ui.loadingTeams")}
                            </li>
                        )}
                        {!teamsLoading && teams.length === 0 && !teamsError && (
                            <li className="text-[var(--oq-ink-soft)] text-sm">
                                {t("ui.noTeamsYet")}
                            </li>
                        )}
                    </ul>
                </div>
            )}

            <div className="py-4 border-t border-[var(--oq-border)] flex flex-col gap-5">
                <div>
                    <p className="label-caps mb-3">{t("ui.stageTheme")}</p>
                    <StageThemePicker />
                </div>
                <FairnessControls />
            </div>

            <div className="pt-4 border-t border-[var(--oq-border)]">
                <p className="label-caps mb-3">
                    {players.length}{" "}
                    {players.length === 1 ? t("ui.studentSingular") : t("ui.studentsPlural")}
                </p>
                {!room && (
                    <p className="text-[var(--oq-ink-soft)] text-sm inline-flex items-center gap-2 min-h-[44px]">
                        <span className="oq-spin" aria-hidden="true" /> {t("ui.loadingPlayers")}
                    </p>
                )}
                <ul className="flex flex-col gap-2 max-h-48 overflow-y-auto">
                    {players.map((p) => (
                        <li
                            key={p.uuid}
                            className="flex items-center justify-between gap-2 text-sm min-h-[44px] px-3 rounded-[2px] hover:bg-[var(--oq-row-alt)] transition-colors"
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
                                    aria-label={t("ui.kickPlayer", { name: p.name })}
                                    className="text-[var(--oq-danger)] text-xs font-bold min-h-[44px] px-2"
                                >
                                    {t("ui.kick")}
                                </button>
                            </span>
                        </li>
                    ))}
                    {room && players.length === 0 && (
                        <li className="text-[var(--oq-ink-soft)] text-sm">
                            {t("ui.shareThePINToInvitePlayers")}
                        </li>
                    )}
                </ul>
            </div>
        </Card>
    );
}

export function HostView() {
    const { t } = useI18n();
    const search = useSearch({ strict: false }) as unknown as {
        pin?: string;
        projector?: boolean | string;
    };
    const storePin = useUIStore((s) => s.pin);
    const connect = useGameStore((s) => s.connect);
    const join = useGameStore((s) => s.join);
    const room = useGameStore((s) => s.room);
    const gameStatus = useGameStore((s) => s.status);
    const wsError = useGameStore((s) => s.error);
    const [copied, setCopied] = useState(false);
    const copyTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    const pin =
        (typeof search.pin === "string" && search.pin.length > 0 ? search.pin : storePin) ?? null;
    const joinUrl = useJoinUrl(pin ?? "");

    useEffect(() => {
        if (!pin) return;
        const proto = window.location.protocol === "https:" ? "wss" : "ws";
        connect(`${proto}://${window.location.host}/ws`);
        join(pin, t("ui.hostLabel"), "host");
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
            <div className="min-h-[100dvh] flex items-center justify-center p-6">
                <p className="text-[var(--oq-ink-soft)] text-center">
                    Create a game from the{" "}
                    <Link to="/admin" className="font-bold text-[var(--oq-accent)]">
                        {t("ui.adminDashboard")}
                    </Link>{" "}
                    first.
                </p>
            </div>
        );
    }

    const playerCount = (room?.players ?? []).filter((p) => p.name !== "Host").length;
    const liveStatus = gameStatus === "ENDED" ? "ENDED" : (room?.status ?? gameStatus);
    const statusLabel =
        liveStatus === "ACTIVE"
            ? t("ui.live")
            : liveStatus === "REVIEW"
              ? "Reviewing"
              : liveStatus === "ENDED"
                ? t("ui.gameOver")
                : t("ui.lobbyOpen");
    const tone: "success" | "accent" | "neutral" =
        liveStatus === "ACTIVE" ? "success" : liveStatus === "REVIEW" ? "accent" : "neutral";
    const copyLink = () => {
        const done = (ok: boolean) => {
            setCopied(ok);
            if (!ok) return;
            if (copyTimer.current) clearTimeout(copyTimer.current);
            copyTimer.current = setTimeout(() => setCopied(false), 2000);
        };
        try {
            const clip = navigator.clipboard;
            if (clip?.writeText) {
                void clip.writeText(joinUrl).then(
                    () => done(true),
                    () => done(false),
                );
                return;
            }
            // Clipboard API is undefined in insecure contexts: legacy fallback.
            const ta = document.createElement("textarea");
            ta.value = joinUrl;
            document.body.appendChild(ta);
            ta.select();
            done(document.execCommand("copy"));
            ta.remove();
        } catch {
            done(false);
        }
    };

    const toggleFullScreen = () => {
        if (document.fullscreenElement) {
            void document.exitFullscreen().catch(() => {});
        } else {
            void document.documentElement.requestFullscreen().catch(() => {});
        }
    };

    return (
        <div className="pattern-exam min-h-[100dvh] flex flex-col">
            <header className="border-b border-[var(--oq-border)] bg-[var(--oq-surface)]">
                <div className="page-shell py-4">
                    <div className="flex items-center gap-6 flex-wrap">
                        <div>
                            <p className="label-caps mb-1">{t("ui.gamePIN")}</p>
                            <p className="mono font-extrabold text-4xl tracking-[.2em] leading-none">
                                {pin}
                            </p>
                        </div>
                        <div className="h-12 w-px bg-[var(--oq-border)] hidden sm:block" />
                        <div>
                            <div className="mb-1">
                                <Chip tone={tone}>{statusLabel}</Chip>
                            </div>
                            <p className="font-bold text-lg">
                                {playerCount}{" "}
                                {playerCount === 1
                                    ? t("ui.studentSingular")
                                    : t("ui.studentsPlural")}
                            </p>
                        </div>
                        <div className="flex-1" />
                        <div className="flex items-center gap-2">
                            <Button variant="secondary" size="sm" onClick={copyLink}>
                                {copied ? t("ui.copied") : t("ui.copyJoinLink")}
                            </Button>
                            <Button variant="secondary" size="sm" onClick={toggleFullScreen}>
                                {t("ui.fullScreen")}
                            </Button>
                            <Link
                                to="/watch/$pin"
                                params={{ pin }}
                                target="_blank"
                                rel="noreferrer"
                                className="btn btn-secondary btn-sm"
                            >
                                {t("ui.projectorView")}
                            </Link>
                        </div>
                        <SoundToggle className="header-icon" />
                        <MusicToggle className="header-icon" />
                        <MotionToggle className="header-icon" />
                        <AboutDialog />
                    </div>
                    <p className="mono text-xs text-[var(--oq-ink-soft)] mt-3 break-all">
                        {joinUrl}
                    </p>
                </div>
            </header>

            <main className="page-shell flex-1 grid lg:grid-cols-[minmax(0,1fr)_320px] gap-6 py-6 items-start w-full">
                <div className="min-w-0 flex flex-col gap-6">
                    {!room ? (
                        <div
                            className="rounded-[2px] border border-[var(--oq-border)] bg-[var(--oq-surface)] p-6"
                            aria-label={t("ui.loadingRoom")}
                        >
                            <div className="skeleton h-5 w-40 mb-4" />
                            <div className="flex flex-col gap-4" aria-hidden="true">
                                {[0, 1, 2, 3].map((i) => (
                                    <div key={i} className="skeleton h-11" />
                                ))}
                            </div>
                            <p className="text-[var(--oq-ink-soft)] text-sm mt-4">
                                {t("ui.loadingRoom")}
                            </p>
                            {wsError && (
                                <p role="alert" className="text-[var(--oq-danger)] text-sm mt-2">
                                    Connection error: {wsError}
                                </p>
                            )}
                        </div>
                    ) : (
                        <>
                            <HostStage pin={pin} joinUrl={joinUrl} />
                            {room?.gameMode === "TEAM" && <TeamBoard />}
                            {room?.gameMode === "BATTLE" && <BracketPanel />}
                            <LeaderboardPanel />
                        </>
                    )}
                    {wsError && room && (
                        <p role="alert" className="text-[var(--oq-danger)] text-sm">
                            Connection error: {wsError}
                        </p>
                    )}
                </div>
                {liveStatus === "ENDED" ? (
                    <Card className="p-6 flex flex-col gap-4">
                        <h3 className="font-extrabold text-lg">{t("ui.gameOver")}</h3>
                        <p className="text-[var(--oq-ink-soft)] text-sm">
                            {t(
                                "ui.finalStandingsAreOnTheBoardOpenTheFullReviewWithAnswerKeyAndClassAnalysis",
                            )}
                        </p>
                        <Link to="/results" className="btn btn-primary w-full">
                            {t("ui.viewResults")}
                        </Link>
                    </Card>
                ) : (
                    <ControlsPanel />
                )}
            </main>
        </div>
    );
}
