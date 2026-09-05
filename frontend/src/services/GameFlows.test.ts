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

import { gameStateManager } from "./GameStateManager";
import { useTimerStore } from "../stores/useTimerStore";

type Msg = Record<string, unknown>;

function msg(m: Msg) {
    fakeWs.emitMessage(m);
}

function q(id: string, extra: Msg = {}): Msg {
    return {
        id,
        type: "MCQ",
        title: `Title ${id}`,
        description: "",
        timeLimitSec: 30,
        pointsBase: 100,
        languagesAllowed: null,
        config: {},
        ...extra,
    };
}

function joined(uuid = "u1", token: unknown = "tok-1", status = "LOBBY"): void {
    msg({
        type: "JOINED",
        uuid,
        rejoinToken: token,
        room: { type: "ROOM_STATE", status, players: [], gameMode: "STANDARD" },
    });
}

function room(players: Array<{ uuid: string; name: string }>, extra: Msg = {}): void {
    msg({
        type: "ROOM_STATE",
        status: "LOBBY",
        questionCount: 3,
        currentQuestionId: null,
        players: players.map((p) => ({ ...p, score: 0 })),
        gameMode: "STANDARD",
        ...extra,
    });
}

function qstart(id: string, tl = 30, at = 10_000, extra: Msg = {}): void {
    msg({ type: "QUESTION_START", question: q(id), timeLimitSec: tl, startedAtEpochMs: at, ...extra });
}

function delta(seq: number, entries: unknown[], resync = false): void {
    msg({ type: "LEADERBOARD_DELTA", seq, resync, entries });
}

function entry(uuid: string, rank: number, score = rank * 10): Msg {
    return { uuid, name: `P-${uuid}`, score, rank };
}

