import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";

const fakeWs = vi.hoisted(() => {
    const msgHandlers: Array<(m: Record<string, unknown>) => void> = [];
    const statusHandlers: Array<(s: string) => void> = [];
    return {
        msgHandlers,
        statusHandlers,
        send: vi.fn(),
        connect: vi.fn(),
        disconnect: vi.fn(),
        emitMessage: (m: Record<string, unknown>) => msgHandlers.forEach((h) => h(m)),
        emitStatus: (s: string) => statusHandlers.forEach((h) => h(s)),
        onMessage: () => ({
            subscribe: (h: (m: Record<string, unknown>) => void) => {
                msgHandlers.push(h);
                return { unsubscribe: () => {} };
            },
        }),
        onStatus: () => ({
            subscribe: (h: (s: string) => void) => {
                statusHandlers.push(h);
                return { unsubscribe: () => {} };
            },
        }),
    };
});

vi.mock("./WebSocketService", () => ({ webSocketService: fakeWs }));

import { GameStateManager, gameStateManager } from "./GameStateManager";
import { useTimerStore } from "../stores/useTimerStore";

function msg(m: Record<string, unknown>) {
    fakeWs.emitMessage(m);
}

function joined(uuid = "u1", extra: Record<string, unknown> = {}) {
    msg({
        type: "JOINED",
        uuid,
        rejoinToken: "tok-1",
        room: { type: "ROOM_STATE", status: "LOBBY", players: [], gameMode: "STANDARD" },
        ...extra,
    });
}

function delta(seq: number, entries: unknown[], resync = false) {
    msg({ type: "LEADERBOARD_DELTA", seq, resync, entries });
}

beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    useTimerStore.setState({ questionId: null, totalSec: 30, endEpochMs: null });
    gameStateManager.join("0000", "Tester");
    vi.clearAllMocks();
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("GameStateManager basics", () => {
    test("connect resets sequence tracking and delegates", () => {
        delta(3, [{ uuid: "a", name: "A", score: 10, rank: 1 }]);
        gameStateManager.connect("ws://host/game");
        expect(fakeWs.connect).toHaveBeenCalledWith("ws://host/game");
        delta(9, [{ uuid: "b", name: "B", score: 5, rank: 1 }]);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid).sort()).toEqual(["a", "b"]);
    });

    test("join resets stale state from a previous game", () => {
        joined();
        delta(0, [{ uuid: "a", name: "A", score: 10, rank: 1 }]);
        msg({ type: "ROUND_RESULT", submission: { questionId: "q", allPassed: true, score: 5 } });
        msg({
            type: "GAME_REVIEW",
            rankings: [],
            questions: [],
            players: [],
            classStats: {},
        });
        gameStateManager.join("1234", "Ada");
        const s = gameStateManager.state;
        expect(s.status).toBe("LOBBY");
        expect(s.pin).toBe("1234");
        expect(s.playerName).toBe("Ada");
        expect(s.playerUuid).toBeNull();
        expect(s.rejoinToken).toBeNull();
        expect(s.room).toBeNull();
        expect(s.currentQuestion).toBeNull();
        expect(s.leaderboard).toEqual([]);
        expect(s.lastResult).toBeNull();
        expect(s.review).toBeNull();
        expect(s.error).toBeNull();
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "JOIN", role: "player", name: "Ada", pin: "1234" });
    });

    test("join passes explicit host role", () => {
        gameStateManager.join("9999", "Host", "host");
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "JOIN", role: "host", name: "Host", pin: "9999" });
        expect(gameStateManager.state.role).toBe("host");
    });

    test("submit sends response with language", () => {
        gameStateManager.submit("q1", { code: "x" }, "python");
        expect(fakeWs.send).toHaveBeenCalledWith({
            type: "SUBMIT",
            questionId: "q1",
            response: { code: "x" },
            language: "python",
        });
    });

    test("submit without language sends undefined language", () => {
        gameStateManager.submit("q1", { value: true });
        expect(fakeWs.send).toHaveBeenCalledWith({
            type: "SUBMIT",
            questionId: "q1",
            response: { value: true },
            language: undefined,
        });
    });

    test("hostCommand merges payload into the message", () => {
        gameStateManager.hostCommand("NEXT_QUESTION", { questionId: "q2" });
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "NEXT_QUESTION", questionId: "q2" });
    });

    test("hostCommand without payload sends bare action", () => {
        gameStateManager.hostCommand("END_GAME");
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "END_GAME" });
    });

    test("extendTimer sends seconds", () => {
        gameStateManager.extendTimer(20);
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "EXTEND_TIMER", seconds: 20 });
    });

    test("kickPlayer sends player uuid", () => {
        gameStateManager.kickPlayer("gone-1");
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "KICK_PLAYER", playerUuid: "gone-1" });
    });

    test("resync request sends once then guards while in flight", () => {
        gameStateManager.requestLeaderboardResync();
        gameStateManager.requestLeaderboardResync();
        expect(fakeWs.send).toHaveBeenCalledTimes(1);
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "RESYNC_LEADERBOARD" });
    });

    test("ERROR clears the resync flag so a new resync can go out", () => {
        gameStateManager.requestLeaderboardResync();
        msg({ type: "ERROR", message: "bad" });
        gameStateManager.requestLeaderboardResync();
        expect(fakeWs.send).toHaveBeenCalledTimes(2);
    });

    test("observe emits state updates to subscribers", () => {
        const seen: string[] = [];
        const sub = gameStateManager.observe().subscribe((s) => seen.push(s.status));
        joined();
        expect(seen).toContain("LOBBY");
        sub.unsubscribe();
    });
});

