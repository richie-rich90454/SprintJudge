import { beforeEach, describe, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { useEffect } from "react";
import { QuestionView } from "./QuestionView";
import { audio } from "../services/AudioEngine";

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
        joinTeam: vi.fn(),
        leaveTeam: vi.fn(),
        error: null,
        lastResult: null,
        teams: [],
        bracket: null,
        battleOpponent: null,
        battleResult: null,
        champion: null,
    } as Record<string, any>,
}));

const timerMock = vi.hoisted(() => ({
    state: { endEpochMs: null, totalSec: 30 } as Record<string, any>,
}));

const navMock = vi.hoisted(() => ({ to: null as string | null }));
const timerProps = vi.hoisted(() => ({ value: null as any }));
const rendererMock = vi.hoisted(() => ({
    props: null as null | { onResponse: (r: unknown) => void; revealSignal?: number },
    initialResponse: null as unknown,
}));

vi.mock("@tanstack/react-router", () => ({
    Link: ({ children, to }: { children: unknown; to: string }) => (
        <a href={to}>{children as any}</a>
    ),
    Navigate: ({ to }: { to: string }) => {
        navMock.to = to;
        return null;
    },
    useNavigate: () => vi.fn(),
}));

vi.mock("../components/Timer/CircularTimer", () => ({
    CircularTimer: (props: { endEpochMs: number; totalSec: number; onExpire?: () => void }) => {
        timerProps.value = props;
        return <button onClick={() => props.onExpire?.()}>Expire timer</button>;
    },
}));

vi.mock("../stores/useGameStore", () => ({
    useGameStore: (selector: (s: Record<string, unknown>) => unknown) => selector(gameMock.state),
}));

vi.mock("../stores/useTimerStore", () => ({
    useTimerStore: (selector: (s: Record<string, unknown>) => unknown) => selector(timerMock.state),
}));

vi.mock("../components/QuestionRendererHost", () => ({
    QuestionRendererHost: (props: { onResponse: (r: unknown) => void; revealSignal?: number }) => {
        rendererMock.props = props;
        // Real renderers emit on mount; child effects run before the parent's
        // question-reset effect, which used to wipe this fresh answer.
        useEffect(() => {
            if (rendererMock.initialResponse !== null)
                props.onResponse(rendererMock.initialResponse);
        }, []);
        return null;
    },
}));

vi.mock("../services/AudioEngine", () => ({ audio: { play: vi.fn() } }));

const question = (overrides: Record<string, unknown> = {}) => ({
    id: "q1",
    type: "MCQ",
    title: "Active Question Title",
    description: "",
    timeLimitSec: 30,
    pointsBase: 100,
    languagesAllowed: null,
    config: { options: ["A", "B"] },
    ...overrides,
});

const activeQuestion = (overrides: Record<string, unknown> = {}) => {
    gameMock.state.status = "ACTIVE";
    gameMock.state.playerUuid = "u1";
    gameMock.state.pin = "1234";
    gameMock.state.currentQuestion = question(overrides);
    timerMock.state.endEpochMs = Date.now() + 10000;
    timerMock.state.totalSec = 30;
};