function fullArc(pin: string, name: string, qid: string): void {
    gameStateManager.join(pin, name);
    joined();
    room([{ uuid: "u1", name }]);
    qstart(qid);
    gameStateManager.submit(qid, { selectedIndex: 2 });
    msg({ type: "SUBMISSION_RESULT", questionId: qid, score: 90, allPassed: true });
    msg({ type: "ROUND_RESULT", submission: { questionId: qid, allPassed: true, score: 90 } });
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

describe("GameFlows standard full-game arcs", () => {
    test("join to review arc ends with a seeded podium and cleared token", () => {
        fullArc("1111", "Ada", "q1");
        qstart("q2");
        gameStateManager.submit("q2", { selectedIndex: 0 });
        msg({ type: "SUBMISSION_RESULT", questionId: "q2", score: 40, allPassed: false });
        msg({ type: "ROUND_RESULT", submission: { questionId: "q2", allPassed: false, score: 40 } });
        expect(gameStateManager.state.status).toBe("REVIEW");
        msg({
            type: "GAME_REVIEW",
            rankings: [entry("u1", 1, 130)],
            questions: [],
            players: [],
            classStats: {},
        });
        const s = gameStateManager.state;
        expect(s.status).toBe("ENDED");
        expect(s.leaderboard.map((e) => e.uuid)).toEqual(["u1"]);
        expect(s.currentQuestion).toBeNull();
        expect(s.rejoinToken).toBeNull();
        expect(useTimerStore.getState().questionId).toBeNull();
    });

    test("game-end arc via GAME_END keeps rankings and wipes the question", () => {
        fullArc("2222", "Bo", "q1");
        delta(0, [entry("u1", 1, 90)]);
        msg({ type: "GAME_END", rankings: [entry("u1", 1, 90)] });
        const s = gameStateManager.state;
        expect(s.status).toBe("ENDED");
        expect(s.leaderboard).toHaveLength(1);
        expect(s.currentQuestion).toBeNull();
        expect(s.rejoinToken).toBeNull();
    });

    test("three-question streak accumulates deltas then reviews", () => {
        gameStateManager.join("3333", "Cy");
        joined();
        for (let i = 1; i <= 3; i++) {
            qstart(`q${i}`);
            gameStateManager.submit(`q${i}`, { selectedIndex: i % 4 });
            msg({ type: "SUBMISSION_RESULT", questionId: `q${i}`, score: i * 10, allPassed: i !== 2 });
            msg({ type: "ROUND_RESULT", submission: { questionId: `q${i}`, allPassed: i !== 2, score: i * 10 } });
            expect(gameStateManager.state.status).toBe("REVIEW");
            delta(i - 1, [entry("u1", 1, i * 10)]);
        }
        expect(gameStateManager.state.leaderboard[0].score).toBe(30);
        msg({ type: "GAME_END", rankings: [entry("u1", 1, 60)] });
        expect(gameStateManager.state.status).toBe("ENDED");
    });

    test("practice-mode arc never arms a timed clock", () => {
        gameStateManager.join("4444", "Dan");
        joined();
        room([{ uuid: "u1", name: "Dan" }], { gameMode: "PRACTICE" });
        expect(gameStateManager.state.gameMode).toBe("PRACTICE");
        qstart("qp", -1);
        expect(useTimerStore.getState().totalSec).toBe(0);
        gameStateManager.submit("qp", { selectedIndex: 1 });
        msg({ type: "SUBMISSION_RESULT", questionId: "qp", score: 100, allPassed: true });
        msg({ type: "ROUND_RESULT", submission: { questionId: "qp", allPassed: true, score: 100 } });
        expect(gameStateManager.state.status).toBe("REVIEW");
        expect(useTimerStore.getState().questionId).toBeNull();
    });

    test("exam-mode arc preserves per-question results across rounds", () => {
        gameStateManager.join("5555", "Ex");
        joined();
        room([{ uuid: "u1", name: "Ex" }], { gameMode: "EXAM" });
        qstart("qe1", 60);
        gameStateManager.submit("qe1", { text: "answer one" });
        msg({ type: "SUBMISSION_RESULT", questionId: "qe1", score: 70, allPassed: false, passed: 7, totalTests: 10, aiFeedback: "partial" });
        expect(gameStateManager.state.lastResult?.submission.aiFeedback).toBe("partial");
        msg({ type: "ROUND_RESULT", submission: { questionId: "qe1", allPassed: false, score: 70 } });
        qstart("qe2", 60);
        expect(gameStateManager.state.currentQuestion?.id).toBe("qe2");
        gameStateManager.submit("qe2", { text: "answer two" });
        msg({ type: "SUBMISSION_RESULT", questionId: "qe2", score: 100, allPassed: true });
        expect(gameStateManager.state.lastResult?.submission.questionId).toBe("qe2");
    });

    test("team-mode arc tracks the roster game mode end to end", () => {
        gameStateManager.join("6666", "Tm");
        joined();
        room(
            [
                { uuid: "u1", name: "Tm" },
                { uuid: "u2", name: "Mate" },
            ],
            { gameMode: "TEAM" },
        );
        delta(0, [entry("u1", 2, 20), entry("u2", 1, 40)]);
        qstart("qt");
        gameStateManager.submit("qt", { selectedIndices: [0, 2] });
        msg({ type: "ROUND_RESULT", submission: { questionId: "qt", allPassed: true, score: 50 } });
        expect(gameStateManager.state.gameMode).toBe("TEAM");
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["u2", "u1"]);
    });

    test("battle-mode arc prunes a kicked player mid-game", () => {
        gameStateManager.join("7777", "Bt");
        joined();
        room(
            [
                { uuid: "u1", name: "Bt" },
                { uuid: "u2", name: "Rival" },
            ],
            { gameMode: "BATTLE" },
        );
        delta(0, [entry("u1", 1, 30), entry("u2", 2, 20)]);
        qstart("qb");
        room([{ uuid: "u1", name: "Bt" }], { gameMode: "BATTLE", status: "ACTIVE" });
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["u1"]);
        gameStateManager.submit("qb", { value: 42 });
        msg({ type: "ROUND_RESULT", submission: { questionId: "qb", allPassed: true, score: 10 } });
        expect(gameStateManager.state.status).toBe("REVIEW");
    });

    test("auto-pilot arc runs start to review without submissions", () => {
        gameStateManager.join("8888", "Ap");
        joined();
        room([{ uuid: "u1", name: "Ap" }], { gameMode: "AUTO_PILOT" });
        qstart("qa1", 10);
        msg({ type: "ROUND_RESULT", submission: { questionId: "qa1", allPassed: false, score: 0 } });
        expect(gameStateManager.state.status).toBe("REVIEW");
        qstart("qa2", 10);
        expect(gameStateManager.state.status).toBe("ACTIVE");
        msg({ type: "GAME_END", rankings: [entry("u1", 1, 0)] });
        expect(gameStateManager.state.status).toBe("ENDED");
    });

    test("coding-question arc carries source plus language through submit", () => {
        gameStateManager.join("9999", "Coder");
        joined();
        msg({
            type: "QUESTION_START",
            question: q("qc", { type: "OJ_FULL", config: { starter: "print(1)" } }),
            timeLimitSec: 120,
            startedAtEpochMs: 5_000,
        });
        gameStateManager.submit("qc", { source: "print(2)", language: "python" }, "python");
        expect(fakeWs.send).toHaveBeenCalledWith({
            type: "SUBMIT",
            questionId: "qc",
            response: { source: "print(2)", language: "python" },
            language: "python",
        });
        msg({ type: "SUBMISSION_RESULT", questionId: "qc", score: 100, allPassed: true, passed: 5, totalTests: 5 });
        expect(gameStateManager.state.lastResult?.submission.passed).toBe(5);
    });

    test("submit before any question still sends and later results merge", () => {
        gameStateManager.submit("early", { selectedIndex: 0 });
        expect(fakeWs.send).toHaveBeenCalledWith({
            type: "SUBMIT",
            questionId: "early",
            response: { selectedIndex: 0 },
            language: undefined,
        });
        qstart("early");
        msg({ type: "SUBMISSION_RESULT", questionId: "early", score: 10, allPassed: true });
        expect(gameStateManager.state.lastResult?.submission.score).toBe(10);
    });
});

