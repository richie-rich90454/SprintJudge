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

describe("useTimerStore overwrite chains", () => {
    test("three pushes keep only the last question", () => {
        pushTimer("q1", 30, 1000);
        pushTimer("q2", 45, 2000);
        pushTimer("q3", 60, 3000);
        expect(useTimerStore.getState()).toEqual({ questionId: "q3", totalSec: 60, endEpochMs: 3000 });
    });

    test("push clear push clear push ends on the last push", () => {
        pushTimer("q1", 20, 1000);
        clearTimer();
        pushTimer("q2", 25, 2000);
        clearTimer();
        expect(useTimerStore.getState().questionId).toBeNull();
        pushTimer("q3", 35, 3000);
        expect(useTimerStore.getState()).toEqual({ questionId: "q3", totalSec: 35, endEpochMs: 3000 });
    });

    test("repeated clears preserve the running total", () => {
        pushTimer("q1", 45, 5000);
        clearTimer();
        clearTimer();
        clearTimer();
        const s = useTimerStore.getState();
        expect(s.questionId).toBeNull();
        expect(s.endEpochMs).toBeNull();
        expect(s.totalSec).toBe(45);
    });

    test("zero then positive then zero totalSecs overwrite cleanly", () => {
        pushTimer("q1", 0, Number.POSITIVE_INFINITY);
        pushTimer("q2", 30, 9000);
        expect(useTimerStore.getState().totalSec).toBe(30);
        pushTimer("q3", 0, Number.POSITIVE_INFINITY);
        expect(useTimerStore.getState()).toMatchObject({ questionId: "q3", totalSec: 0 });
    });

    test("finite end overwrites an infinite one and back", () => {
        pushTimer("q1", 0, Number.POSITIVE_INFINITY);
        pushTimer("q1", 30, 8000);
        expect(useTimerStore.getState().endEpochMs).toBe(8000);
        pushTimer("q1", 0, Number.POSITIVE_INFINITY);
        expect(useTimerStore.getState().endEpochMs).toBe(Number.POSITIVE_INFINITY);
    });

    test("same question re-push updates the epoch", () => {
        pushTimer("q1", 30, 1000);
        pushTimer("q1", 40, 2000);
        expect(useTimerStore.getState()).toEqual({ questionId: "q1", totalSec: 40, endEpochMs: 2000 });
    });

    test("subscribe notifies on push and clear in order then stops", () => {
        const events: Array<string | null> = [];
        const unsub = useTimerStore.subscribe((s) => events.push(s.questionId));
        pushTimer("q1", 10, 100);
        pushTimer("q2", 20, 200);
        clearTimer();
        expect(events).toEqual(["q1", "q2", null]);
        unsub();
        pushTimer("q3", 30, 300);
        expect(events).toHaveLength(3);
    });

    test("negative totalSec is stored as-is", () => {
        pushTimer("qp", -1, 1000);
        expect(useTimerStore.getState()).toMatchObject({ questionId: "qp", totalSec: -1 });
        clearTimer();
        expect(useTimerStore.getState().questionId).toBeNull();
    });

    test("ten rapid pushes keep the last write", () => {
        for (let i = 0; i < 10; i++) pushTimer(`q${i}`, i * 5, i * 1000);
        expect(useTimerStore.getState()).toEqual({ questionId: "q9", totalSec: 45, endEpochMs: 9000 });
    });

    test("clear on fresh then push then clear is a safe cycle", () => {
        clearTimer();
        pushTimer("solo", 15, 1500);
        expect(useTimerStore.getState().questionId).toBe("solo");
        clearTimer();
        expect(useTimerStore.getState()).toEqual({ questionId: null, totalSec: 15, endEpochMs: null });
    });
});