describe("QuestionView", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        gameMock.state = {
            status: "LOBBY",
            currentQuestion: null,
            gameMode: "STANDARD",
            room: null,
            playerUuid: null,
            pin: null,
            resume: vi.fn(),
            submit: vi.fn(),
            joinTeam: vi.fn(),
            leaveTeam: vi.fn(),
            error: null,
            lastResult: null,
            teams: [],
            bracket: null,
            battleOpponent: null,
            battleResult: null,
            champion: null,
        };
        timerMock.state = { endEpochMs: null, totalSec: 30 };
        rendererMock.props = null;
        rendererMock.initialResponse = null;
        timerProps.value = null;
        navMock.to = null;
        localStorage.clear();
    });

    test("renders No game when no pin/session and Standby when joined", () => {
        render(<QuestionView />);
        expect(screen.getByText("No game")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "Join a game" })).toHaveAttribute("href", "/join");
    });

    test("standby variants for connecting, practice, error, and pin", () => {
        gameMock.state.playerUuid = "u1";
        const first = render(<QuestionView />);
        expect(screen.getByText("Standby")).toBeInTheDocument();
        expect(
            screen.getByText("Waiting for the host to start the next question."),
        ).toBeInTheDocument();
        first.unmount();

        gameMock.state.playerUuid = null;
        gameMock.state.pin = "1234";
        const connecting = render(<QuestionView />);
        expect(screen.getByText("Connecting to the game…")).toBeInTheDocument();
        connecting.unmount();

        gameMock.state.playerUuid = "u1";
        gameMock.state.gameMode = "PRACTICE";
        const practice = render(<QuestionView />);
        expect(screen.getByText("Preparing your first question…")).toBeInTheDocument();
        practice.unmount();

        gameMock.state.gameMode = "STANDARD";
        gameMock.state.room = { gameMode: "PRACTICE" };
        gameMock.state.error = "socket lost";
        render(<QuestionView />);
        expect(screen.getByRole("alert")).toHaveTextContent("socket lost");
        expect(screen.getByRole("link", { name: "Change PIN" })).toBeInTheDocument();
    });

    test("resumes once when arriving without a pin", () => {
        const view = render(<QuestionView />);
        expect(gameMock.state.resume).toHaveBeenCalledTimes(1);
        gameMock.state.pin = "1234";
        view.rerender(<QuestionView />);
        expect(gameMock.state.resume).toHaveBeenCalledTimes(1);
    });

    test("navigates to results when the game ends", () => {
        gameMock.state.status = "ENDED";
        render(<QuestionView />);
        expect(navMock.to).toBe("/results");
    });

    test("team lobby shows empty state, joining, leaving, and ws errors", () => {
        gameMock.state.gameMode = "TEAM";
        const empty = render(<QuestionView />);
        expect(screen.getByText("Team lobby")).toBeInTheDocument();
        expect(screen.getByText("The host has not created teams yet.")).toBeInTheDocument();
        empty.unmount();

        gameMock.state.playerUuid = "u1";
        gameMock.state.teams = [
            { id: "t1", name: "Team One", memberUuids: ["u2", "u3"], score: 0 },
            { id: "t2", name: "Mine", memberUuids: ["u1"], score: 5 },
        ];
        gameMock.state.error = "socket lost";
        render(<QuestionView />);
        expect(screen.getByText("2 teams")).toBeInTheDocument();
        expect(screen.getByText("2 members")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Join" })).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Join" }));
        expect(gameMock.state.joinTeam).toHaveBeenCalledWith("t1");
        fireEvent.click(screen.getByRole("button", { name: "Leave" }));
        expect(gameMock.state.leaveTeam).toHaveBeenCalled();
        expect(screen.getByRole("alert")).toHaveTextContent("socket lost");
        expect(
            screen.getByText("You are on Mine. Waiting for the host to start."),
        ).toBeInTheDocument();
    });

    test("team lobby uses singular member wording", () => {
        gameMock.state.gameMode = "TEAM";
        gameMock.state.teams = [{ id: "t1", name: "Solo", memberUuids: ["u1"], score: 0 }];
        render(<QuestionView />);
        expect(screen.getByText("1 team")).toBeInTheDocument();
        expect(screen.getByText("1 member")).toBeInTheDocument();
        expect(
            screen.getByText("Join a team before the host starts the game."),
        ).toBeInTheDocument();
    });

    test("battle lobby renders byes, pairings, opponent fallbacks, and the bracket", () => {
        gameMock.state.gameMode = "BATTLE";
        gameMock.state.playerUuid = "u1";
        gameMock.state.teams = [];
        gameMock.state.room = {
            players: [
                { uuid: "u1", name: "Ada" },
                { uuid: "u2", name: "Grace" },
            ],
        };
        gameMock.state.bracket = {
            rounds: [
                {
                    number: 2,
                    pairs: [
                        { p1Uuid: "u1", p2Uuid: "u2", winnerUuid: null, bye: false },
                        { p1Uuid: "u3", p2Uuid: null, winnerUuid: "u3", bye: true },
                    ],
                },
            ],
        };
        const first = render(<QuestionView />);
        expect(screen.getAllByText("Round 2").length).toBeGreaterThanOrEqual(1);
        expect(screen.getByText("You face Grace")).toBeInTheDocument();
        expect(screen.getByText("Ada vs Grace")).toBeInTheDocument();
        expect(screen.getByText("Player vs bye")).toBeInTheDocument();
        first.unmount();

        gameMock.state.battleOpponent = {
            type: "BATTLE_OPPONENT",
            round: 2,
            opponentUuid: "u2",
            opponentName: "Named rival",
            bye: false,
        };
        gameMock.state.bracket = {
            rounds: [
                {
                    number: 2,
                    pairs: [{ p1Uuid: "u1", p2Uuid: "u2", winnerUuid: null, bye: false }],
                },
            ],
        };
        const named = render(<QuestionView />);
        expect(screen.getByText("You face Named rival")).toBeInTheDocument();
        named.unmount();

        gameMock.state.bracket = null;
        const notStarted = render(<QuestionView />);
        expect(screen.getByText("Not started")).toBeInTheDocument();
        expect(screen.getByText("Waiting for pairings")).toBeInTheDocument();
        notStarted.unmount();

        gameMock.state.battleOpponent = {
            type: "BATTLE_OPPONENT",
            round: 2,
            opponentUuid: "u2",
            opponentName: "",
            bye: true,
        };
        gameMock.state.bracket = {
            rounds: [
                { number: 1, pairs: [{ p1Uuid: "u1", p2Uuid: null, winnerUuid: null, bye: true }] },
            ],
        };
        const bye = render(<QuestionView />);
        expect(screen.getByText("You have a bye")).toBeInTheDocument();
        bye.unmount();

        gameMock.state.playerUuid = "u9";
        gameMock.state.battleOpponent = null;
        const waiting = render(<QuestionView />);
        expect(screen.getByText("Waiting for pairings")).toBeInTheDocument();
        expect(screen.getByText("Ada vs bye")).toBeInTheDocument();
        waiting.unmount();

        gameMock.state.error = "socket lost";
        render(<QuestionView />);
        expect(screen.getByRole("alert")).toHaveTextContent("socket lost");
    });

    test("review renders question preview, explanation, standings, and feedback", () => {
        gameMock.state.status = "REVIEW";
        gameMock.state.gameMode = "TEAM";
        gameMock.state.playerUuid = "u1";
        gameMock.state.teams = [{ id: "t1", name: "Team One", memberUuids: ["u1"], score: 1234.5 }];
        gameMock.state.currentQuestion = question({ description: "Different description" });
        gameMock.state.lastResult = {
            type: "ROUND_RESULT",
            questionId: "q1",
            revealed: true,
            scores: [],
            correctAnswer: { explanation: "Because reasons" },
        };
        render(<QuestionView />);
        expect(screen.getByText("Round complete")).toBeInTheDocument();
        expect(screen.getByText("Active Question Title")).toBeInTheDocument();
        expect(screen.getByText("Team standings")).toBeInTheDocument();
        expect(screen.getByText("1,234.5")).toBeInTheDocument();
        expect(screen.getByText("Why")).toBeInTheDocument();
        expect(screen.getByText("Because reasons")).toBeInTheDocument();
        expect(
            screen.getByText("The host will bring the next question when the room is ready."),
        ).toBeInTheDocument();
    });

    test("review practice wording and preparation state", () => {
        gameMock.state.status = "REVIEW";
        gameMock.state.room = { gameMode: "PRACTICE" };
        const preparing = render(<QuestionView />);
        expect(screen.getByText("The host is preparing the next round.")).toBeInTheDocument();
        preparing.unmount();

        gameMock.state.currentQuestion = question();
        render(<QuestionView />);
        expect(screen.getByText("Next question coming up.")).toBeInTheDocument();
    });

    test("review battle results, champion, and bye wording", () => {
        gameMock.state.status = "REVIEW";
        gameMock.state.gameMode = "BATTLE";
        gameMock.state.playerUuid = "u1";
        gameMock.state.currentQuestion = question();
        gameMock.state.room = { players: [{ uuid: "u2", name: "Grace" }] };
        gameMock.state.battleResult = {
            type: "BATTLE_RESULT",
            round: 1,
            matches: [{ p1Uuid: "u1", p2Uuid: "u2", winnerUuid: "u1", bye: false }],
            winners: ["u1"],
        };
        gameMock.state.battleOpponent = {
            type: "BATTLE_OPPONENT",
            round: 1,
            opponentUuid: "u2",
            opponentName: "",
            bye: false,
        };
        gameMock.state.champion = {
            type: "BATTLE_CHAMPION",
            championUuid: "u1",
            championName: "Ada",
        };
        const won = render(<QuestionView />);
        expect(screen.getByText("You beat your opponent.")).toBeInTheDocument();
        expect(screen.getByText("Champion: Ada")).toBeInTheDocument();
        won.unmount();

        gameMock.state.battleResult.matches[0].winnerUuid = "u2";
        const lost = render(<QuestionView />);
        expect(screen.getByText("You lost this round.")).toBeInTheDocument();
        lost.unmount();

        gameMock.state.battleResult.matches[0] = {
            p1Uuid: "u1",
            p2Uuid: null,
            winnerUuid: null,
            bye: true,
        };
        render(<QuestionView />);
        expect(screen.getByText("Bye round. You advance.")).toBeInTheDocument();
    });

    test("review handles a missing local player and opponent fallbacks", () => {
        gameMock.state.status = "REVIEW";
        gameMock.state.gameMode = "BATTLE";
        gameMock.state.playerUuid = null;
        gameMock.state.currentQuestion = question();
        gameMock.state.battleResult = {
            type: "BATTLE_RESULT",
            round: 1,
            matches: [{ p1Uuid: "u1", p2Uuid: "u2", winnerUuid: "u1", bye: false }],
            winners: ["u1"],
        };
        render(<QuestionView />);
        expect(screen.queryByText(/You beat/)).not.toBeInTheDocument();
    });

    test("battle lobby falls back to generic opponent and player names", () => {
        gameMock.state.gameMode = "BATTLE";
        gameMock.state.playerUuid = "u1";
        gameMock.state.room = { players: [{ uuid: "u1", name: "Ada" }] };
        gameMock.state.bracket = {
            rounds: [
                {
                    number: 1,
                    pairs: [
                        { p1Uuid: "u1", p2Uuid: "u2", winnerUuid: null, bye: false },
                        { p1Uuid: "u3", p2Uuid: "u4", winnerUuid: null, bye: false },
                    ],
                },
            ],
        };
        render(<QuestionView />);
        expect(screen.getByText("You face your opponent")).toBeInTheDocument();
        expect(screen.getByText("Player vs Player")).toBeInTheDocument();
    });

    test("renders the active question with timer, chips, description, and response gating", () => {
        activeQuestion();
        const view = render(<QuestionView />);
        expect(screen.getByRole("heading", { name: "Active Question Title" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Expire timer" })).toBeInTheDocument();
        expect(timerProps.value.totalSec).toBe(30);
        expect(rendererMock.props?.revealSignal).toBe(0);
        const submit = screen.getByRole("button", { name: "Lock in answer" });
        expect(submit).toBeDisabled();
        act(() => rendererMock.props?.onResponse({ option: 0 }));
        expect(screen.getByRole("button", { name: "Lock in answer" })).toBeEnabled();
        fireEvent.click(submit);
        expect(gameMock.state.submit).toHaveBeenCalledWith("q1", { option: 0 }, undefined);
        expect(screen.getByRole("button", { name: "Answer locked in" })).toBeDisabled();
        fireEvent.click(submit);
        expect(gameMock.state.submit).toHaveBeenCalledTimes(1);
        expect(audio.play).toHaveBeenCalledWith("click");
        view.unmount();

        gameMock.state.gameMode = "BATTLE";
        gameMock.state.bracket = {
            rounds: [
                { number: 3, pairs: [{ p1Uuid: "u1", p2Uuid: null, winnerUuid: null, bye: true }] },
            ],
        };
        const bye = render(<QuestionView />);
        expect(screen.getByText("Bye round")).toBeInTheDocument();
        bye.unmount();

        gameMock.state.bracket.rounds[0].pairs[0] = {
            p1Uuid: "u1",
            p2Uuid: "u2",
            winnerUuid: null,
            bye: false,
        };
        gameMock.state.room = { players: [{ uuid: "u2", name: "Grace" }] };
        const paired = render(<QuestionView />);
        expect(screen.getByText("Round 3: vs Grace")).toBeInTheDocument();
        paired.unmount();

        gameMock.state.gameMode = "TEAM";
        gameMock.state.teams = [{ id: "t1", name: "Team One", memberUuids: ["u1"], score: 0 }];
        render(<QuestionView />);
        expect(screen.getByText("Team One")).toBeInTheDocument();
    });

    test("untimed questions and coding questions render their own controls", () => {
        activeQuestion({
            type: "OJ_FULL",
            description: "Code it",
            config: { starter: "print(1)" },
        });
        timerMock.state.endEpochMs = Infinity;
        render(<QuestionView />);
        expect(screen.getByText("Untimed")).toBeInTheDocument();
        expect(screen.getByText("Code it")).toBeInTheDocument();
        expect(screen.getByText(/Write code that passes the hidden tests/)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Compile and submit" })).toBeEnabled();
    });

    test("a renderer's mount-time answer survives the question reset", () => {
        activeQuestion({ type: "OJ_FULL", config: { starter: "print(1)" } });
        rendererMock.initialResponse = { source: "print(1)", language: "python" };
        render(<QuestionView />);
        fireEvent.click(screen.getByRole("button", { name: "Compile and submit" }));
        expect(gameMock.state.submit).toHaveBeenCalledWith(
            "q1",
            { source: "print(1)", language: "python" },
            "python",
        );
    });

    test("timer expiry submits a coding answer with its language, once only", () => {
        activeQuestion({ type: "OJ_PATCH" });
        render(<QuestionView />);
        act(() => rendererMock.props?.onResponse({ source: "x", language: "python" }));
        fireEvent.click(screen.getByRole("button", { name: "Expire timer" }));
        expect(gameMock.state.submit).toHaveBeenCalledWith(
            "q1",
            { source: "x", language: "python" },
            "python",
        );
        fireEvent.click(screen.getByRole("button", { name: "Expire timer" }));
        expect(gameMock.state.submit).toHaveBeenCalledTimes(1);
    });

    test("shake timer clears the shake after 400ms and on a repeated failure", () => {
        vi.useFakeTimers();
        try {
            activeQuestion();
            const view = render(<QuestionView />);
            gameMock.state.lastResult = {
                questionId: "q1",
                submission: { questionId: "q1", allPassed: false, score: 0 },
            };
            view.rerender(<QuestionView />);
            expect(document.querySelector(".question-shake")).not.toBeNull();
            act(() => vi.advanceTimersByTime(400));
            expect(document.querySelector(".question-shake")).toBeNull();

            gameMock.state.lastResult = {
                questionId: "q1",
                submission: { questionId: "q1", allPassed: false, score: 0, totalTests: 1 },
            };
            view.rerender(<QuestionView />);
            expect(document.querySelector(".question-shake")).not.toBeNull();
        } finally {
            vi.useRealTimers();
        }
    });

    test("coding feedback tolerates missing test totals", () => {
        activeQuestion({ type: "OJ_FULL" });
        const view = render(<QuestionView />);
        gameMock.state.lastResult = {
            questionId: "q1",
            submission: { questionId: "q1", allPassed: true, score: 10 },
        };
        view.rerender(<QuestionView />);
        expect(screen.getByText("0/0 tests passed, +10")).toBeInTheDocument();

        gameMock.state.lastResult = {
            questionId: "q1",
            submission: { questionId: "q1", allPassed: true },
        };
        view.rerender(<QuestionView />);
        expect(screen.getByText("0/0 tests passed, +0")).toBeInTheDocument();
    });

    test("a second fast failure clears the pending shake timer", () => {
        activeQuestion();
        const view = render(<QuestionView />);
        gameMock.state.lastResult = {
            questionId: "q1",
            submission: { questionId: "q1", allPassed: false, score: 0 },
        };
        view.rerender(<QuestionView />);
        gameMock.state.lastResult = {
            questionId: "q1",
            submission: { questionId: "q1", allPassed: false, score: 0, passed: 0 },
        };
        expect(() => view.rerender(<QuestionView />)).not.toThrow();
        expect(document.querySelector(".question-shake")).not.toBeNull();
    });

    test("review feedback, unmatched battle matches, and non-finite team scores", () => {
        gameMock.state.status = "REVIEW";
        gameMock.state.gameMode = "TEAM";
        gameMock.state.playerUuid = "u9";
        gameMock.state.currentQuestion = question();
        gameMock.state.teams = [{ id: "t1", name: "Broken", memberUuids: [], score: NaN }];
        gameMock.state.battleResult = {
            type: "BATTLE_RESULT",
            round: 1,
            matches: [{ p1Uuid: "u1", p2Uuid: "u2", winnerUuid: "u1", bye: false }],
            winners: ["u1"],
        };
        gameMock.state.lastResult = {
            questionId: "q1",
            submission: { questionId: "q1", allPassed: false, score: 0 },
        };
        const failed = render(<QuestionView />);
        expect(screen.getByText("Not quite")).toBeInTheDocument();
        expect(screen.getByText("0")).toBeInTheDocument();
        failed.unmount();

        gameMock.state.gameMode = "STANDARD";
        gameMock.state.lastResult = {
            questionId: "q1",
            submission: { questionId: "q1", allPassed: true, score: 25 },
        };
        render(<QuestionView />);
        expect(screen.getByText("+25 pts")).toBeInTheDocument();
    });

    test("battle chip handles a p2 player and a missing opponent name", () => {
        gameMock.state.status = "LOBBY";
        gameMock.state.gameMode = "BATTLE";
        gameMock.state.playerUuid = "u1";
        gameMock.state.room = { players: [{ uuid: "u2", name: "Grace" }] };
        gameMock.state.bracket = {
            rounds: [
                {
                    number: 1,
                    pairs: [{ p1Uuid: "u2", p2Uuid: "u1", winnerUuid: null, bye: false }],
                },
            ],
        };
        const lobby = render(<QuestionView />);
        expect(screen.getByText("You face Grace")).toBeInTheDocument();
        lobby.unmount();

        activeQuestion();
        gameMock.state.gameMode = "BATTLE";
        gameMock.state.playerUuid = "u1";
        gameMock.state.room = null;
        render(<QuestionView />);
        expect(screen.getByText("Round 1: vs opponent")).toBeInTheDocument();
    });

    test("keeps a code draft until a submission ack clears it", () => {
        activeQuestion({ type: "OJ_FULL", config: { starter: "print(1)" } });
        localStorage.setItem("sprintjudge_code_q1", "print(2)");
        const view = render(<QuestionView />);
        act(() => rendererMock.props?.onResponse({ source: "print(2)", language: "python" }));
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

    test("successful and failed non-coding feedback play audio and style the banner", () => {
        activeQuestion();
        act(() => rendererMock.props?.onResponse({ option: 1 }));
        const view = render(<QuestionView />);
        gameMock.state.lastResult = {
            questionId: "q1",
            submission: { questionId: "q1", allPassed: true, score: 50, aiFeedback: "nice" },
        };
        view.rerender(<QuestionView />);
        expect(screen.getByText("+50 pts")).toBeInTheDocument();
        expect(audio.play).toHaveBeenCalledWith("correct");
        expect(document.querySelector(".motes")).toBeInTheDocument();
        view.unmount();

        const failed = render(<QuestionView />);
        gameMock.state.lastResult = {
            questionId: "q1",
            submission: { questionId: "q1", allPassed: false, score: 0 },
        };
        failed.rerender(<QuestionView />);
        expect(screen.getByText("Not quite")).toBeInTheDocument();
        expect(audio.play).toHaveBeenCalledWith("wrong");
        expect(document.querySelector(".question-shake")).not.toBeNull();
    });

    test("coding feedback includes test counts and clears shake on a new question", () => {
        activeQuestion({ type: "OJ_FULL" });
        const view = render(<QuestionView />);
        gameMock.state.lastResult = {
            questionId: "q1",
            submission: {
                questionId: "q1",
                allPassed: false,
                passed: 2,
                totalTests: 3,
                score: 20,
                aiFeedback: "off by one",
            },
        };
        view.rerender(<QuestionView />);
        expect(screen.getByText("2/3 tests passed, +20 - off by one")).toBeInTheDocument();
        expect(audio.play).toHaveBeenCalledWith("wrong");

        gameMock.state.currentQuestion = question({ id: "q2", type: "MCQ" });
        view.rerender(<QuestionView />);
        expect(screen.queryByText("2/3 tests passed, +20 - off by one")).not.toBeInTheDocument();
    });

    test("local storage failures and unrelated results are ignored", () => {
        activeQuestion();
        const remove = vi.spyOn(Storage.prototype, "removeItem").mockImplementation(() => {
            throw new Error("denied");
        });
        const view = render(<QuestionView />);
        gameMock.state.lastResult = {
            questionId: "other",
            submission: { questionId: "other", allPassed: true, score: 1 },
        };
        view.rerender(<QuestionView />);
        expect(remove).not.toHaveBeenCalled();
        gameMock.state.lastResult = {
            questionId: "q1",
            submission: { questionId: "q1", allPassed: true, score: 1 },
        };
        expect(() => view.rerender(<QuestionView />)).not.toThrow();
        expect(remove).toHaveBeenCalledWith("sprintjudge_code_q1");
        remove.mockRestore();
    });
});
