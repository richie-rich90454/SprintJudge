import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { useTimerStore, pushTimer, clearTimer } from "./useTimerStore";

beforeEach(() => {
    useTimerStore.setState({ questionId: null, totalSec: 30, endEpochMs: null });
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("useTimerStore", () => {
    test("initial state is empty with 30s default", () => {
        const s = useTimerStore.getState();
        expect(s.questionId).toBeNull();
        expect(s.totalSec).toBe(30);
        expect(s.endEpochMs).toBeNull();
    });

    test("pushTimer sets all three fields", () => {
        pushTimer("q1", 45, 1700000000000);
        const s = useTimerStore.getState();
        expect(s.questionId).toBe("q1");
        expect(s.totalSec).toBe(45);
        expect(s.endEpochMs).toBe(1700000000000);
    });

    test("pushTimer overwrites previous values", () => {
        pushTimer("q1", 45, 1000);
        pushTimer("q2", 10, 2000);
        const s = useTimerStore.getState();
        expect(s.questionId).toBe("q2");
        expect(s.totalSec).toBe(10);
        expect(s.endEpochMs).toBe(2000);
    });

    test("clearTimer nulls question and epoch but keeps totalSec", () => {
        pushTimer("q1", 45, 1000);
        clearTimer();
        const s = useTimerStore.getState();
        expect(s.questionId).toBeNull();
        expect(s.endEpochMs).toBeNull();
        expect(s.totalSec).toBe(45);
    });

    test("clearTimer on fresh store is a safe no-op", () => {
        clearTimer();
        const s = useTimerStore.getState();
        expect(s.questionId).toBeNull();
        expect(s.endEpochMs).toBeNull();
    });

    test("push works after clear", () => {
        pushTimer("q1", 20, 1000);
        clearTimer();
        pushTimer("q9", 60, 9999);
        const s = useTimerStore.getState();
        expect(s.questionId).toBe("q9");
        expect(s.totalSec).toBe(60);
        expect(s.endEpochMs).toBe(9999);
    });

    test("pushTimer stores zero totalSec as-is for untimed modes", () => {
        pushTimer("q1", 0, Number.POSITIVE_INFINITY);
        const s = useTimerStore.getState();
        expect(s.totalSec).toBe(0);
        expect(s.endEpochMs).toBe(Number.POSITIVE_INFINITY);
    });
});