describe("GameFlows double-join resets", () => {
    test("second join wipes uuid, token, board, question, review and drafts", () => {
        gameStateManager.join("1111", "First");
        joined("u-first", "tok-first");
        localStorage.setItem("sprintjudge_code_q1", "stale-code");
        delta(0, [entry("u-first", 1, 50)]);
        qstart("q1");
        msg({ type: "ROUND_RESULT", submission: { questionId: "q1", allPassed: true, score: 50 } });
        gameStateManager.join("2222", "Second");
        const mid = gameStateManager.state;
        expect(mid.pin).toBe("2222");
        expect(mid.playerName).toBe("Second");
        expect(mid.playerUuid).toBeNull();
        expect(mid.rejoinToken).toBeNull();
        expect(mid.leaderboard).toEqual([]);
        expect(mid.currentQuestion).toBeNull();
        expect(mid.lastResult).toBeNull();
        expect(mid.review).toBeNull();
        joined("u-second", "tok-second");
        expect(localStorage.getItem("sprintjudge_code_q1")).toBeNull();
        expect(gameStateManager.state.playerUuid).toBe("u-second");
        delta(7, [entry("u-second", 1, 5)]);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["u-second"]);
    });

    test("join after game end starts a clean lobby arc", () => {
        fullArc("1111", "Ada", "q1");
        msg({ type: "GAME_END", rankings: [entry("u1", 1, 90)] });
        expect(gameStateManager.state.status).toBe("ENDED");
        gameStateManager.join("9999", "Ada");
        expect(gameStateManager.state.status).toBe("LOBBY");
        joined("u-new");
        qstart("qn");
        expect(gameStateManager.state.status).toBe("ACTIVE");
        expect(gameStateManager.state.leaderboard).toEqual([]);
    });

    test("triple join keeps only the latest session", () => {
        gameStateManager.join("0001", "A");
        joined("u-a", "tok-a");
        gameStateManager.join("0002", "B");
        joined("u-b", "tok-b");
        gameStateManager.join("0003", "C");
        joined("u-c", "tok-c");
        const s = gameStateManager.state;
        expect(s.pin).toBe("0003");
        expect(s.playerName).toBe("C");
        expect(s.playerUuid).toBe("u-c");
        expect(s.rejoinToken).toBe("tok-c");
        fakeWs.emitStatus("open");
        expect(fakeWs.send).toHaveBeenCalledWith({
            type: "JOIN",
            role: "player",
            name: "C",
            pin: "0003",
            rejoinToken: "tok-c",
        });
    });

    test("join clears several cached drafts at once", () => {
        gameStateManager.join("1234", "Drafter");
        joined();
        localStorage.setItem("sprintjudge_code_q1", "a");
        localStorage.setItem("sprintjudge_code_q2", "b");
        localStorage.setItem("sprintjudge_code_q3", "c");
        localStorage.setItem("unrelated", "keep");
        gameStateManager.join("5678", "Drafter");
        joined();
        expect(localStorage.getItem("sprintjudge_code_q1")).toBeNull();
        expect(localStorage.getItem("sprintjudge_code_q2")).toBeNull();
        expect(localStorage.getItem("sprintjudge_code_q3")).toBeNull();
        expect(localStorage.getItem("unrelated")).toBe("keep");
    });

    test("role flips back to player when rejoining as player after hosting", () => {
        gameStateManager.join("1111", "Hosty", "host");
        joined();
        expect(gameStateManager.state.role).toBe("host");
        gameStateManager.join("2222", "Hosty");
        expect(gameStateManager.state.role).toBe("player");
        expect(fakeWs.send).toHaveBeenLastCalledWith({ type: "JOIN", role: "player", name: "Hosty", pin: "2222" });
    });
});

