import { BehaviorSubject } from "rxjs";
import { webSocketService, WsMessage } from "./WebSocketService";
import {
    GameState,
    QuestionDto,
    RoomState,
    LeaderboardEntry,
    LeaderboardDelta,
    GameReview,
    SubmissionResult,
    TeamInfo,
    BattleBracket,
    BattleOpponent,
    BattleResultMessage,
    BattleChampionMessage,
} from "../types";

const SESSION_KEY = "sprintjudge_player_session";

interface StoredSession {
    pin: string;
    name: string;
    token: string;
}

function readStoredSession(): StoredSession | null {
    try {
        const raw = sessionStorage.getItem(SESSION_KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw) as Partial<StoredSession>;
        if (!parsed.pin || !parsed.name || !parsed.token) return null;
        return { pin: parsed.pin, name: parsed.name, token: parsed.token };
    } catch {
        return null;
    }
}

function writeStoredSession(session: StoredSession): void {
    try {
        sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    } catch {
        /* private mode or quota: rejoin degrades to a manual join */
    }
}

function clearStoredSession(): void {
    try {
        sessionStorage.removeItem(SESSION_KEY);
    } catch {
        /* ignore */
    }
}

const initial: GameState = {
    status: "LOBBY",
    pin: null,
    playerUuid: null,
    rejoinToken: null,
    playerName: null,
    role: "player",
    quizId: null,
    currentQuestion: null,
    leaderboard: [],
    room: null,
    lastResult: null,
    error: null,
    gameMode: "STANDARD",
    review: null,
    teams: [],
    bracket: null,
    battleOpponent: null,
    battleResult: null,
    champion: null,
};

/**
 * Single source of truth for game state. Leaderboard deltas are applied
 * strictly in sequence order; any gap triggers an authoritative resync so the
 * client can never drift from server truth (Q17c contract).
 */
export class GameStateManager {
    private static _instance: GameStateManager | null = null;
    private readonly state$ = new BehaviorSubject<GameState>(initial);
    private lastSeq: number | null = null;
    private resyncInFlight = false;

    static get instance(): GameStateManager {
        if (!this._instance) this._instance = new GameStateManager();
        return this._instance;
    }

    constructor() {
        webSocketService.onMessage().subscribe((m) => this.dispatch(m));
        // On reconnect, rejoin and resync if the player was already connected.
        webSocketService.onStatus().subscribe((status) => {
            if (status === "open" && this.state.playerUuid) {
                webSocketService.send({
                    type: "JOIN",
                    role: this.state.role,
                    name: this.state.playerName,
                    pin: this.state.pin,
                    rejoinToken: this.state.rejoinToken,
                });
                this.requestLeaderboardResync();
            } else if (status === "failed") {
                this.patch({ error: "Connection failed after 10 retries — refresh to rejoin" });
            }
        });
    }

    get state(): GameState {
        return this.state$.value;
    }

    observe() {
        return this.state$.asObservable();
    }

    connect(url: string) {
        this.lastSeq = null;
        this.resyncInFlight = false;
        webSocketService.connect(url);
    }

    join(
        pin: string,
        name: string,
        role: "player" | "host" = "player",
        rejoinToken: string | null = null,
    ) {
        // New game, clean slate: stale leaderboard/review/results from a
        // previous game must never leak into this one (replay guard).
        this.lastSeq = null;
        this.resyncInFlight = false;
        if (!rejoinToken) clearStoredSession();
        this.patch({
            role,
            pin,
            playerName: name,
            error: null,
            status: "LOBBY",
            playerUuid: null,
            rejoinToken,
            room: null,
            currentQuestion: null,
            leaderboard: [],
            lastResult: null,
            review: null,
            teams: [],
            bracket: null,
            battleOpponent: null,
            battleResult: null,
            champion: null,
        });
        webSocketService.send({
            type: "JOIN",
            role,
            name,
            pin,
            ...(rejoinToken ? { rejoinToken } : {}),
        });
    }

    /** Reclaim a player seat after a page refresh. Returns false when absent. */
    resume(): boolean {
        const stored = readStoredSession();
        if (!stored) return false;
        const proto = window.location.protocol === "https:" ? "wss" : "ws";
        this.connect(`${proto}://${window.location.host}/ws`);
        this.join(stored.pin, stored.name, "player", stored.token);
        return true;
    }

