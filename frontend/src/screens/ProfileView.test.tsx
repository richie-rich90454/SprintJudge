import type { ReactNode } from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ProfileView } from "./ProfileView";
import {
    createProfile,
    fetchProfile,
    forgetIdentity,
    storeIdentity,
    updateProfile,
} from "../services/ProfileApiService";

const mock = vi.hoisted(() => ({
    identity: null as any,
    query: {} as any,
    queryOptions: {} as any,
    mutationOptions: [] as any[],
    mutationCalls: 0,
    mutationStates: {} as Record<number, any>,
    mutationResults: {} as Record<number, any>,
    game: {
        playerName: null as string | null,
        playerUuid: null as string | null,
        rejoinToken: null as string | null,
    },
    queryClient: { setQueryData: vi.fn() },
}));

vi.mock("@tanstack/react-query", () => ({
    useQueryClient: () => mock.queryClient,
    useQuery: (options: any) => {
        mock.queryOptions = options;
        return mock.query;
    },
    useMutation: (options: any) => {
        const index = mock.mutationCalls++;
        mock.mutationOptions[index] = options;
        const slot = index % 3;
        const state = mock.mutationStates[slot] ?? {};
        return {
            mutate: (arg: unknown) => {
                const result = mock.mutationResults[slot];
                if (result !== undefined) mock.mutationStates[slot] = { ...state, data: result };
                options.onSuccess?.(result, arg);
            },
            isPending: state.isPending ?? false,
            isError: state.isError ?? false,
            error: state.error ?? null,
            data: state.data ?? undefined,
        };
    },
}));