describe("GameFlows host-role arcs", () => {
    test("host runs next, force-submit and end commands in one game", () => {
        gameStateManager.join("4321", "Host", "host");
        joined("host-1");
        gameStateManager.hostCommand("NEXT_QUESTION", { questionId: "q1" });
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "NEXT_QUESTION", questionId: "q1" });
        qstart("q1");
        expect(gameStateManager.state.status).toBe("ACTIVE");
        gameStateManager.hostCommand("FORCE_SUBMIT");
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "FORCE_SUBMIT" });
        msg({ type: "ROUND_RESULT", submission: { questionId: "q1", allPassed: true, score: 10 } });
        qstart("q2");
        gameStateManager.hostCommand("END_GAME");
        msg({ type: "GAME_END", rankings: [] });
        expect(gameStateManager.state.status).toBe("ENDED");
    });

    test("host reconnect arc rejoins with host role and token", () => {
        gameStateManager.join("7777", "Host", "host");
        joined("host-9", "tok-host");
        msg({ type: "ERROR", message: "clear-gate" });
        vi.clearAllMocks();
        fakeWs.emitStatus("open");
        expect(fakeWs.send).toHaveBeenCalledWith({
            type: "JOIN",
            role: "host",
            name: "Host",
            pin: "7777",
            rejoinToken: "tok-host",
        });
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "RESYNC_LEADERBOARD" });
    });

    test("host kick flow removes the player from board via roster prune", () => {
        gameStateManager.join("1234", "Host", "host");
        joined("host-1");
        room(
            [
                { uuid: "host-1", name: "Host" },
                { uuid: "gone-1", name: "Gone" },
            ],
            { status: "ACTIVE" },
        );
        delta(0, [entry("host-1", 1, 10), entry("gone-1", 2, 5)]);
        gameStateManager.kickPlayer("gone-1");
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "KICK_PLAYER", playerUuid: "gone-1" });
        room([{ uuid: "host-1", name: "Host" }], { status: "ACTIVE" });
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["host-1"]);
    });

    test("host extend-timer flow accumulates across three extensions", () => {
        gameStateManager.join("1234", "Host", "host");
        joined("host-1");
        qstart("qh", 30, 1_000);
        gameStateManager.extendTimer(10);
        msg({ type: "TIMER_UPDATE", extendSec: 10, newEndEpochMs: 41_000 });
        gameStateManager.extendTimer(20);
        msg({ type: "TIMER_UPDATE", extendSec: 20, newEndEpochMs: 61_000 });
        const s = useTimerStore.getState();
        expect(s.totalSec).toBe(60);
        expect(s.endEpochMs).toBe(61_000);
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "EXTEND_TIMER", seconds: 10 });
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "EXTEND_TIMER", seconds: 20 });
    });
});