describe("GameStateManager JOINED", () => {
    test("JOINED seeds session, clears stale game data and resyncs", () => {
        localStorage.setItem("sprintjudge_code_q1", "cached");
        localStorage.setItem("other", "keep");
        joined("u9");
        const s = gameStateManager.state;
        expect(s.playerUuid).toBe("u9");
        expect(s.rejoinToken).toBe("tok-1");
        expect(s.status).toBe("LOBBY");
        expect(s.currentQuestion).toBeNull();
        expect(s.leaderboard).toEqual([]);
        expect(s.lastResult).toBeNull();
        expect(s.review).toBeNull();
        expect(s.error).toBeNull();
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "RESYNC_LEADERBOARD" });
        expect(localStorage.getItem("sprintjudge_code_q1")).toBeNull();
        expect(localStorage.getItem("other")).toBe("keep");
    });

    test("JOINED without rejoin token or room falls back cleanly", () => {
        msg({ type: "JOINED", uuid: "u2" });
        const s = gameStateManager.state;
        expect(s.playerUuid).toBe("u2");
        expect(s.rejoinToken).toBeNull();
        expect(s.status).toBe("LOBBY");
    });

    test("JOINED tolerates localStorage failures when clearing drafts", () => {
        vi.spyOn(window.localStorage, "key").mockImplementation(() => {
            throw new Error("denied");
        });
        expect(() => joined()).not.toThrow();
        expect(gameStateManager.state.playerUuid).toBe("u1");
    });
});

describe("GameStateManager ROOM_STATE", () => {
    test("stores room and drops departed players from the leaderboard", () => {
        delta(0, [
            { uuid: "a", name: "A", score: 10, rank: 1 },
            { uuid: "b", name: "B", score: 8, rank: 2 },
        ]);
        msg({
            type: "ROOM_STATE",
            status: "ACTIVE",
            questionCount: 3,
            currentQuestionId: null,
            players: [{ uuid: "a", name: "A", score: 10 }],
            gameMode: "TEAM",
        });
        const s = gameStateManager.state;
        expect(s.gameMode).toBe("TEAM");
        expect(s.leaderboard.map((e) => e.uuid)).toEqual(["a"]);
        expect(s.room?.status).toBe("ACTIVE");
    });

    test("missing gameMode defaults to STANDARD and missing roster clears board", () => {
        delta(0, [{ uuid: "a", name: "A", score: 10, rank: 1 }]);
        msg({ type: "ROOM_STATE", status: "LOBBY" });
        expect(gameStateManager.state.gameMode).toBe("STANDARD");
        expect(gameStateManager.state.leaderboard).toEqual([]);
    });
});