vi.mock("../components/Shell", () => ({
    Shell: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("../stores/useGameStore", () => ({
    useGameStore: (selector: (s: any) => unknown) => selector(mock.game),
}));

vi.mock("../services/ProfileApiService", () => ({
    createProfile: vi.fn(),
    fetchProfile: vi.fn(),
    forgetIdentity: vi.fn(),
    storedIdentity: () => mock.identity,
    storeIdentity: vi.fn(),
    updateProfile: vi.fn(),
}));

const identity = { id: "p1", token: "tok" };
const report = {
    profile: { id: "p1", nickname: "Ada", linkToken: "tok", playerUuid: null },
    stats: { sessions: 1, totalScore: 10, correct: 1, answers: 2 },
    achievements: [
        { id: "a1", title: "First Quiz", description: "Played one", unlocked: true },
        { id: "a2", title: "Sharpshooter", description: "Perfect run", unlocked: false },
    ],
    history: [
        {
            sessionId: "s1",
            quizTitle: "Quiz A",
            playedAt: "2024-01-01T00:00:00Z",
            correct: 1,
            answers: 2,
            score: 10,
        },
    ],
    portfolio: [
        {
            quizId: "q1",
            quizTitle: "Quiz A",
            sessions: 1,
            bestScore: 10,
            lastPlayedAt: "2024-01-01T00:00:00Z",
        },
    ],
};
const mutation = (slot: number) => mock.mutationOptions[mock.mutationCalls - 3 + slot];

describe("ProfileView", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mock.identity = null;
        mock.mutationCalls = 0;
        mock.mutationOptions = [];
        mock.mutationStates = {};
        mock.mutationResults = {};
        mock.game = { playerName: null, playerUuid: null, rejoinToken: null };
        mock.query = { data: undefined, isPending: false, isError: false, refetch: vi.fn() };
    });

    test("shows a real Error message on create failure", () => {
        mock.mutationStates[0] = {
            isError: true,
            error: new Error("nickname taken"),
        };
        render(<ProfileView />);
        expect(screen.getByRole("alert")).toHaveTextContent(
            "Could not create a profile: nickname taken",
        );
    });

    test("does not render 'undefined' for non-Error rejections", () => {
        mock.mutationStates[0] = { isError: true, error: { code: 400 } };
        render(<ProfileView />);
        expect(screen.getByRole("alert")).toHaveTextContent(
            "Could not create a profile: unknown error",
        );
        expect(screen.queryByText(/undefined/)).not.toBeInTheDocument();
    });

    test("create form uses the game identity when available and remembers the report", () => {
        mock.game = { playerName: "Ada", playerUuid: "uuid-1", rejoinToken: "rejoin-1" };
        render(<ProfileView />);
        const input = screen.getByLabelText("Nickname");
        expect(input).toHaveValue("Ada");
        const createButton = screen.getByRole("button", { name: "Create profile" });
        fireEvent.change(input, { target: { value: "" } });
        expect(createButton).toBeDisabled();
        fireEvent.change(input, { target: { value: " Ada " } });
        expect(createButton).toBeEnabled();

        vi.mocked(createProfile).mockResolvedValue(report as any);
        void mutation(0).mutationFn();
        expect(createProfile).toHaveBeenCalledWith(" Ada ", "uuid-1", "rejoin-1");

        mock.mutationResults[0] = report;
        fireEvent.click(createButton);
        expect(storeIdentity).toHaveBeenCalledWith({ id: "p1", token: "tok" });
        expect(mock.queryClient.setQueryData).toHaveBeenCalledWith(
            ["player-profile", "p1"],
            report,
        );
    });

    test("create omits missing game identifiers, shows pending state, and fetches the profile", () => {
        render(<ProfileView />);
        fireEvent.change(screen.getByLabelText("Nickname"), { target: { value: "Solo" } });
        vi.mocked(createProfile).mockResolvedValue(report as any);
        void mutation(0).mutationFn();
        expect(createProfile).toHaveBeenCalledWith("Solo", undefined, undefined);

        vi.mocked(fetchProfile).mockResolvedValue(report as any);
        void mock.queryOptions.queryFn();
        expect(fetchProfile).toHaveBeenCalledWith(mock.identity);

        mock.identity = identity;
        mock.query = { ...mock.query, isPending: true };
        const view = render(<ProfileView />);
        expect(screen.getByLabelText("Loading profile")).toBeInTheDocument();
        view.unmount();

        mock.identity = null;
        mock.mutationStates[0] = { isPending: true };
        render(<ProfileView />);
        expect(screen.getByRole("button", { name: "Creating…" })).toBeDisabled();
    });

    test("profile load error offers retry and forget", () => {
        mock.identity = identity;
        mock.query = {
            data: undefined,
            isPending: false,
            isError: true,
            error: new Error("expired"),
            refetch: vi.fn(),
        };
        render(<ProfileView />);
        expect(screen.getByText("Could not load your profile")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(mock.query.refetch).toHaveBeenCalled();
        fireEvent.click(screen.getByRole("button", { name: "Forget this device" }));
        expect(forgetIdentity).toHaveBeenCalled();
        expect(screen.getByRole("heading", { name: "Create a profile" })).toBeInTheDocument();
    });

    test("renders the full profile with achievements, history, and portfolio", () => {
        mock.identity = identity;
        mock.query = { data: report, isPending: false, isError: false, refetch: vi.fn() };
        render(<ProfileView />);
        expect(screen.getByRole("heading", { name: "Ada" })).toBeInTheDocument();
        expect(screen.getByText(/1 quiz \| 10 points \| 1\/2 correct/)).toBeInTheDocument();
        expect(screen.getByText("Unlocked")).toBeInTheDocument();
        expect(screen.getByText("Locked")).toBeInTheDocument();
        expect(screen.getAllByText("Quiz A")).toHaveLength(2);
        expect(screen.getByText(/1 attempt \| best 10 pts/)).toBeInTheDocument();
        expect(screen.getByText("10 pts")).toBeInTheDocument();

        fireEvent.change(screen.getByLabelText("Nickname"), { target: { value: "  " } });
        expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
        fireEvent.change(screen.getByLabelText("Nickname"), { target: { value: "Grace" } });
        expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();
    });

    test("shows plural stats and empty history and portfolio", () => {
        mock.identity = identity;
        mock.query = {
            data: {
                ...report,
                stats: { sessions: 2, totalScore: 0, correct: 0, answers: 0 },
                history: [],
                portfolio: [],
            },
            isPending: false,
            isError: false,
            refetch: vi.fn(),
        };
        render(<ProfileView />);
        expect(screen.getByText(/2 quizzes \| 0 points \| 0\/0 correct/)).toBeInTheDocument();
        expect(screen.getByText("No results linked yet")).toBeInTheDocument();
        expect(screen.getByText("No quizzes yet")).toBeInTheDocument();
    });

    test("falls back to Quiz when history and portfolio have no title", () => {
        mock.identity = identity;
        mock.query = {
            data: {
                ...report,
                history: [{ ...report.history[0], quizTitle: null }],
                portfolio: [{ ...report.portfolio[0], quizTitle: null, sessions: 3 }],
            },
            isPending: false,
            isError: false,
            refetch: vi.fn(),
        };
        render(<ProfileView />);
        expect(screen.getAllByText("Quiz").length).toBeGreaterThanOrEqual(2);
        expect(screen.getByText(/3 attempts \| best 10 pts/)).toBeInTheDocument();
    });

    test("links a game identity only when it differs from the profile's", () => {
        mock.identity = identity;
        mock.game = { playerName: "Ada", playerUuid: "uuid-1", rejoinToken: "rejoin-1" };
        mock.query = { data: report, isPending: false, isError: false, refetch: vi.fn() };
        const first = render(<ProfileView />);
        expect(screen.getByRole("button", { name: "Link this game identity" })).toBeInTheDocument();
        first.unmount();

        mock.game = { playerName: "Ada", playerUuid: null, rejoinToken: "rejoin-1" };
        const noUuid = render(<ProfileView />);
        expect(
            screen.queryByRole("button", { name: "Link this game identity" }),
        ).not.toBeInTheDocument();
        noUuid.unmount();

        mock.game = { playerName: "Ada", playerUuid: "uuid-1", rejoinToken: null };
        const noToken = render(<ProfileView />);
        expect(
            screen.queryByRole("button", { name: "Link this game identity" }),
        ).not.toBeInTheDocument();
        noToken.unmount();

        mock.query = {
            data: { ...report, profile: { ...report.profile, playerUuid: "uuid-1" } },
            isPending: false,
            isError: false,
            refetch: vi.fn(),
        };
        const linked = render(<ProfileView />);
        expect(
            screen.queryByRole("button", { name: "Link this game identity" }),
        ).not.toBeInTheDocument();
        linked.unmount();

        mock.query = { data: report, isPending: false, isError: false, refetch: vi.fn() };
        mock.game = { playerName: "Ada", playerUuid: "uuid-1", rejoinToken: "rejoin-1" };
        vi.mocked(updateProfile).mockResolvedValue(report as any);
        const view = render(<ProfileView />);
        void mutation(2).mutationFn();
        expect(updateProfile).toHaveBeenCalledWith(identity, {
            playerUuid: "uuid-1",
            rejoinToken: "rejoin-1",
        });
        mock.mutationResults[2] = report;
        fireEvent.click(screen.getByRole("button", { name: "Link this game identity" }));
        expect(mock.queryClient.setQueryData).toHaveBeenCalledWith(
            ["player-profile", "p1"],
            report,
        );
        view.unmount();

        mock.mutationStates[2] = { isPending: true };
        const pending = render(<ProfileView />);
        expect(screen.getByRole("button", { name: "Link this game identity" })).toBeDisabled();
        pending.unmount();
    });

    test("renames the profile and reports rename and link errors", () => {
        mock.identity = identity;
        mock.query = { data: report, isPending: false, isError: false, refetch: vi.fn() };
        const view = render(<ProfileView />);
        fireEvent.change(screen.getByLabelText("Nickname"), { target: { value: "Grace" } });
        vi.mocked(updateProfile).mockResolvedValue(report as any);
        void mutation(1).mutationFn("Grace");
        expect(updateProfile).toHaveBeenCalledWith(identity, { nickname: "Grace" });
        mock.mutationResults[1] = report;
        fireEvent.click(screen.getByRole("button", { name: "Save" }));
        expect(mock.queryClient.setQueryData).toHaveBeenCalledWith(
            ["player-profile", "p1"],
            report,
        );
        view.unmount();

        mock.mutationStates[1] = { isError: true, error: new Error("stale") };
        const renameError = render(<ProfileView />);
        expect(screen.getByRole("alert")).toHaveTextContent("Could not save that change");
        renameError.unmount();

        mock.mutationStates[1] = {};
        mock.mutationStates[2] = { isError: true, error: new Error("stale") };
        const linkError = render(<ProfileView />);
        expect(screen.getByRole("alert")).toHaveTextContent("Could not save that change");
        linkError.unmount();

        mock.mutationStates[1] = { isPending: true };
        const pending = render(<ProfileView />);
        expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
        pending.unmount();
    });

    test("forgetting a loaded profile returns to the create form", () => {
        mock.identity = identity;
        mock.query = { data: report, isPending: false, isError: false, refetch: vi.fn() };
        render(<ProfileView />);
        fireEvent.click(screen.getByRole("button", { name: "Forget this device" }));
        expect(forgetIdentity).toHaveBeenCalled();
        expect(screen.getByRole("heading", { name: "Create a profile" })).toBeInTheDocument();
    });
});