describe("GameFlows disconnect and reconnect arcs", () => {
    test("drop mid-question then reclaim keeps the question and board", () => {
        gameStateManager.join("1212", "Jo");
        joined("u7", "tok-7");
        room([{ uuid: "u7", name: "Jo" }], { status: "ACTIVE" });
        qstart("qm", 45, 2_000);
        delta(0, [entry("u7", 1, 25)]);
        msg({ type: "ERROR", message: "clear-gate" });
        vi.clearAllMocks();
        fakeWs.emitStatus("closed");
        expect(gameStateManager.state.currentQuestion?.id).toBe("qm");
        fakeWs.emitStatus("open");
        expect(fakeWs.send).toHaveBeenCalledWith({
            type: "JOIN",
            role: "player",
            name: "Jo",
            pin: "1212",
            rejoinToken: "tok-7",
        });
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "RESYNC_LEADERBOARD" });
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["u7"]);
        expect(gameStateManager.state.currentQuestion?.id).toBe("qm");
    });

    test("reconnect with no live session sends nothing and changes nothing", () => {
        gameStateManager.join("0000", "Nobody");
        vi.clearAllMocks();
        fakeWs.emitStatus("open");
        expect(fakeWs.send).not.toHaveBeenCalled();
    });

    test("double reconnect replays join twice with the same token", () => {
        gameStateManager.join("3434", "Rep");
        joined("u-r", "tok-r");
        vi.clearAllMocks();
        fakeWs.emitStatus("open");
        fakeWs.emitStatus("open");
        const joins = fakeWs.send.mock.calls.filter((c) => (c[0] as Msg).type === "JOIN");
        expect(joins).toHaveLength(2);
        expect(joins[0][0]).toEqual({ type: "JOIN", role: "player", name: "Rep", pin: "3434", rejoinToken: "tok-r" });
    });

    test("failed status after drop surfaces the refresh error without wiping the board", () => {
        gameStateManager.join("5656", "Unlucky");
        joined("u-f", "tok-f");
        delta(0, [entry("u-f", 1, 12)]);
        fakeWs.emitStatus("failed");
        expect(gameStateManager.state.error).toBe("Connection failed after 10 retries — refresh to rejoin");
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["u-f"]);
        gameStateManager.join("5656", "Unlucky");
        expect(gameStateManager.state.error).toBeNull();
    });

    test("reconnect resync replaces a stale board authoritatively", () => {
        gameStateManager.join("7878", "Stale");
        joined("u-s", "tok-s");
        delta(0, [entry("u-s", 1, 10)]);
        fakeWs.emitStatus("open");
        vi.clearAllMocks();
        delta(1, [entry("u-s", 1, 99), entry("new", 2, 1)], true);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["u-s", "new"]);
        expect(gameStateManager.state.leaderboard[0].score).toBe(99);
    });

    test("connect resets the delta baseline so old sequences are accepted again", () => {
        delta(4, [entry("a", 1, 10)]);
        gameStateManager.connect("ws://host/game");
        expect(fakeWs.connect).toHaveBeenCalledWith("ws://host/game");
        delta(1, [entry("b", 1, 7)]);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toContain("b");
    });
});

describe("GameFlows sequence gaps and roster prunes", () => {
    test("gap then resync then continued increments stay ordered", () => {
        joined();
        delta(0, [entry("a", 2, 20), entry("b", 1, 40)]);
        delta(2, [entry("gap", 1, 999)]);
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "RESYNC_LEADERBOARD" });
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["b", "a"]);
        delta(1, [entry("c", 3, 5)], true);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["c"]);
        delta(2, [entry("d", 1, 100)]);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["d", "c"]);
    });

    test("interleaved roster prune between gap and resync drops the departed", () => {
        joined();
        delta(0, [entry("a", 1, 30), entry("b", 2, 20)]);
        room(
            [
                { uuid: "a", name: "A" },
                { uuid: "b", name: "B" },
            ],
            { status: "ACTIVE" },
        );
        delta(3, [entry("ghost", 1, 500)]);
        room([{ uuid: "a", name: "A" }], { status: "ACTIVE" });
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["a"]);
        delta(1, [entry("a", 1, 35), entry("b", 2, 20)], true);
        room([{ uuid: "a", name: "A" }], { status: "ACTIVE" });
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["a"]);
    });

    test("empty roster prunes the whole board mid-round", () => {
        joined();
        delta(0, [entry("a", 1, 10), entry("b", 2, 5)]);
        room([], { status: "ACTIVE" });
        expect(gameStateManager.state.leaderboard).toEqual([]);
        qstart("qz");
        expect(gameStateManager.state.status).toBe("ACTIVE");
    });

    test("duplicate deltas around a prune never resurrect the departed", () => {
        joined();
        delta(0, [entry("a", 1, 10), entry("b", 2, 9)]);
        room([{ uuid: "a", name: "A" }], { status: "ACTIVE" });
        delta(0, [entry("b", 1, 99)]);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["a"]);
    });

    test("resync flag with unsorted entries sorts by rank", () => {
        joined();
        delta(10, [entry("z", 5, 1), entry("y", 2, 2), entry("x", 1, 3)], true);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["x", "y", "z"]);
    });

    test("incremental updates preserve ranks across three merges", () => {
        joined();
        delta(0, [entry("a", 3, 3), entry("b", 2, 6), entry("c", 1, 9)], true);
        delta(1, [entry("a", 1, 30)]);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["c", "a", "b"]);
        delta(2, [entry("b", 1, 60), entry("c", 2, 55)]);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["a", "b", "c"]);
    });
});

