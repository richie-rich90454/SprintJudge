import type { ReactNode } from "react";
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { act, render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ResultView } from "./ResultView";

const gameMock = vi.hoisted(() => ({
    state: {
        leaderboard: [] as any[],
        review: null as any,
        error: null as string | null,
        quizId: null as string | null,
        practicing: false,
    },
}));

const uiMock = vi.hoisted(() => ({
    state: { sound: "on", motion: "reduced", pin: null, theme: "light" },
}));

const navigateMock = vi.hoisted(() => vi.fn());
const buildBankQuizMock = vi.hoisted(() => vi.fn());
const originalCreateObjectURL = URL.createObjectURL;
const originalRevokeObjectURL = URL.revokeObjectURL;

vi.mock("../services/AdminApiService", () => ({
    adminApi: {
        buildBankQuiz: (...args: unknown[]) => buildBankQuizMock(...args),
        exportBank: vi.fn(),
    },
}));

vi.mock("../stores/useGameStore", () => ({
    useGameStore: Object.assign((selector: (s: any) => unknown) => selector(gameMock.state), {
        setState: (patch: any) => Object.assign(gameMock.state, patch),
    }),
}));

vi.mock("../stores/useUIStore", () => ({
    useUIStore: (selector: (s: any) => unknown) => selector(uiMock.state),
    motionReduced: () => true,
}));

