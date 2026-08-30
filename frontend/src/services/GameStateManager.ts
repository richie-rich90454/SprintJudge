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
} from "../types";

const initial: GameState = {
    status: "LOBBY",
    pin: null,
    playerUuid: null,
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
                });
                this.requestLeaderboardResync();
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

    join(pin: string, name: string, role: "player" | "host" = "player") {
        this.patch({ role });
        webSocketService.send({ type: "JOIN", role, name, pin });
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

    requestLeaderboardResync() {
        if (this.resyncInFlight) return;
        this.resyncInFlight = true;
        webSocketService.send({ type: "RESYNC_LEADERBOARD" });
    }

    private patch(p: Partial<GameState>) {
        this.state$.next({ ...this.state$.value, ...p });
    }

    private dispatch(m: WsMessage) {
        switch (m.type) {
            case "JOINED":
                this.patch({
                    playerUuid: m.uuid as string,
                    room: m.room as unknown as RoomState,
                    status: (m.room as unknown as RoomState)?.status ?? "LOBBY",
                });
                // Baseline for the delta protocol; server answers with a full batch.
                this.requestLeaderboardResync();
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
            case "LEADERBOARD":
                this.lastSeq = null;
                this.patch({ leaderboard: m.rankings as LeaderboardEntry[] });
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
                this.patch({
                    status: "ENDED",
                    leaderboard: m.rankings as LeaderboardEntry[],
                    currentQuestion: null,
                });
                clearTimer();
                break;
            case "GAME_REVIEW":
                this.patch({
                    status: "ENDED",
                    review: m as unknown as GameReview,
                    currentQuestion: null,
                });
                clearTimer();
                break;
            case "TIMER_UPDATE":
                if (this.state.currentQuestion) {
                    pushTimer(
                        this.state.currentQuestion.id,
                        this.state.currentQuestion.timeLimitSec,
                        m.newEndEpochMs as number,
                    );
                }
                break;
            case "ERROR":
                this.resyncInFlight = false;
                this.patch({ error: m.message as string });
                break;
            case "TEAM_CREATED":
            case "TEAM_JOINED":
            case "TEAM_LIST":
            case "BRACKET":
                // Team/battle events are consumed by UI components, not state machine.
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
import { pushTimer, clearTimer } from "../stores/useTimerStore";

export const gameStateManager = GameStateManager.instance;
