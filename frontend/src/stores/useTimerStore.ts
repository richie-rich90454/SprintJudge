import { create } from "zustand";

interface TimerState {
    questionId: string | null;
    totalSec: number;
    endEpochMs: number | null;
}

/**
 * Isolated timer tick state (Q34): countdown updates no longer re-render
 * leaderboards, lobbies, or any other subscriber of the game store.
 */
export const useTimerStore = create<TimerState>(() => ({
    questionId: null,
    totalSec: 30,
    endEpochMs: null,
}));

export function pushTimer(questionId: string, totalSec: number, endEpochMs: number) {
    useTimerStore.setState({ questionId, totalSec, endEpochMs });
}

export function clearTimer() {
    useTimerStore.setState({ questionId: null, endEpochMs: null });
}
