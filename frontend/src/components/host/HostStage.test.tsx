import { describe, test, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import type { QuestionDto, RoundResult, RoomState } from "../../types";
import type { ReactNode } from "react";
import { I18nProvider, useI18n } from "../../i18n";
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
    useGameStore: (selector: (s: Record<string, unknown>) => unknown) => selector(gameMock.state),
}));

vi.mock("../../stores/useTimerStore", () => ({
    useTimerStore: (selector: (s: Record<string, unknown>) => unknown) => selector(timerMock.state),
}));

function LocaleHarness({ children }: { children: ReactNode }) {
    const { setLocale } = useI18n();
    return (
        <>
            <button type="button" onClick={() => setLocale("ar")}>
                Switch to Arabic
            </button>
            {children}
        </>
    );
}

function renderInProvider(ui: ReactNode) {
    return render(
        <I18nProvider>
            <LocaleHarness>{ui}</LocaleHarness>
        </I18nProvider>,
    );
}

describe("HostStage", () => {
    beforeEach(() => {
        gameMock.state.status = "LOBBY";
        gameMock.state.currentQuestion = null;
        gameMock.state.room = null;
        gameMock.state.lastResult = null;
        localStorage.clear();
    });

    test("renders lobby with PIN and join URL", () => {
        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.getByText(/Join the room\./)).toBeInTheDocument();
        expect(screen.getByText("1234")).toBeInTheDocument();
        expect(screen.getByText("http://localhost/join")).toBeInTheDocument();
    });

    test("QR label tolerates a missing pin", () => {
        renderInProvider(<HostStage pin={undefined as never} joinUrl="http://localhost/join" />);
        expect(screen.getByRole("img", { name: /Join link QR code for game/ })).toBeInTheDocument();
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
        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.getByText("Active Question Title")).toBeInTheDocument();
    });

    test("switches lobby copy and direction to Arabic", () => {
        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.getByText(/Join the room\./)).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Switch to Arabic" }));

        expect(screen.getByText("انضم إلى الغرفة.")).toBeInTheDocument();
        expect(screen.getByText("غرفة الانتظار مفتوحة")).toBeInTheDocument();
        expect(screen.getByText("PIN اللعبة")).toBeInTheDocument();
        expect(screen.getByText(/في الغرفة/)).toBeInTheDocument();
        expect(screen.getByRole("img", { name: /اللعبة 1234/ })).toBeInTheDocument();
        expect(document.documentElement.dir).toBe("rtl");
    });
});

function makeQuestion(config: Record<string, unknown>): QuestionDto {
    return {
        id: "q1",
        type: "MCQ",
        title: "Question Title",
        description: "",
        timeLimitSec: 30,
        pointsBase: 100,
        languagesAllowed: null,
        config,
    };
}

function makeScore(correct: boolean) {
    return {
        uuid: `u${correct}`,
        name: "Ada",
        score: correct ? 100 : 0,
        correct,
        roundScore: correct ? 100 : 0,
        streak: 0,
        bonus: 0,
    };
}

function makeRound(
    correctAnswer: Record<string, unknown>,
    scores: ReturnType<typeof makeScore>[],
    answerCounts?: Record<string, number>,
): RoundResult {
    return {
        type: "ROUND_RESULT",
        questionId: "q1",
        revealed: true,
        correctAnswer,
        scores,
        ...(answerCounts ? { answerCounts } : {}),
    };
}

function makeRoom(
    players: RoomState["players"],
    questionCount = 0,
    currentQuestionIndex = 0,
): RoomState {
    return {
        type: "ROOM_STATE",
        status: "ACTIVE",
        questionCount,
        currentQuestionIndex,
        currentQuestionId: "q1",
        players,
        gameMode: "STANDARD",
    };
}