vi.mock("@tanstack/react-router", () => ({
    useNavigate: () => navigateMock,
    Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

vi.mock("../motion/useStageMotion", () => ({
    useCountUp: () => ({ current: null }),
}));

describe("ResultView", () => {
    beforeEach(() => {
        gameMock.state.leaderboard = [];
        gameMock.state.review = null;
        gameMock.state.error = null;
        navigateMock.mockReset();
        if (!URL.createObjectURL) URL.createObjectURL = (() => "blob:mock") as any;
        if (!URL.revokeObjectURL) URL.revokeObjectURL = (() => {}) as any;
        vi.stubGlobal(
            "fetch",
            vi.fn().mockResolvedValue({
                ok: true,
                blob: async () => new Blob(["x"]),
                json: async () => ({}),
            }),
        );
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
        vi.useRealTimers();
        delete (navigator as { clipboard?: unknown }).clipboard;
        URL.createObjectURL = originalCreateObjectURL;
        URL.revokeObjectURL = originalRevokeObjectURL;
    });

    test("renders empty results and disabled action buttons", async () => {
        render(<ResultView />);
        expect(screen.getByText("Results")).toBeInTheDocument();
        expect(screen.getByText("No results yet.")).toBeInTheDocument();
        const exportBtn = await screen.findByRole("button", { name: /Export CSV/i });
        expect(exportBtn).toBeDisabled();
        expect(screen.getByRole("button", { name: /Practice next/i })).toBeDisabled();
    });

    test("hides admin-only actions from non-admin players", async () => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));
        render(<ResultView />);
        await waitFor(() =>
            expect(screen.queryByRole("button", { name: /Export CSV/i })).not.toBeInTheDocument(),
        );
        expect(screen.queryByRole("button", { name: /Practice next/i })).not.toBeInTheDocument();
    });

    test("switches tabs", async () => {
        const user = userEvent.setup();
        render(<ResultView />);
        await user.click(screen.getByRole("tab", { name: "Answer Key" }));
        await waitFor(() => expect(screen.getByText(/Loading review/)).toBeInTheDocument());
    });

    test("practice next builds quiz", async () => {
        gameMock.state.review = {
            sessionId: "s1",
            questions: [],
            players: [],
            classStats: null,
            teams: [],
            rankings: [],
        };
        vi.stubGlobal(
            "fetch",
            vi.fn().mockResolvedValue({
                ok: true,
                json: async () => ({ subject: "math", unit: "u", topic: "t", count: 5 }),
            }),
        );
        buildBankQuizMock.mockReset();
        buildBankQuizMock.mockResolvedValue({ quizId: "p1", questions: 5, skipped: 0 });
        render(<ResultView />);
        const practiceBtn = await screen.findByRole("button", { name: /Practice next/i });
        fireEvent.click(practiceBtn);
        await waitFor(() =>
            expect(
                screen.getByRole("link", { name: /Open it in the dashboard/i }),
            ).toBeInTheDocument(),
        );
    });

    test("export csv calls endpoint", async () => {
        gameMock.state.review = {
            sessionId: "s1",
            questions: [],
            players: [],
            classStats: null,
            teams: [],
            rankings: [],
        };
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            blob: async () => new Blob(["x"]),
        });
        vi.stubGlobal("fetch", fetchMock);
        render(<ResultView />);
        const exportBtn = await screen.findByRole("button", { name: /Export CSV/i });
        fireEvent.click(exportBtn);
        await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    });

    test("export keeps the anchor attached through click and revokes afterwards", async () => {
        gameMock.state.review = {
            sessionId: "s1",
            questions: [],
            players: [],
            classStats: null,
            teams: [],
            rankings: [],
        };
        const revoke = vi.fn();
        vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:csv");
        vi.spyOn(URL, "revokeObjectURL").mockImplementation(revoke);
        if ((window.URL as unknown) !== (URL as unknown)) {
            vi.spyOn(window.URL, "createObjectURL").mockReturnValue("blob:csv");
            vi.spyOn(window.URL, "revokeObjectURL").mockImplementation(revoke);
        }
        let attachedAtClick = false;
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
            this: HTMLAnchorElement,
        ) {
            attachedAtClick = document.body.contains(this);
        });
        render(<ResultView />);
        const exportBtn = await screen.findByRole("button", { name: /Export CSV/i });
        fireEvent.click(exportBtn);
        for (let i = 0; i < 5; i++) await Promise.resolve();
        expect(attachedAtClick).toBe(true);
        await waitFor(() => expect(revoke).toHaveBeenCalled());
    });

    const fullReview = () => ({
        sessionId: "s1",
        quizId: "quiz-1",
        questions: [
            {
                questionId: "q1",
                title: "Question one",
                questionType: "MCQ",
                pointsBase: 100,
                correctRate: 0.8,
                totalAttempts: 5,
                answer: "A",
                options: ["A", "B"],
            },
            {
                questionId: "q2",
                title: "Question two",
                questionType: "TRUE_FALSE",
                pointsBase: 50,
                correctRate: 0.2,
                totalAttempts: 3,
                answer: true,
            },
        ],
        players: [
            {
                playerUuid: "p1",
                playerName: "Ada",
                totalScore: 200,
                answers: [
                    { questionId: "q1", correct: true, scoreEarned: 100, attemptCount: 1 },
                    { questionId: "q2", correct: false, scoreEarned: 0, attemptCount: 2 },
                ],
            },
            {
                playerUuid: "p2",
                playerName: "Bob",
                totalScore: 50,
                answers: [{ questionId: "q1", correct: false, scoreEarned: 0, attemptCount: 3 }],
            },
        ],
        classStats: {
            totalPlayers: 2,
            totalQuestions: 2,
            totalAttempts: 8,
            totalCorrect: 4,
        },
        teams: [{ id: "t1", name: "Red", totalScore: 250, memberUuids: ["p1", "p2"] }],
        rankings: [],
    });

    test("renders the podium, the rest of the board and navigation actions", async () => {
        const user = userEvent.setup();
        gameMock.state.quizId = "quiz-1";
        gameMock.state.leaderboard = [
            { uuid: "p1", name: "Ada", score: 200, rank: 1 },
            { uuid: "p2", name: "Bob", score: 50, rank: 2 },
            { uuid: "p3", name: "Cy", score: 10, rank: 3 },
            { uuid: "p4", name: "Dee", score: 1, rank: 4 },
        ];
        gameMock.state.review = fullReview();
        render(<ResultView />);
        expect(screen.getByText("2 students, 2 questions")).toBeInTheDocument();
        expect(await screen.findByRole("button", { name: "New game" })).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Leave results" }));
        expect(navigateMock).toHaveBeenCalledWith({ to: "/" });
        fireEvent.click(screen.getByRole("button", { name: "New game" }));
        expect(navigateMock).toHaveBeenCalledWith({ to: "/admin" });

        await user.click(screen.getByRole("tab", { name: "Students" }));
        fireEvent.click(screen.getByRole("button", { name: "Reveal names" }));
        fireEvent.click(screen.getByRole("button", { name: /1\. Ada/ }));
        expect(screen.getByText(/q2/)).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Hide names" }));
    });

    test("expands answer keys and renders team standings", async () => {
        const user = userEvent.setup();
        gameMock.state.review = fullReview();
        render(<ResultView />);
        await user.click(screen.getByRole("tab", { name: "Answer Key" }));
        expect(screen.getByText("1. Question one")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: /Question one/ }));
        expect(screen.getByText(/Player 1/)).toBeInTheDocument();

        await user.click(screen.getByRole("tab", { name: "Teams" }));
        expect(screen.getByText("Team standings")).toBeInTheDocument();
        expect(screen.getByText("Red")).toBeInTheDocument();
        await user.click(screen.getByRole("tab", { name: "Analysis" }));
    });

    test("restores a cached review and ignores malformed cache entries", async () => {
        sessionStorage.setItem(
            "sprintjudge_last_review",
            JSON.stringify({
                review: fullReview(),
                leaderboard: [{ uuid: "p1", name: "Ada", score: 1, rank: 1 }],
            }),
        );
        const view = render(<ResultView />);
        await waitFor(() => expect(gameMock.state.review).toMatchObject({ sessionId: "s1" }));
        view.rerender(<ResultView />);
        expect(screen.getAllByText("Ada").length).toBeGreaterThan(0);
        sessionStorage.setItem("sprintjudge_last_review", "{bad json");
        gameMock.state.review = null;
        const user = userEvent.setup();
        view.rerender(<ResultView />);
        await user.click(screen.getByRole("tab", { name: "Answer Key" }));
        expect(screen.getByText(/Loading review/)).toBeInTheDocument();
        sessionStorage.clear();
    });

    test("shows timeout copy and socket errors in every review tab", async () => {
        const user = userEvent.setup();
        const realSetTimeout = window.setTimeout;
        const timeouts: Array<() => void> = [];
        vi.spyOn(window, "setTimeout").mockImplementation(((cb: () => void, ms?: number) => {
            if (ms === 10_000) {
                timeouts.push(cb);
                return 1;
            }
            return realSetTimeout(cb, ms);
        }) as typeof window.setTimeout);
        gameMock.state.error = "Socket down";
        render(<ResultView />);
        act(() => timeouts.forEach((cb) => cb()));

        for (const [tab, message] of [
            ["Answer Key", "Review unavailable. Reload to try again."],
            ["Students", "Review unavailable. Reload to try again."],
            ["Analysis", "Analysis unavailable. Reload to try again."],
        ] as const) {
            await user.click(screen.getByRole("tab", { name: tab }));
            expect(screen.getByText(message)).toBeInTheDocument();
            expect(
                screen.getAllByRole("alert").some((el) => el.textContent === "Socket down"),
            ).toBe(true);
        }
    });

    test("ignores the admin settings result and rejection after unmount", async () => {
        const errors = vi.spyOn(console, "error").mockImplementation(() => {});
        let resolve!: (value: unknown) => void;
        vi.stubGlobal(
            "fetch",
            vi.fn(
                () =>
                    new Promise((done) => {
                        resolve = done;
                    }),
            ),
        );
        const first = render(<ResultView />);
        first.unmount();
        await act(async () => {
            resolve({ ok: true });
            await Promise.resolve();
        });
        expect(first.container).toBeEmptyDOMElement();

        let reject!: (reason: unknown) => void;
        vi.stubGlobal(
            "fetch",
            vi.fn(
                () =>
                    new Promise((_, no) => {
                        reject = no;
                    }),
            ),
        );
        const second = render(<ResultView />);
        second.unmount();
        await act(async () => {
            reject(new Error("offline"));
            await Promise.resolve();
        });

        vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
        render(<ResultView />);
        await waitFor(() =>
            expect(screen.queryByRole("button", { name: /Export CSV/i })).not.toBeInTheDocument(),
        );
        expect(errors).not.toHaveBeenCalled();
        errors.mockRestore();
    });

    test("restores a cached review without a leaderboard and ignores disabled actions", async () => {
        sessionStorage.setItem(
            "sprintjudge_last_review",
            JSON.stringify({ review: { ...fullReview(), sessionId: undefined } }),
        );
        const fetchMock = vi
            .fn()
            .mockResolvedValue({ ok: true, blob: async () => new Blob(["x"]) });
        vi.stubGlobal("fetch", fetchMock);
        render(<ResultView />);
        await waitFor(() => expect(gameMock.state.review).toBeTruthy());
        expect(screen.getByText("No results yet.")).toBeInTheDocument();

        for (const name of [/Export CSV/i, /Practice next/i]) {
            const button = screen.getByRole("button", { name });
            const propsKey = Object.keys(button).find((key) => key.startsWith("__reactProps"));
            expect(propsKey).toBeDefined();
            (button as any)[propsKey!].onClick();
        }
        expect(fetchMock.mock.calls.some(([url]) => String(url).includes("/export/"))).toBe(false);
        expect(fetchMock.mock.calls.some(([url]) => String(url).includes("teach-next"))).toBe(
            false,
        );
        sessionStorage.clear();
    });

    test("uses the unit when a recommendation has no topic", async () => {
        gameMock.state.review = { ...fullReview(), sessionId: "s1" };
        vi.stubGlobal(
            "fetch",
            vi.fn().mockResolvedValue({
                ok: true,
                json: async () => ({ subject: "math", unit: "u", topic: "", count: 5 }),
            }),
        );
        buildBankQuizMock.mockReset();
        buildBankQuizMock.mockResolvedValue({ quizId: "p2", questions: 5, skipped: 0 });
        render(<ResultView />);
        fireEvent.click(await screen.findByRole("button", { name: /Practice next/i }));
        await waitFor(() =>
            expect(
                screen.getByRole("link", { name: /Open it in the dashboard/i }),
            ).toBeInTheDocument(),
        );
        expect(buildBankQuizMock).toHaveBeenCalledWith(
            expect.objectContaining({ title: "Practice: u" }),
        );
    });

    test("renders singular class labels and zero accuracy for malformed stats", async () => {
        gameMock.state.review = {
            ...fullReview(),
            classStats: {
                totalPlayers: 1,
                totalQuestions: 1,
                totalAttempts: 1,
                totalCorrect: undefined,
                avgScore: undefined,
            },
        };
        gameMock.state.leaderboard = [{ uuid: "p1", name: "Ada", score: 1, rank: 1 }];
        const writeText = vi.fn().mockResolvedValue(undefined);
        Object.defineProperty(navigator, "clipboard", { configurable: true, value: { writeText } });
        render(<ResultView />);
        expect(screen.getByText("1 student, 1 question")).toBeInTheDocument();
        await act(async () => {
            fireEvent.click(screen.getByRole("button", { name: "Copy summary" }));
            await Promise.resolve();
        });
        expect(writeText).toHaveBeenCalledWith(expect.stringContaining("0% correct"));
    });

    test("renders zero accuracy when nobody attempted anything", async () => {
        const user = userEvent.setup();
        gameMock.state.review = {
            ...fullReview(),
            classStats: {
                totalPlayers: 2,
                totalQuestions: 2,
                totalAttempts: 0,
                totalCorrect: 0,
                avgScore: 0,
            },
        };
        gameMock.state.leaderboard = [{ uuid: "p1", name: "Ada", score: 1, rank: 1 }];
        render(<ResultView />);
        await user.click(screen.getByRole("tab", { name: "Analysis" }));
        expect(screen.getByText("0%")).toBeInTheDocument();
    });

    test("expands and collapses questions, tolerates missing answers, and reveals names", async () => {
        const user = userEvent.setup();
        gameMock.state.review = {
            ...fullReview(),
            questions: [{ ...fullReview().questions[0], correctRate: 0.5 }],
            players: [fullReview().players[0], { ...fullReview().players[1], answers: [] }],
        };
        render(<ResultView />);
        await user.click(screen.getByRole("tab", { name: "Answer Key" }));
        const question = screen.getByRole("button", { name: /Question one/ });
        fireEvent.click(question);
        expect(question).toHaveAttribute("aria-expanded", "true");
        expect(screen.getByText("50%")).toBeInTheDocument();
        expect(screen.queryByText("Bob")).not.toBeInTheDocument();

        await user.click(screen.getByRole("tab", { name: "Students" }));
        const student = screen.getByRole("button", { name: /1\. Player 1/ });
        fireEvent.click(student);
        expect(student).toHaveAttribute("aria-expanded", "true");
        fireEvent.click(student);
        expect(student).toHaveAttribute("aria-expanded", "false");
        fireEvent.click(screen.getByRole("button", { name: "Reveal names" }));
        expect(screen.getByRole("button", { name: "Hide names" })).toBeInTheDocument();

        await user.click(screen.getByRole("tab", { name: "Answer Key" }));
        expect(screen.getByRole("button", { name: /Question one/ })).toHaveAttribute(
            "aria-expanded",
            "true",
        );
        expect(screen.getByText(/Ada/)).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: /Question one/ }));
        expect(screen.getByRole("button", { name: /Question one/ })).toHaveAttribute(
            "aria-expanded",
            "false",
        );

        await user.click(screen.getByRole("tab", { name: "Analysis" }));
        expect(screen.getAllByText("50%").length).toBeGreaterThan(0);
    });

    test("shows singular team membership and the empty analysis state", async () => {
        const user = userEvent.setup();
        gameMock.state.review = {
            ...fullReview(),
            teams: [{ id: "t1", name: "Solo", totalScore: 1, memberUuids: ["p1"] }],
            questions: [],
        };
        render(<ResultView />);
        await user.click(screen.getByRole("tab", { name: "Teams" }));
        expect(screen.getByText("1 member")).toBeInTheDocument();
        await user.click(screen.getByRole("tab", { name: "Analysis" }));
        expect(screen.getByText("No analysis to show yet.")).toBeInTheDocument();
    });

    test("reports export and recommendation failures", async () => {
        gameMock.state.review = fullReview();
        vi.stubGlobal(
            "fetch",
            vi.fn((url: string) =>
                Promise.resolve({
                    ok: !url.includes("/export/") && !url.includes("teach-next"),
                    json: async () => ({}),
                }),
            ),
        );
        render(<ResultView />);
        const exportBtn = await screen.findByRole("button", { name: /Export CSV/i });
        fireEvent.click(exportBtn);
        expect(await screen.findByText(/Could not export the room/)).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: /Practice next/i }));
        expect(await screen.findByText(/Could not build a practice quiz/)).toBeInTheDocument();

        vi.stubGlobal(
            "fetch",
            vi.fn().mockResolvedValue({
                ok: true,
                json: async () => ({ subject: "", unit: "", count: 0 }),
            }),
        );
        gameMock.state.practicing = false;
        fireEvent.click(screen.getByRole("button", { name: /Practice next/i }));
        await waitFor(() =>
            expect(screen.getAllByText(/Could not build a practice quiz/)).toHaveLength(1),
        );
    });
});