describe("GameStateManager QUESTION_START", () => {
    test("timed start arms the countdown and activates the question", () => {
        msg({
            type: "QUESTION_START",
            question: { id: "q1", type: "MCQ", title: "T", description: "", timeLimitSec: 30, pointsBase: 100, languagesAllowed: null, config: {} },
            timeLimitSec: 30,
            startedAtEpochMs: 10_000,
        });
        const s = gameStateManager.state;
        expect(s.status).toBe("ACTIVE");
        expect(s.currentQuestion?.id).toBe("q1");
        expect(s.lastResult).toBeNull();
        expect(s.error).toBeNull();
        expect(useTimerStore.getState()).toMatchObject({ questionId: "q1", totalSec: 30, endEpochMs: 40_000 });
    });

    test("practice mode with negative limit sets an untimed clock", () => {
        msg({
            type: "QUESTION_START",
            question: { id: "qp", type: "MCQ", title: "T", description: "", timeLimitSec: -1, pointsBase: 100, languagesAllowed: null, config: {} },
            timeLimitSec: -1,
            startedAtEpochMs: 10_000,
        });
        expect(useTimerStore.getState()).toMatchObject({ questionId: "qp", totalSec: 0 });
        expect(useTimerStore.getState().endEpochMs).toBe(Number.POSITIVE_INFINITY);
    });

    test("missing time limit sets an untimed clock", () => {
        msg({
            type: "QUESTION_START",
            question: { id: "qz", type: "MCQ", title: "T", description: "", timeLimitSec: 0, pointsBase: 100, languagesAllowed: null, config: {} },
        });
        expect(useTimerStore.getState().questionId).toBe("qz");
        expect(useTimerStore.getState().totalSec).toBe(0);
    });

    test("missing question leaves state untouched", () => {
        msg({ type: "QUESTION_START", timeLimitSec: 30, startedAtEpochMs: 1 });
        expect(gameStateManager.state.currentQuestion).toBeNull();
        expect(gameStateManager.state.status).toBe("LOBBY");
    });

    test("missing start timestamp falls back to Date.now", () => {
        vi.spyOn(Date, "now").mockReturnValue(5_000);
        msg({
            type: "QUESTION_START",
            question: { id: "qn", type: "MCQ", title: "T", description: "", timeLimitSec: 30, pointsBase: 100, languagesAllowed: null, config: {} },
            timeLimitSec: 30,
        });
        expect(useTimerStore.getState().endEpochMs).toBe(35_000);
    });
});

describe("GameStateManager leaderboard deltas", () => {
    test("first delta is accepted at any sequence", () => {
        delta(5, [{ uuid: "b", name: "B", score: 5, rank: 1 }]);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["b"]);
    });

    test("duplicate sequence is ignored", () => {
        delta(0, [{ uuid: "a", name: "A", score: 10, rank: 1 }]);
        delta(0, [{ uuid: "zzz", name: "Z", score: 99, rank: 1 }]);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["a"]);
    });

    test("older sequence is ignored", () => {
        delta(4, [{ uuid: "a", name: "A", score: 10, rank: 1 }]);
        delta(2, [{ uuid: "old", name: "O", score: 1, rank: 1 }]);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["a"]);
    });

    test("gap triggers a resync without applying the delta", () => {
        delta(0, [{ uuid: "a", name: "A", score: 10, rank: 1 }]);
        delta(2, [{ uuid: "gap", name: "G", score: 50, rank: 1 }]);
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "RESYNC_LEADERBOARD" });
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["a"]);
    });

    test("resync delta replaces the board sorted by rank", () => {
        delta(0, [{ uuid: "a", name: "A", score: 10, rank: 2 }]);
        delta(
            1,
            [
                { uuid: "b", name: "B", score: 20, rank: 2 },
                { uuid: "c", name: "C", score: 30, rank: 1 },
            ],
            true,
        );
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["c", "b"]);
    });

    test("empty-entries delta replaces the board even without the flag", () => {
        delta(0, [{ uuid: "a", name: "A", score: 10, rank: 1 }]);
        delta(1, []);
        expect(gameStateManager.state.leaderboard).toEqual([]);
    });

    test("incremental delta merges rows and sorts by rank", () => {
        delta(
            0,
            [
                { uuid: "b", name: "B", score: 20, rank: 2 },
                { uuid: "a", name: "A", score: 10, rank: 1 },
            ],
            true,
        );
        delta(1, [{ uuid: "c", name: "C", score: 30, rank: 0 }]);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["c", "a", "b"]);
    });

    test("authoritative resync clears the in-flight flag", () => {
        delta(0, [{ uuid: "a", name: "A", score: 10, rank: 1 }]);
        delta(5, [{ uuid: "x", name: "X", score: 1, rank: 1 }]);
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "RESYNC_LEADERBOARD" });
        vi.clearAllMocks();
        delta(1, [{ uuid: "a", name: "A", score: 11, rank: 1 }], true);
        gameStateManager.requestLeaderboardResync();
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "RESYNC_LEADERBOARD" });
    });
});