describe("GameFlows error storms", () => {
    test("ten sequential errors keep the game state consistent", () => {
        fullArc("1111", "Ada", "q1");
        const boardBefore = gameStateManager.state.leaderboard;
        for (let i = 0; i < 10; i++) msg({ type: "ERROR", message: `err-${i}` });
        const s = gameStateManager.state;
        expect(s.error).toBe("err-9");
        expect(s.status).toBe("REVIEW");
        expect(s.currentQuestion?.id).toBe("q1");
        expect(s.leaderboard).toBe(boardBefore);
        gameStateManager.join("1111", "Ada");
        expect(gameStateManager.state.error).toBeNull();
    });

    test("errors interleaved with deltas do not disturb the board", () => {
        joined();
        delta(0, [entry("a", 1, 10)]);
        msg({ type: "ERROR", message: "boom-1" });
        delta(1, [entry("b", 2, 5)]);
        msg({ type: "ERROR", message: "boom-2" });
        delta(2, [entry("c", 3, 1)]);
        expect(gameStateManager.state.leaderboard).toHaveLength(3);
        expect(gameStateManager.state.error).toBe("boom-2");
    });

    test("error during active question keeps the timer armed", () => {
        joined();
        qstart("qe", 30, 1_000);
        msg({ type: "ERROR", message: "hmm" });
        msg({ type: "ERROR", message: "hmm-again" });
        expect(useTimerStore.getState().questionId).toBe("qe");
        expect(gameStateManager.state.status).toBe("ACTIVE");
        expect(gameStateManager.state.error).toBe("hmm-again");
    });

    test("error clears the resync gate mid-storm so recovery can resync", () => {
        joined();
        delta(0, [entry("a", 1, 10)]);
        delta(5, [entry("gap", 1, 1)]);
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "RESYNC_LEADERBOARD" });
        msg({ type: "ERROR", message: "storm" });
        gameStateManager.requestLeaderboardResync();
        const resyncs = fakeWs.send.mock.calls.filter((c) => (c[0] as Msg).type === "RESYNC_LEADERBOARD");
        expect(resyncs.length).toBeGreaterThanOrEqual(2);
    });

    test("empty error message still surfaces and recovers on next join", () => {
        msg({ type: "ERROR", message: "" });
        expect(gameStateManager.state.error).toBe("");
        msg({ type: "ERROR", message: "real" });
        expect(gameStateManager.state.error).toBe("real");
        gameStateManager.join("0000", "Tester");
        expect(gameStateManager.state.error).toBeNull();
    });
});

describe("GameFlows timer extend chains", () => {
    test("four extensions accumulate onto the question base", () => {
        qstart("qt", 30, 1_000);
        const ends = [35_000, 42_000, 50_000, 61_000];
        const adds = [5, 7, 8, 11];
        let total = 30;
        adds.forEach((a, i) => {
            msg({ type: "TIMER_UPDATE", extendSec: a, newEndEpochMs: ends[i] });
            total += a;
            expect(useTimerStore.getState().totalSec).toBe(total);
            expect(useTimerStore.getState().endEpochMs).toBe(ends[i]);
        });
        expect(useTimerStore.getState().questionId).toBe("qt");
    });

    test("new question resets the accumulated total", () => {
        qstart("q1", 30, 1_000);
        msg({ type: "TIMER_UPDATE", extendSec: 30, newEndEpochMs: 61_000 });
        expect(useTimerStore.getState().totalSec).toBe(60);
        qstart("q2", 20, 100_000);
        expect(useTimerStore.getState()).toMatchObject({ questionId: "q2", totalSec: 20, endEpochMs: 120_000 });
        msg({ type: "TIMER_UPDATE", extendSec: 5, newEndEpochMs: 125_000 });
        expect(useTimerStore.getState().totalSec).toBe(25);
    });

    test("string extendSec coerces numerically", () => {
        qstart("qs", 30, 1_000);
        msg({ type: "TIMER_UPDATE", extendSec: "10", newEndEpochMs: 41_000 });
        expect(useTimerStore.getState().totalSec).toBe(40);
    });

    test("round result clears an extended timer", () => {
        qstart("qc", 30, 1_000);
        msg({ type: "TIMER_UPDATE", extendSec: 50, newEndEpochMs: 81_000 });
        expect(useTimerStore.getState().totalSec).toBe(80);
        msg({ type: "ROUND_RESULT", submission: { questionId: "qc", allPassed: true, score: 5 } });
        expect(useTimerStore.getState().questionId).toBeNull();
    });
});

