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