describe("GameStateManager results and game end", () => {
    test("ROUND_RESULT reviews the submission and clears the timer", () => {
        msg({
            type: "QUESTION_START",
            question: { id: "q1", type: "MCQ", title: "T", description: "", timeLimitSec: 30, pointsBase: 100, languagesAllowed: null, config: {} },
            timeLimitSec: 30,
            startedAtEpochMs: 1_000,
        });
        msg({ type: "ROUND_RESULT", submission: { questionId: "q1", allPassed: true, score: 100 } });
        const s = gameStateManager.state;
        expect(s.status).toBe("REVIEW");
        expect(s.lastResult?.submission.questionId).toBe("q1");
        expect(useTimerStore.getState().questionId).toBeNull();
    });

    test("SUBMISSION_RESULT merges over the previous result and nulls missing feedback", () => {
        msg({ type: "ROUND_RESULT", submission: { questionId: "q1", allPassed: true, score: 100 }, note: "keep" });
        msg({ type: "SUBMISSION_RESULT", questionId: "q1", score: 80, allPassed: false, passed: 3, totalTests: 5 });
        const last = gameStateManager.state.lastResult as unknown as Record<string, unknown>;
        expect(last["note"]).toBe("keep");
        expect(last["submission"]).toMatchObject({ questionId: "q1", score: 80, allPassed: false, aiFeedback: null });
    });

    test("SUBMISSION_RESULT with feedback stores it", () => {
        msg({ type: "SUBMISSION_RESULT", questionId: "q1", score: 80, allPassed: false, aiFeedback: "retry" });
        const last = gameStateManager.state.lastResult as unknown as Record<string, { aiFeedback: string }>;
        expect(last["submission"].aiFeedback).toBe("retry");
    });

    test("GAME_END ends the game with rankings and clears token and timer", () => {
        joined();
        useTimerStore.setState({ questionId: "q1", totalSec: 30, endEpochMs: 999 });
        msg({ type: "GAME_END", rankings: [{ uuid: "a", name: "A", score: 10, rank: 1 }] });
        const s = gameStateManager.state;
        expect(s.status).toBe("ENDED");
        expect(s.leaderboard.map((e) => e.uuid)).toEqual(["a"]);
        expect(s.currentQuestion).toBeNull();
        expect(s.rejoinToken).toBeNull();
        expect(useTimerStore.getState().questionId).toBeNull();
    });

    test("GAME_REVIEW seeds the podium and stores the review", () => {
        joined();
        const review = {
            type: "GAME_REVIEW",
            rankings: [{ uuid: "w", name: "W", score: 50, rank: 1 }],
            questions: [],
            players: [],
            classStats: { totalPlayers: 1, totalQuestions: 1, avgScore: 50, totalCorrect: 1, totalAttempts: 1, hardestQuestionId: "q", easiestQuestionId: "q" },
        };
        msg(review);
        const s = gameStateManager.state;
        expect(s.status).toBe("ENDED");
        expect(s.review?.rankings.map((e) => e.uuid)).toEqual(["w"]);
        expect(s.leaderboard.map((e) => e.uuid)).toEqual(["w"]);
        expect(s.rejoinToken).toBeNull();
    });

    test("GAME_REVIEW without rankings seeds an empty podium", () => {
        msg({ type: "GAME_REVIEW", questions: [], players: [], classStats: {} });
        expect(gameStateManager.state.leaderboard).toEqual([]);
        expect(gameStateManager.state.status).toBe("ENDED");
    });
});