describe("GameFlows malformed messages", () => {
    test("bare type-only messages never crash", () => {
        const before = gameStateManager.state.status;
        for (const t of ["QUESTION_START", "ROOM_STATE", "TIMER_UPDATE", "ERROR", "SUBMISSION_RESULT", "ROUND_RESULT"] as const) {
            expect(() => msg({ type: t })).not.toThrow();
        }
        expect(gameStateManager.state.currentQuestion).toBeNull();
        expect(gameStateManager.state.status).not.toBe("ACTIVE");
        void before;
    });

    test("wrong-typed fields fall back instead of crashing", () => {
        expect(() => {
            msg({ type: "JOINED", uuid: "u9", rejoinToken: 123, room: null });
            msg({ type: "ROOM_STATE", players: null, gameMode: null, status: "ACTIVE" });
            msg({ type: "QUESTION_START", question: null, timeLimitSec: "lots", startedAtEpochMs: "now" });
            msg({ type: "TIMER_UPDATE", extendSec: null, newEndEpochMs: null });
            msg({ type: "ERROR", message: null });
            msg({ type: "SUBMISSION_RESULT" });
        }).not.toThrow();
        expect(gameStateManager.state.gameMode).toBe("STANDARD");
    });

    test("null rankings on game end and review never crash", () => {
        joined();
        expect(() => msg({ type: "GAME_END", rankings: null })).not.toThrow();
        expect(gameStateManager.state.status).toBe("ENDED");
        gameStateManager.join("0000", "Tester");
        joined();
        expect(() => msg({ type: "GAME_REVIEW", rankings: null, questions: null, players: null, classStats: null })).not.toThrow();
        expect(gameStateManager.state.status).toBe("ENDED");
        expect(gameStateManager.state.leaderboard).toEqual([]);
    });

    test("null and undefined payload values flow through submit and host commands", () => {
        expect(() => {
            gameStateManager.submit("q1", null);
            gameStateManager.submit("q1", undefined, undefined);
            gameStateManager.hostCommand("NEXT_QUESTION", undefined);
            gameStateManager.hostCommand("END_GAME", {} as Record<string, unknown>);
            gameStateManager.extendTimer(0);
            gameStateManager.kickPlayer("");
        }).not.toThrow();
        expect(fakeWs.send).toHaveBeenCalledWith({ type: "SUBMIT", questionId: "q1", response: null, language: undefined });
    });

    test("deeply nested unknown fields are ignored safely", () => {
        const before = gameStateManager.state;
        expect(() => {
            msg({ type: "ROOM_STATE", status: "LOBBY", players: [{ uuid: "a" }], nested: { deep: [1, { two: true }] } });
            msg({ type: "LEADERBOARD_DELTA", seq: 0, resync: true, entries: [{ uuid: "a", name: "A", score: "many", rank: "first" }] });
            msg({ type: "QUESTION_START", question: { id: "qx" }, extra: [[["x"]]] });
        }).not.toThrow();
        expect(gameStateManager.state.room?.status).toBe("LOBBY");
        void before;
    });

    test("observe stream survives a malformed storm and still notifies", () => {
        const seen: string[] = [];
        const sub = gameStateManager.observe().subscribe((s) => seen.push(s.status));
        msg({ type: "BOGUS" });
        msg({ type: "QUESTION_START" });
        msg({ type: "ERROR" });
        qstart("qo");
        expect(seen).toContain("ACTIVE");
        sub.unsubscribe();
        const n = seen.length;
        qstart("qo2");
        expect(seen).toHaveLength(n);
    });
});

