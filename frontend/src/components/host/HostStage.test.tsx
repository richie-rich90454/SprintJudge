import { describe, test, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { HostStage } from "./HostStage";

const gameMock = vi.hoisted(() => ({
    state: {
        status: "LOBBY",
        currentQuestion: null,
        room: null,
        lastResult: null,
    } as Record<string, unknown>,
}));

const timerMock = vi.hoisted(() => ({
    state: { endEpochMs: null, totalSec: 30 } as Record<string, unknown>,
}));

vi.mock("../../stores/useGameStore", () => ({
    useGameStore: (selector: (s: Record<string, unknown>) => unknown) =>
        selector(gameMock.state),
}));

vi.mock("../../stores/useTimerStore", () => ({
    useTimerStore: (selector: (s: Record<string, unknown>) => unknown) =>
        selector(timerMock.state),
}));

describe("HostStage", () => {
    beforeEach(() => {
        gameMock.state.status = "LOBBY";
        gameMock.state.currentQuestion = null;
        gameMock.state.room = null;
        gameMock.state.lastResult = null;
    });

    test("renders lobby with PIN and join URL", () => {
        render(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.getByText(/Join the room\./)).toBeInTheDocument();
        expect(screen.getByText("1234")).toBeInTheDocument();
        expect(screen.getByText("http://localhost/join")).toBeInTheDocument();
    });

    test("renders question title when ACTIVE", () => {
        gameMock.state.status = "ACTIVE";
        gameMock.state.currentQuestion = {
            id: "q1",
            type: "MCQ",
            title: "Active Question Title",
            description: "",
            timeLimitSec: 30,
            pointsBase: 100,
            languagesAllowed: null,
            config: { options: ["A", "B"] },
        };
        render(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.getByText("Active Question Title")).toBeInTheDocument();
    });
});