describe("GameStateManager timer and errors", () => {
    test("TIMER_UPDATE accumulates extensions onto the live total", () => {
        msg({
            type: "QUESTION_START",
            question: { id: "q1", type: "MCQ", title: "T", description: "", timeLimitSec: 30, pointsBase: 100, languagesAllowed: null, config: {} },
            timeLimitSec: 30,
            startedAtEpochMs: 1_000,
        });
        msg({ type: "TIMER_UPDATE", extendSec: 15, newEndEpochMs: 99_000 });
        expect(useTimerStore.getState()).toMatchObject({ questionId: "q1", totalSec: 45, endEpochMs: 99_000 });
    });

    test("TIMER_UPDATE without an active question is a no-op", () => {
        msg({ type: "TIMER_UPDATE", extendSec: 15, newEndEpochMs: 99_000 });
        expect(useTimerStore.getState().questionId).toBeNull();
    });

    test("TIMER_UPDATE without extendSec keeps the total", () => {
        msg({
            type: "QUESTION_START",
            question: { id: "q1", type: "MCQ", title: "T", description: "", timeLimitSec: 30, pointsBase: 100, languagesAllowed: null, config: {} },
            timeLimitSec: 30,
            startedAtEpochMs: 1_000,
        });
        msg({ type: "TIMER_UPDATE", newEndEpochMs: 77_000 });
        expect(useTimerStore.getState()).toMatchObject({ totalSec: 30, endEpochMs: 77_000 });
    });

    test("ERROR surfaces the message", () => {
        msg({ type: "ERROR", message: "kicked" });
        expect(gameStateManager.state.error).toBe("kicked");
    });

    test("team and battle events leave the state machine alone", () => {
        const before = gameStateManager.state;
        for (const t of ["TEAM_CREATED", "TEAM_JOINED", "TEAM_LIST", "BRACKET", "SOMETHING_ELSE"]) {
            msg({ type: t });
        }
        expect(gameStateManager.state).toBe(before);
    });
});

describe("GameStateManager connection status", () => {
    test("reconnect with a session rejoins and resyncs", () => {
        gameStateManager.join("4321", "Jo", "player");
        joined("u7");
        msg({ type: "ERROR", message: "x" });
        vi.clearAllMocks();
        fakeWs.emitStatus("open");
        expect(fakeWs.send).toHaveBeenCalledWith({
            type: "JOIN",
            role: "player",
            name: "Jo",
            pin: "4321",
            rejoinToken: "tok-1",
        });
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "RESYNC_LEADERBOARD" });
    });

    test("reconnect without a session sends nothing", () => {
        fakeWs.emitStatus("open");
        expect(fakeWs.send).not.toHaveBeenCalled();
    });

    test("failed status surfaces a connection error", () => {
        fakeWs.emitStatus("failed");
        expect(gameStateManager.state.error).toBe("Connection failed after 10 retries — refresh to rejoin");
    });

    test("closed status changes nothing", () => {
        const before = gameStateManager.state;
        fakeWs.emitStatus("closed");
        expect(gameStateManager.state).toBe(before);
    });

    test("instance accessor returns the singleton and lazily creates it", () => {
        expect(GameStateManager.instance).toBe(gameStateManager);
        const internals = GameStateManager as unknown as { _instance: GameStateManager | null };
        const kept = internals._instance;
        internals._instance = null;
        const created = GameStateManager.instance;
        expect(created).not.toBe(kept);
        expect(GameStateManager.instance).toBe(created);
        internals._instance = kept;
    });
});
