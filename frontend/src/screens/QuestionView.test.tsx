import { describe, test, expect, vi, beforeEach } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { QuestionView } from "./QuestionView";

const gameMock = vi.hoisted(() => ({
    state: {
        status: "LOBBY",
        currentQuestion: null,
        gameMode: "STANDARD",
        room: null,
        playerUuid: null,
        pin: null,
        resume: vi.fn(),
        submit: vi.fn(),
        error: null,
        lastResult: null,
        teams: [],
        bracket: null,
        battleOpponent: null,
        battleResult: null,
        champion: null,
    } as Record<string, unknown>,
}));

const timerMock = vi.hoisted(() => ({
    state: { endEpochMs: null, totalSec: 30 } as Record<string, unknown>,
}));

vi.mock("../stores/useGameStore", () => ({
    useGameStore: (selector: (s: Record<string, unknown>) => unknown) =>
        selector(gameMock.state),
}));

vi.mock("../stores/useTimerStore", () => ({
    useTimerStore: (selector: (s: Record<string, unknown>) => unknown) =>
        selector(timerMock.state),
}));

vi.mock("@tanstack/react-router", () => ({
    Link: ({ children, to }: { children: unknown; to: string }) => (
        <a href={to}>{children as any}</a>
    ),
    Navigate: () => null,
    useNavigate: () => vi.fn(),
}));

const rendererMock = vi.hoisted(() => ({
    props: null as null | {
        onResponse: (r: unknown) => void;
        revealSignal?: number;
    },
}));

vi.mock("../components/QuestionRendererHost", () => ({
    QuestionRendererHost: (props: {
        onResponse: (r: unknown) => void;
        revealSignal?: number;
    }) => {
        rendererMock.props = props;
        return null;
    },
}));

vi.mock("../services/AudioEngine", () => ({
    audio: { play: vi.fn() },
}));

describe("QuestionView", () => {
    beforeEach(() => {
        gameMock.state.status = "LOBBY";
        gameMock.state.currentQuestion = null;
        gameMock.state.gameMode = "STANDARD";
        gameMock.state.room = null;
        gameMock.state.playerUuid = null;
        gameMock.state.pin = null;
        gameMock.state.error = null;
        gameMock.state.lastResult = null;
        gameMock.state.resume = vi.fn();
        gameMock.state.submit = vi.fn();
        timerMock.state.endEpochMs = null;
        timerMock.state.totalSec = 30;
        rendererMock.props = null;
        localStorage.clear();
    });

    test("renders No game when no pin/session", () => {
        render(<QuestionView />);
        expect(screen.getByText("No game")).toBeInTheDocument();
    });

    test("renders standby when joined but no question", () => {
        gameMock.state.playerUuid = "u1";
        render(<QuestionView />);
        expect(screen.getByText("Standby")).toBeInTheDocument();
    });

    test("renders question title when active", () => {
        gameMock.state.status = "ACTIVE";
        gameMock.state.playerUuid = "u1";
        gameMock.state.pin = "1234";
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
        timerMock.state.endEpochMs = Date.now() + 10000;
        render(<QuestionView />);
        expect(screen.getByText("Active Question Title")).toBeInTheDocument();
    });

    test("keeps the code draft on submit until SUBMISSION_RESULT acks it", () => {
        gameMock.state.status = "ACTIVE";
        gameMock.state.playerUuid = "u1";
        gameMock.state.pin = "1234";
        gameMock.state.currentQuestion = {
            id: "q1",
            type: "OJ_FULL",
            title: "Code question",
            description: "",
            timeLimitSec: 30,
            pointsBase: 100,
            languagesAllowed: null,
            config: { starter: "print(1)" },
        };
        timerMock.state.endEpochMs = Date.now() + 10000;
        localStorage.setItem("sprintjudge_code_q1", "print(2)");
        const view = render(<QuestionView />);
        act(() => {
            rendererMock.props?.onResponse({ source: "print(2)", language: "python" });
        });
        fireEvent.click(screen.getByRole("button", { name: /Compile and submit/i }));
        expect(gameMock.state.submit).toHaveBeenCalledWith(
            "q1",
            { source: "print(2)", language: "python" },
            "python",
        );
        expect(localStorage.getItem("sprintjudge_code_q1")).toBe("print(2)");

        gameMock.state.lastResult = {
            questionId: "q1",
            submission: { questionId: "q1", allPassed: true, score: 100 },
        };
        view.rerender(<QuestionView />);
        expect(localStorage.getItem("sprintjudge_code_q1")).toBeNull();
    });
});