describe("HostStage active/review states", () => {
    beforeEach(() => {
        gameMock.state.status = "ACTIVE";
        gameMock.state.currentQuestion = makeQuestion({ options: ["Alpha", "Beta"] });
        gameMock.state.room = null;
        gameMock.state.lastResult = null;
        timerMock.state.endEpochMs = null;
        timerMock.state.totalSec = 30;
        localStorage.clear();
    });

    test("shows progress, singular student count and a live timer", () => {
        gameMock.state.room = makeRoom(
            [
                { uuid: "h", name: "Host", score: 0 },
                { uuid: "a", name: "Ada", score: 0 },
            ],
            3,
            0,
        );
        timerMock.state.endEpochMs = Date.now() + 30_000;
        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.getByText("Question 1 of 3")).toBeInTheDocument();
        expect(screen.getByText("1 student in the room")).toBeInTheDocument();
        expect(screen.getByRole("timer")).toBeInTheDocument();
    });

    test("omits the timer for a missing or non-finite end", () => {
        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.queryByRole("timer")).not.toBeInTheDocument();

        timerMock.state.endEpochMs = Number.NaN;
        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.queryAllByRole("timer")).toHaveLength(0);
    });

    test("plural student count when the room has several students", () => {
        gameMock.state.room = makeRoom([
            { uuid: "a", name: "Ada", score: 0 },
            { uuid: "b", name: "Ben", score: 0 },
        ]);
        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.getByText("2 students in the room")).toBeInTheDocument();
    });

    test("review renders answer plaque, option bars, misconception from reveal and explanation", () => {
        gameMock.state.status = "REVIEW";
        gameMock.state.currentQuestion = makeQuestion({
            options: ["Alpha", "Beta"],
            optionMeta: [{ id: "A", misconception: "old idea" }],
            explanation: "config explanation",
        });
        gameMock.state.lastResult = makeRound(
            {
                correctIndex: 0,
                optionMeta: [
                    { id: "B", misconception: "Worst idea" },
                    { id: "A", misconception: "old idea" },
                ],
                explanation: "reveal explanation",
            },
            [makeScore(true), makeScore(false), makeScore(false)],
            { A: 1, B: 2 },
        );

        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.getByText("Answer reveal")).toBeInTheDocument();
        expect(screen.getByText("1 of 3 correct")).toBeInTheDocument();
        expect(screen.getByText("33%")).toBeInTheDocument();
        expect(screen.getByText("Correct: A, Alpha")).toBeInTheDocument();
        expect(screen.getByText("Distribution")).toBeInTheDocument();
        expect(screen.getByText("Worst idea")).toBeInTheDocument();
        expect(screen.getByText("Why")).toBeInTheDocument();
        expect(screen.getByText("reveal explanation")).toBeInTheDocument();
        expect(screen.queryByRole("timer")).not.toBeInTheDocument();

        const bars = document.querySelectorAll(".reveal-bar");
        expect(bars).toHaveLength(2);
        expect((bars[0] as HTMLElement).style.width).toBe("33%");
        expect((bars[1] as HTMLElement).style.width).toBe("67%");
    });

    test("review falls back to score bars and config metadata when reveal has none", () => {
        gameMock.state.status = "REVIEW";
        gameMock.state.currentQuestion = makeQuestion({
            options: ["Alpha", "Beta"],
            optionMeta: [{ id: "A", misconception: "config misconception" }],
            explanation: "config explanation",
        });
        gameMock.state.lastResult = makeRound({ correctIndex: 0 }, [
            makeScore(true),
            makeScore(true),
            makeScore(false),
        ]);

        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.getAllByText(/^Correct$/).length).toBeGreaterThan(0);
        expect(screen.getByText("Incorrect")).toBeInTheDocument();
        expect(screen.getByText("config explanation")).toBeInTheDocument();
        expect(screen.getByText("67%")).toBeInTheDocument();

        const bars = document.querySelectorAll(".reveal-bar");
        expect(bars).toHaveLength(2);
        expect((bars[0] as HTMLElement).style.width).toBe("67%");
        expect((bars[1] as HTMLElement).style.width).toBe("33%");
    });

    test("review uses config option metadata when the reveal has none", () => {
        gameMock.state.status = "REVIEW";
        gameMock.state.currentQuestion = makeQuestion({
            options: ["Alpha", "Beta"],
            optionMeta: [{ id: "A", misconception: "config misconception" }],
            explanation: "config explanation",
        });
        gameMock.state.lastResult = makeRound(
            { correctIndex: 0 },
            [makeScore(true), makeScore(false)],
            { A: 2, B: 1 },
        );

        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.getByText("config misconception")).toBeInTheDocument();
        expect(screen.getByText("config explanation")).toBeInTheDocument();
        expect(document.querySelectorAll(".reveal-bar")).toHaveLength(2);
    });

    test("review uses sorted answer-count keys when the question has no options", () => {
        gameMock.state.status = "REVIEW";
        gameMock.state.currentQuestion = makeQuestion({});
        gameMock.state.lastResult = makeRound({}, [makeScore(true)], { Z: 0, A: 0 });
        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        const bars = document.querySelectorAll(".reveal-bar");
        expect(bars).toHaveLength(2);
        expect((bars[0] as HTMLElement).style.width).toBe("0%");
        expect(screen.queryByText("Why")).not.toBeInTheDocument();
        expect(screen.queryByText("Watch this misconception")).not.toBeInTheDocument();
    });

    test("review pads missing option counts and ignores incomplete metadata", () => {
        gameMock.state.status = "REVIEW";
        gameMock.state.currentQuestion = makeQuestion({
            options: ["Alpha", "Beta", "Gamma"],
            optionMeta: [{ id: "", misconception: "skip" }, { id: "A" }],
        });
        gameMock.state.lastResult = makeRound({}, [makeScore(true)], { A: 1 });
        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        const bars = document.querySelectorAll(".reveal-bar");
        expect(bars).toHaveLength(3);
        expect((bars[1] as HTMLElement).style.width).toBe("0%");
        expect(screen.queryByText("skip")).not.toBeInTheDocument();
        expect(screen.queryByText("Watch this misconception")).not.toBeInTheDocument();
    });

    test("review with no scores avoids division by zero", () => {
        gameMock.state.status = "REVIEW";
        gameMock.state.lastResult = makeRound({}, []);
        renderInProvider(<HostStage pin="1234" joinUrl="http://localhost/join" />);
        expect(screen.getByText("0%")).toBeInTheDocument();
        expect(screen.queryByText(/Correct:/)).not.toBeInTheDocument();
    });

    test("ended projector stage shows final standings at projector scale", () => {
        gameMock.state.status = "ENDED";
        gameMock.state.currentQuestion = null;
        const { container } = renderInProvider(
            <HostStage pin="1234" joinUrl="http://localhost/join" projector />,
        );
        expect(screen.getByText("Final standings")).toBeInTheDocument();
        expect(screen.getByText("That is the round.")).toBeInTheDocument();
        expect(container.querySelector("section")).toHaveClass("min-h-[62vh]");
    });
});
