import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";

const fakeManager = vi.hoisted(() => {
    const listeners: Array<(s: Record<string, unknown>) => void> = [];
    return {
        listeners,
        state: {
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
        } as Record<string, unknown>,
        connect: vi.fn(),
        join: vi.fn(),
        submit: vi.fn(),
        hostCommand: vi.fn(),
        extendTimer: vi.fn(),
        kickPlayer: vi.fn(),
        observe: () => ({
            subscribe: (cb: (s: Record<string, unknown>) => void) => {
                listeners.push(cb);
                return { unsubscribe: () => {} };
            },
        }),
    };
});

vi.mock("../services/GameStateManager", () => ({ gameStateManager: fakeManager }));

import { useGameStore } from "./useGameStore";

beforeEach(() => {
    vi.clearAllMocks();
    useGameStore.setState({
        pin: null,
        playerName: null,
        role: "player",
        error: null,
        status: "LOBBY",
    } as unknown as Parameters<typeof useGameStore.setState>[0]);
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("useGameStore delegation", () => {
    test("exposes manager state initially", () => {
        expect(useGameStore.getState().status).toBe("LOBBY");
        expect(useGameStore.getState().gameMode).toBe("STANDARD");
    });

    test("connect delegates url to manager", () => {
        useGameStore.getState().connect("ws://x/game");
        expect(fakeManager.connect).toHaveBeenCalledWith("ws://x/game");
    });

    test("join stores pin and name and defaults to player role", () => {
        useGameStore.getState().join("1234", "Ada");
        expect(useGameStore.getState().pin).toBe("1234");
        expect(useGameStore.getState().playerName).toBe("Ada");
        expect(fakeManager.join).toHaveBeenCalledWith("1234", "Ada", "player");
    });

    test("join passes explicit host role", () => {
        useGameStore.getState().join("9999", "Host", "host");
        expect(fakeManager.join).toHaveBeenCalledWith("9999", "Host", "host");
    });

    test("join overwrites previous pin and name", () => {
        useGameStore.getState().join("1111", "First");
        useGameStore.getState().join("2222", "Second");
        expect(useGameStore.getState().pin).toBe("2222");
        expect(useGameStore.getState().playerName).toBe("Second");
    });

    test("submit passes question, response and language", () => {
        const response = { selectedIndex: 1 };
        useGameStore.getState().submit("q1", response, "python");
        expect(fakeManager.submit).toHaveBeenCalledWith("q1", response, "python");
    });

    test("submit without language passes undefined", () => {
        useGameStore.getState().submit("q1", { value: true });
        expect(fakeManager.submit).toHaveBeenCalledWith("q1", { value: true }, undefined);
    });

    test("hostCommand passes action with payload", () => {
        const payload = { questionId: "q1" };
        useGameStore.getState().hostCommand("NEXT_QUESTION", payload);
        expect(fakeManager.hostCommand).toHaveBeenCalledWith("NEXT_QUESTION", payload);
    });

    test("hostCommand without payload passes undefined", () => {
        useGameStore.getState().hostCommand("END_GAME");
        expect(fakeManager.hostCommand).toHaveBeenCalledWith("END_GAME", undefined);
    });

    test("extendTimer delegates seconds", () => {
        useGameStore.getState().extendTimer(30);
        expect(fakeManager.extendTimer).toHaveBeenCalledWith(30);
    });

    test("kick delegates to kickPlayer", () => {
        useGameStore.getState().kick("uuid-9");
        expect(fakeManager.kickPlayer).toHaveBeenCalledWith("uuid-9");
    });

    test("clearError nulls the error", () => {
        useGameStore.setState({ error: "boom" } as unknown as Parameters<
            typeof useGameStore.setState
        >[0]);
        useGameStore.getState().clearError();
        expect(useGameStore.getState().error).toBeNull();
    });

    test("manager state updates propagate into the store", () => {
        const notify = fakeManager.listeners[0];
        expect(typeof notify).toBe("function");
        notify({ status: "ACTIVE", pin: "5555" } as Record<string, unknown>);
        expect(useGameStore.getState().status).toBe("ACTIVE");
        expect(useGameStore.getState().pin).toBe("5555");
    });
});

describe("useGameStore workflows", () => {
    test("join submit hostCommand extend kick fire in call order", () => {
        const s = useGameStore.getState();
        s.join("1234", "Ada", "host");
        s.submit("q1", { selectedIndex: 1 }, "python");
        s.hostCommand("NEXT_QUESTION", { questionId: "q2" });
        s.extendTimer(15);
        s.kick("u9");
        const order = [
            fakeManager.join.mock.calls.length,
            fakeManager.submit.mock.calls.length,
            fakeManager.hostCommand.mock.calls.length,
            fakeManager.extendTimer.mock.calls.length,
            fakeManager.kickPlayer.mock.calls.length,
        ];
        expect(order).toEqual([1, 1, 1, 1, 1]);
        expect(fakeManager.submit).toHaveBeenCalledWith("q1", { selectedIndex: 1 }, "python");
    });

    test("join to review to clearError cycle", () => {
        const notify = fakeManager.listeners[0];
        useGameStore.getState().join("1111", "Bo");
        notify({ status: "ACTIVE", currentQuestion: { id: "q1" } } as Record<string, unknown>);
        expect(useGameStore.getState().status).toBe("ACTIVE");
        notify({ status: "REVIEW", lastResult: { submission: { score: 80 } } } as Record<string, unknown>);
        expect(useGameStore.getState().status).toBe("REVIEW");
        notify({ error: "late" } as Record<string, unknown>);
        useGameStore.getState().clearError();
        expect(useGameStore.getState().error).toBeNull();
        expect(useGameStore.getState().status).toBe("REVIEW");
    });

    test("rejoin overwrites the session on the manager twice", () => {
        useGameStore.getState().join("0001", "First");
        useGameStore.getState().join("0002", "Second", "host");
        expect(fakeManager.join).toHaveBeenNthCalledWith(1, "0001", "First", "player");
        expect(fakeManager.join).toHaveBeenNthCalledWith(2, "0002", "Second", "host");
        expect(useGameStore.getState().pin).toBe("0002");
    });

    test("join-new-game after end swaps pin and name", () => {
        const notify = fakeManager.listeners[0];
        useGameStore.getState().join("1111", "Ada");
        notify({ status: "ENDED", leaderboard: [{ uuid: "a" }] } as Record<string, unknown>);
        expect(useGameStore.getState().status).toBe("ENDED");
        useGameStore.getState().join("2222", "Ada");
        expect(useGameStore.getState().pin).toBe("2222");
        expect(fakeManager.join).toHaveBeenLastCalledWith("2222", "Ada", "player");
    });

    test("error then join keeps the error until clearError", () => {
        const notify = fakeManager.listeners[0];
        notify({ error: "kicked" } as Record<string, unknown>);
        useGameStore.getState().join("3333", "Cy");
        expect(useGameStore.getState().error).toBe("kicked");
        useGameStore.getState().clearError();
        notify({ error: "again" } as Record<string, unknown>);
        expect(useGameStore.getState().error).toBe("again");
        useGameStore.getState().clearError();
        expect(useGameStore.getState().error).toBeNull();
    });

    test("full state snapshot propagates every field", () => {
        const notify = fakeManager.listeners[0];
        const snap = {
            status: "ACTIVE",
            pin: "7777",
            playerUuid: "u1",
            rejoinToken: "tok",
            playerName: "Dee",
            role: "host",
            currentQuestion: { id: "q9" },
            leaderboard: [{ uuid: "u1", score: 5 }],
            room: { status: "ACTIVE" },
            lastResult: null,
            error: null,
            gameMode: "TEAM",
            review: null,
        } as unknown as Record<string, unknown>;
        notify(snap);
        const s = useGameStore.getState();
        expect(s.playerUuid).toBe("u1");
        expect(s.rejoinToken).toBe("tok");
        expect(s.role).toBe("host");
        expect(s.gameMode).toBe("TEAM");
    });

    test("all three host commands delegate in sequence", () => {
        const s = useGameStore.getState();
        s.hostCommand("NEXT_QUESTION", { questionId: "q1" });
        s.hostCommand("FORCE_SUBMIT");
        s.hostCommand("END_GAME");
        expect(fakeManager.hostCommand).toHaveBeenNthCalledWith(1, "NEXT_QUESTION", { questionId: "q1" });
        expect(fakeManager.hostCommand).toHaveBeenNthCalledWith(2, "FORCE_SUBMIT", undefined);
        expect(fakeManager.hostCommand).toHaveBeenNthCalledWith(3, "END_GAME", undefined);
    });

    test("connect twice delegates both urls", () => {
        useGameStore.getState().connect("ws://a/game");
        useGameStore.getState().connect("ws://b/game");
        expect(fakeManager.connect).toHaveBeenNthCalledWith(1, "ws://a/game");
        expect(fakeManager.connect).toHaveBeenNthCalledWith(2, "ws://b/game");
    });

    test("submit passes complex responses by reference", () => {
        const response = { order: ["b", "a"], language: "python", source: "x=1" };
        useGameStore.getState().submit("qd", response);
        expect(fakeManager.submit).toHaveBeenCalledWith("qd", response, undefined);
        useGameStore.getState().submit("qd", response, "python");
        expect(fakeManager.submit).toHaveBeenLastCalledWith("qd", response, "python");
    });

    test("kick chains three uuids", () => {
        useGameStore.getState().kick("u1");
        useGameStore.getState().kick("u2");
        useGameStore.getState().kick("u3");
        expect(fakeManager.kickPlayer).toHaveBeenCalledTimes(3);
        expect(fakeManager.kickPlayer).toHaveBeenLastCalledWith("u3");
    });

    test("extendTimer accepts zero and positive chains", () => {
        useGameStore.getState().extendTimer(0);
        useGameStore.getState().extendTimer(30);
        expect(fakeManager.extendTimer).toHaveBeenNthCalledWith(1, 0);
        expect(fakeManager.extendTimer).toHaveBeenNthCalledWith(2, 30);
    });

    test("status walks lobby active review ended via notifications", () => {
        const notify = fakeManager.listeners[0];
        for (const status of ["LOBBY", "ACTIVE", "REVIEW", "ENDED"]) {
            notify({ status } as Record<string, unknown>);
            expect(useGameStore.getState().status).toBe(status);
        }
    });

    test("subscribe sees join and notification updates in order", () => {
        const seen: Array<string | null> = [];
        const unsub = useGameStore.subscribe((s) => seen.push(s.pin));
        useGameStore.getState().join("5555", "Eli");
        fakeManager.listeners[0]({ pin: "6666" } as Record<string, unknown>);
        expect(seen).toEqual(["5555", "6666"]);
        unsub();
        fakeManager.listeners[0]({ pin: "7777" } as Record<string, unknown>);
        expect(seen).toEqual(["5555", "6666"]);
    });

    test("clearError is a safe no-op chain without any error", () => {
        useGameStore.getState().clearError();
        useGameStore.getState().clearError();
        expect(useGameStore.getState().error).toBeNull();
    });

    test("host join then player join flips the tracked role", () => {
        const notify = fakeManager.listeners[0];
        useGameStore.getState().join("1111", "H", "host");
        notify({ role: "host" } as Record<string, unknown>);
        expect(useGameStore.getState().role).toBe("host");
        useGameStore.getState().join("2222", "H");
        notify({ role: "player" } as Record<string, unknown>);
        expect(useGameStore.getState().role).toBe("player");
    });

    test("review payload lands then a new join keeps store usable", () => {
        const notify = fakeManager.listeners[0];
        notify({ status: "ENDED", review: { rankings: [] } } as Record<string, unknown>);
        expect(useGameStore.getState().status).toBe("ENDED");
        useGameStore.getState().join("9999", "Zed");
        useGameStore.getState().submit("qn", { value: 1 });
        expect(fakeManager.submit).toHaveBeenCalledWith("qn", { value: 1 }, undefined);
    });
});