    submit(questionId: string, response: unknown, language?: string) {
        webSocketService.send({ type: "SUBMIT", questionId, response, language });
    }

    hostCommand(
        action: "NEXT_QUESTION" | "FORCE_SUBMIT" | "END_GAME",
        payload?: Record<string, unknown>,
    ) {
        webSocketService.send({ type: action, ...(payload ?? {}) } as WsMessage);
    }

    extendTimer(seconds: number) {
        webSocketService.send({ type: "EXTEND_TIMER", seconds });
    }

    kickPlayer(playerUuid: string) {
        webSocketService.send({ type: "KICK_PLAYER", playerUuid });
    }

    joinTeam(teamId: string) {
        webSocketService.send({ type: "JOIN_TEAM", teamId });
    }

    leaveTeam() {
        webSocketService.send({ type: "LEAVE_TEAM" });
    }

    createTeam(name: string) {
        webSocketService.send({ type: "CREATE_TEAM", name });
    }

    requestTeams() {
        webSocketService.send({ type: "GET_TEAMS" });
    }

    requestBracket() {
        webSocketService.send({ type: "GET_BRACKET" });
    }

    requestLeaderboardResync() {
        if (this.resyncInFlight) return;
        this.resyncInFlight = true;
        webSocketService.send({ type: "RESYNC_LEADERBOARD" });
    }

    private patch(p: Partial<GameState>) {
        this.state$.next({ ...this.state$.value, ...p });
    }

    /** A new game invalidates every cached code draft from the previous one. */
    private clearStaleDrafts() {
        try {
            for (let i = localStorage.length - 1; i >= 0; i--) {
                const key = localStorage.key(i);
                if (key?.startsWith("sprintjudge_code_")) localStorage.removeItem(key);
            }
        } catch {
            /* ignore */
        }
    }

