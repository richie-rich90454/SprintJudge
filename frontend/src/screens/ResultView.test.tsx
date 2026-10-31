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

vi.mock("../stores/useGameStore", () => ({
    useGameStore: (selector: (s: any) => unknown) => selector(gameMock.state),
}));

vi.mock("../stores/useUIStore", () => ({
    useUIStore: (selector: (s: any) => unknown) => selector(uiMock.state),
    motionReduced: () => true,
}));

vi.mock("@tanstack/react-router", () => ({
    useNavigate: () => navigateMock,
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

    test("renders empty results and disabled action buttons", () => {
        render(<ResultView />);
        expect(screen.getByText("Results")).toBeInTheDocument();
        expect(screen.getByText("No results yet.")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /Export CSV/i })).toBeDisabled();
        expect(screen.getByRole("button", { name: /Practice next/i })).toBeDisabled();
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
        const fetchMock = vi
            .fn()
            .mockResolvedValueOnce({
                ok: true,
                json: async () => ({ subject: "math", unit: "u", topic: "t", count: 5 }),
            })
            .mockResolvedValueOnce({
                ok: true,
                json: async () => ({ quizId: "p1" }),
            });
        vi.stubGlobal("fetch", fetchMock);
        render(<ResultView />);
        fireEvent.click(screen.getByRole("button", { name: /Practice next/i }));
        await waitFor(() =>
            expect(screen.getByText("Practice quiz ready: p1")).toBeInTheDocument(),
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
        fireEvent.click(screen.getByRole("button", { name: /Export CSV/i }));
        await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    });
});