describe("GameFlows mixed sagas", () => {
    test("observe subscriber sees the whole join to active arc in order", () => {
        const seen: string[] = [];
        const sub = gameStateManager.observe().subscribe((s) => seen.push(s.status));
        gameStateManager.join("4242", "Watcher");
        joined();
        qstart("qw");
        expect(seen).toEqual(expect.arrayContaining(["LOBBY", "LOBBY", "ACTIVE"]));
        expect(seen[seen.length - 1]).toBe("ACTIVE");
        sub.unsubscribe();
    });

    test("leaderboard-only session without questions stays consistent", () => {
        joined();
        room(
            [
                { uuid: "a", name: "A" },
                { uuid: "b", name: "B" },
                { uuid: "c", name: "C" },
            ],
            { status: "LOBBY" },
        );
        delta(0, [entry("a", 1, 10), entry("b", 2, 8), entry("c", 3, 6)]);
        room(
            [
                { uuid: "b", name: "B" },
                { uuid: "c", name: "C" },
            ],
            { status: "LOBBY" },
        );
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["b", "c"]);
        delta(1, [entry("c", 1, 20)]);
        expect(gameStateManager.state.leaderboard.map((e) => e.uuid)).toEqual(["c", "b"]);
    });

    test("submission results accumulate before the round result lands", () => {
        qstart("qm");
        msg({ type: "SUBMISSION_RESULT", questionId: "qm", score: 10, allPassed: false, passed: 1, totalTests: 5 });
        expect(gameStateManager.state.lastResult?.submission.score).toBe(10);
        msg({ type: "SUBMISSION_RESULT", questionId: "qm", score: 60, allPassed: false, passed: 3, totalTests: 5 });
        expect(gameStateManager.state.lastResult?.submission.score).toBe(60);
        msg({ type: "SUBMISSION_RESULT", questionId: "qm", score: 100, allPassed: true, passed: 5, totalTests: 5, aiFeedback: "nice" });
        expect(gameStateManager.state.lastResult?.submission.aiFeedback).toBe("nice");
        msg({ type: "ROUND_RESULT", submission: { questionId: "qm", allPassed: true, score: 100 } });
        expect(gameStateManager.state.status).toBe("REVIEW");
    });

    test("question supersedes question without a round result in between", () => {
        qstart("q-old", 30, 1_000);
        qstart("q-new", 45, 50_000);
        expect(gameStateManager.state.currentQuestion?.id).toBe("q-new");
        expect(useTimerStore.getState()).toMatchObject({ questionId: "q-new", totalSec: 45, endEpochMs: 95_000 });
        gameStateManager.submit("q-new", { selectedIndex: 3 });
        msg({ type: "ROUND_RESULT", submission: { questionId: "q-new", allPassed: true, score: 20 } });
        expect(gameStateManager.state.status).toBe("REVIEW");
    });

    test("full reconnect saga mid-round then finish to review", () => {
        gameStateManager.join("9090", "Saga");
        joined("u-saga", "tok-saga");
        room([{ uuid: "u-saga", name: "Saga" }], { status: "ACTIVE" });
        qstart("qg", 30, 1_000);
        delta(0, [entry("u-saga", 1, 10)]);
        fakeWs.emitStatus("closed");
        fakeWs.emitStatus("open");
        expect(fakeWs.send).toHaveBeenCalledWith({
            type: "JOIN",
            role: "player",
            name: "Saga",
            pin: "9090",
            rejoinToken: "tok-saga",
        });
        gameStateManager.submit("qg", { selectedIndex: 1 });
        msg({ type: "ROUND_RESULT", submission: { questionId: "qg", allPassed: true, score: 10 } });
        expect(gameStateManager.state.status).toBe("REVIEW");
        expect(gameStateManager.state.currentQuestion?.id).toBe("qg");
    });

    test("game review then a brand-new game leaves no review residue", () => {
        fullArc("1111", "Ada", "q1");
        msg({ type: "GAME_REVIEW", rankings: [entry("u1", 1, 90)], questions: [], players: [], classStats: {} });
        expect(gameStateManager.state.review).not.toBeNull();
        gameStateManager.join("2222", "Ada");
        joined("u-fresh");
        expect(gameStateManager.state.review).toBeNull();
        expect(gameStateManager.state.status).toBe("LOBBY");
        expect(gameStateManager.state.leaderboard).toEqual([]);
    });
});
