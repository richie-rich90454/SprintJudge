import type { ReactNode } from "react";
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ResultView } from "./ResultView";

const gameMock = vi.hoisted(() => ({
    state: {
        leaderboard: [] as any[],
        review: null as any,
        error: null as string | null,
    },
}));

const uiMock = vi.hoisted(() => ({
    state: { sound: "on", motion: "reduced", pin: null, theme: "light" },
}));

const navigateMock = vi.hoisted(() => vi.fn());
const buildBankQuizMock = vi.hoisted(() => vi.fn());

vi.mock("../services/AdminApiService", () => ({
    adminApi: {
        buildBankQuiz: (...args: unknown[]) => buildBankQuizMock(...args),
        exportBank: vi.fn(),
    },
}));

vi.mock("../stores/useGameStore", () => ({
    useGameStore: (selector: (s: any) => unknown) => selector(gameMock.state),
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
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ subject: "math", unit: "u", topic: "t", count: 5 }),
        }));
        buildBankQuizMock.mockReset();
        buildBankQuizMock.mockResolvedValue({ quizId: "p1", questions: 5, skipped: 0 });
        render(<ResultView />);
        const practiceBtn = await screen.findByRole("button", { name: /Practice next/i });
        fireEvent.click(practiceBtn);
        await waitFor(() =>
            expect(screen.getByRole("link", { name: /Open it in the dashboard/i })).toBeInTheDocument(),
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
});