    private dispatch(m: WsMessage) {
        switch (m.type) {
            case "JOINED":
                // Never clear live game data here. join() already reset for a
                // new game — and autostart rooms broadcast Q1 BEFORE this ack
                // is sent, so clearing would wipe a rendered question with no
                // re-send coming (flash-then-standby). Stale data from a
                // previous game cannot survive: join() nulled it first.
                this.patch({
                    playerUuid: m.uuid as string,
                    rejoinToken: (m.rejoinToken as string) ?? null,
                    room: m.room as unknown as RoomState,
                    status: (m.room as unknown as RoomState)?.status ?? "LOBBY",
                    error: null,
                    leaderboard: [],
                });
                // Baseline for the delta protocol; server answers with a full batch.
                this.requestLeaderboardResync();
                const joinedMode = (m.room as unknown as RoomState)?.gameMode;
                if (joinedMode) this.patch({ gameMode: joinedMode });
                if (joinedMode === "TEAM") this.requestTeams();
                if (joinedMode === "BATTLE") this.requestBracket();
                this.clearStaleDrafts();
                if (
                    this.state.role === "player" &&
                    this.state.pin &&
                    this.state.playerName &&
                    this.state.rejoinToken
                ) {
                    writeStoredSession({
                        pin: this.state.pin,
                        name: this.state.playerName,
                        token: this.state.rejoinToken,
                    });
                }
                break;
            case "ROOM_STATE": {
                const room = m as unknown as RoomState;
                // The roster is connection truth: drop leaderboard rows for players
                // who left or were kicked (the delta protocol has no tombstone).
                const connected = new Set((room.players ?? []).map((p) => p.uuid));
                this.patch({
                    room,
                    gameMode: room.gameMode ?? "STANDARD",
                    leaderboard: this.state$.value.leaderboard.filter((e) => connected.has(e.uuid)),
                });
                break;
            }
            case "QUESTION_START": {
                const q = m.question as QuestionDto | undefined;
                const tl = m.timeLimitSec as number | undefined;
                if (!q) break;
                // Practice mode sends timeLimitSec=-1 → no timer.
                if (tl && tl > 0) {
                    const end = ((m.startedAtEpochMs as number) ?? Date.now()) + tl * 1000;
                    pushTimer(q.id, tl, end);
                } else {
                    pushTimer(q.id, 0, Infinity);
                }
                this.patch({ status: "ACTIVE", currentQuestion: q, lastResult: null, error: null });
                break;
            }
            case "LEADERBOARD_DELTA":
                this.applyDelta(m as unknown as LeaderboardDelta);
                break;
            case "ROUND_RESULT":
                this.patch({
                    status: "REVIEW",
                    lastResult: m as unknown as SubmissionResult,
                });
                clearTimer();
                break;
            case "SUBMISSION_RESULT": {
                const sub = {
                    questionId: m.questionId as string,
                    score: m.score as number,
                    allPassed: m.allPassed as boolean,
                    passed: m.passed as number | undefined,
                    totalTests: m.totalTests as number | undefined,
                    aiFeedback: (m.aiFeedback as string) ?? null,
                };
                this.patch({
                    lastResult: {
                        ...(this.state.lastResult ?? {}),
                        submission: sub,
                    },
                });
                break;
            }
            case "GAME_END":
                clearStoredSession();
                this.patch({
                    status: "ENDED",
                    leaderboard: m.rankings as LeaderboardEntry[],
                    currentQuestion: null,
                    rejoinToken: null,
                });
                clearTimer();
                break;
            case "GAME_REVIEW": {
                const review = m as unknown as GameReview;
                clearStoredSession();
                this.patch({
                    status: "ENDED",
                    review,
                    // The podium reads the leaderboard store: seed it from the
                    // review when no GAME_END preceded it.
                    leaderboard: review.rankings ?? [],
                    currentQuestion: null,
                    rejoinToken: null,
                });
                clearTimer();
                break;
            }
            case "TIMER_UPDATE":
                if (this.state.currentQuestion) {
                    // Accumulate onto the live total: each update carries only
                    // its own extension, and QUESTION_START resets the base.
                    const total = useTimerStore.getState().totalSec + Number(m.extendSec ?? 0);
                    pushTimer(this.state.currentQuestion.id, total, m.newEndEpochMs as number);
                }
                break;
            case "ERROR":
                this.resyncInFlight = false;
                this.patch({ error: m.message as string });
                break;
            case "TEAM_CREATED":
            case "TEAM_JOINED":
                // The server follows both with a TEAM_LIST broadcast.
                break;
            case "TEAM_LIST":
                this.patch({ teams: (m.teams as TeamInfo[]) ?? [] });
                break;
            case "BRACKET":
                this.patch({ bracket: m as unknown as BattleBracket });
                break;
            case "BATTLE_OPPONENT":
                this.patch({ battleOpponent: m as unknown as BattleOpponent });
                break;
            case "BATTLE_RESULT":
                this.patch({ battleResult: m as unknown as BattleResultMessage });
                break;
            case "BATTLE_CHAMPION":
                this.patch({ champion: m as unknown as BattleChampionMessage });
                break;
        }
    }

    /** Strict seq application with automatic resync on gap — never approximate. */
    private applyDelta(delta: LeaderboardDelta) {
        if (this.lastSeq !== null && delta.seq <= this.lastSeq) return; // duplicate/old
        if (this.lastSeq !== null && delta.seq > this.lastSeq + 1) {
            this.requestLeaderboardResync(); // gap
            return;
        }
        this.lastSeq = delta.seq;

        if (delta.resync || delta.entries.length === 0) {
            // Authoritative replacement (also covers "everyone at zero" rooms).
            this.patch({ leaderboard: [...delta.entries].sort((a, b) => a.rank - b.rank) });
            this.resyncInFlight = false;
            return;
        }
        const byUuid = new Map(this.state$.value.leaderboard.map((e) => [e.uuid, e]));
        for (const e of delta.entries) byUuid.set(e.uuid, e);
        const merged = [...byUuid.values()].sort((a, b) => a.rank - b.rank);
        this.patch({ leaderboard: merged });
    }
}

// Local import to avoid a cycle with the timer store module graph.
import { pushTimer, clearTimer, useTimerStore } from "../stores/useTimerStore";

export const gameStateManager = GameStateManager.instance;
