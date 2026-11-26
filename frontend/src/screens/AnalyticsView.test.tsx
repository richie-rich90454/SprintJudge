import type { ReactNode } from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { AnalyticsView } from "./AnalyticsView";
import { buildAdaptiveSet, getClassReport, getItemReports, getStudentReports } from "../services/AnalyticsApiService";

const rq = vi.hoisted(() => ({
    queries: {} as Record<string, any>,
    queryOptions: {} as Record<string, any>,
    keys: [] as string[][],
    mutationOptions: {} as any,
    mutationCalls: 0,
    mutationState: {} as Record<string, any>,
    mutationResult: undefined as any,
}));

vi.mock("@tanstack/react-query", () => ({
    useQuery: (options: { queryKey: string[] }) => {
        rq.keys.push(options.queryKey);
        rq.queryOptions[options.queryKey[0]] = options;
        return rq.queries[options.queryKey[0]] ?? {
            isPending: false,
            isError: false,
            data: undefined,
            error: null,
            refetch: vi.fn(),
        };
    },
    useMutation: (options: any) => {
        rq.mutationCalls++;
        rq.mutationOptions = options;
        const state = rq.mutationState;
        return {
            mutate: (arg: unknown) => {
                void options.mutationFn(arg);
                if (rq.mutationResult !== undefined) options.onSuccess?.(rq.mutationResult, arg);
            },
            isPending: state.isPending ?? false,
            isError: state.isError ?? false,
            error: state.error ?? null,
            data: rq.mutationResult,
        };
    },
}));

vi.mock("../components/Shell", () => ({
    Shell: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("../services/BankApiService", () => ({ getBankVolume: vi.fn() }));

vi.mock("../services/AnalyticsApiService", () => ({
    buildAdaptiveSet: vi.fn(),
    getClassReport: vi.fn(),
    getItemReports: vi.fn(),
    getStudentReports: vi.fn(),
}));

const ok = (data: unknown) => ({ isPending: false, isError: false, data, error: null, refetch: vi.fn() });
const pending = () => ({ isPending: true, isError: false, data: undefined, error: null, refetch: vi.fn() });
const failed = () => ({
    isPending: false,
    isError: true,
    data: undefined,
    error: new Error("offline"),
    refetch: vi.fn(),
});

const classReport = {
    players: 1,
    sessionCount: 1,
    attempts: 1,
    questions: 1,
    accuracy: 0.5,
    avgScore: 10,
    sessionStats: [{ sessionId: "s1", players: 1, accuracy: 0.8 }],
};

describe("AnalyticsView", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        rq.keys = [];
        rq.queryOptions = {};
        rq.mutationState = {};
        rq.mutationResult = undefined;
        rq.queries = {
            "bank-volume": ok({ MATH: 5, SCI: 2 }),
            "analytics-students": ok([]),
            "analytics-classes": ok(classReport),
            "analytics-items": ok([]),
        };
        vi.mocked(buildAdaptiveSet).mockResolvedValue({ questionCount: 2, quizId: "q1" } as any);
        vi.mocked(getStudentReports).mockResolvedValue([] as any);
        vi.mocked(getClassReport).mockResolvedValue(classReport as any);
        vi.mocked(getItemReports).mockResolvedValue([] as any);
    });

    test("surfaces student and item fetch errors with retry actions", () => {
        rq.queries["analytics-students"] = failed();
        rq.queries["analytics-items"] = failed();
        render(<AnalyticsView />);

        expect(screen.getByText("Could not load the student report")).toBeInTheDocument();
        expect(screen.getByText("Could not load item data")).toBeInTheDocument();
        const retries = screen.getAllByRole("button", { name: "Retry" });
        expect(retries).toHaveLength(2);
        fireEvent.click(retries[0]);
        expect(rq.queries["analytics-students"].refetch).toHaveBeenCalled();
        fireEvent.click(retries[1]);
        expect(rq.queries["analytics-items"].refetch).toHaveBeenCalled();
    });

    test("renders partial API responses without throwing", () => {
        rq.queries["analytics-classes"] = ok({
            players: 1,
            sessionCount: 1,
            attempts: 1,
            questions: 1,
            accuracy: 0.5,
            avgScore: 10,
        });
        rq.queries["analytics-students"] = ok([
            { playerUuid: "p1", playerName: "Ada", attempts: 1, accuracy: 0.5, avgScore: 10 },
        ]);
        rq.queries["analytics-items"] = ok([
            { bankId: "b1", title: "Item", unit: "u", attempts: 1, pValue: 0.5, bankDifficulty: null },
        ]);
        rq.mutationResult = { questionCount: 2, quizId: "q1", avgDifficulty: 0.5, skipped: 0 };

        expect(() => render(<AnalyticsView />)).not.toThrow();
        expect(screen.getAllByText("-").length).toBeGreaterThanOrEqual(3);
        expect(screen.getByText(/Average difficulty/)).toBeInTheDocument();
        expect(screen.getByText("2 questions ready")).toBeInTheDocument();
    });

    test("queries the session filter only after Apply is clicked", () => {
        render(<AnalyticsView />);
        const studentKeys = () => rq.keys.filter((key) => key[0] === "analytics-students");

        fireEvent.change(screen.getByLabelText(/Session id/), { target: { value: "session-42" } });
        expect(rq.keys).not.toContainEqual(["analytics-students", "session-42"]);
        expect(studentKeys().every((key) => key[1] === "")).toBe(true);

        fireEvent.click(screen.getByRole("button", { name: "Apply filter" }));
        expect(studentKeys()).toContainEqual(["analytics-students", "session-42"]);
        expect(rq.keys).toContainEqual(["analytics-classes", "session-42"]);
    });

    test("calls every query function with the applied subject and session", () => {
        render(<AnalyticsView />);
        void rq.queryOptions["analytics-students"].queryFn();
        void rq.queryOptions["analytics-classes"].queryFn();
        void rq.queryOptions["analytics-items"].queryFn();
        expect(getStudentReports).toHaveBeenCalledWith("");
        expect(getClassReport).toHaveBeenCalledWith("");
        expect(getItemReports).toHaveBeenCalledWith("MATH");

        fireEvent.click(screen.getByRole("button", { name: "Apply filter" }));
        fireEvent.change(screen.getByLabelText("Subject"), { target: { value: "SCI" } });
        void rq.queryOptions["analytics-students"].queryFn();
        void rq.queryOptions["analytics-items"].queryFn();
        expect(getStudentReports).toHaveBeenCalledWith("");
        expect(getItemReports).toHaveBeenCalledWith("SCI");
    });

    test("handles missing volume data and a builder with no subject", () => {
        rq.queries["bank-volume"] = ok(undefined);
        render(<AnalyticsView />);
        expect(screen.getByRole("button", { name: "Build playable quiz" })).toBeDisabled();
        const button = screen.getByRole("button", { name: "Build playable quiz" });
        const propsKey = Object.keys(button).find((key) => key.startsWith("__reactProps"));
        expect(propsKey).toBeDefined();
        (button as any)[propsKey!].onClick();
        expect(buildAdaptiveSet).not.toHaveBeenCalled();
    });

    test("builds an adaptive set with all controls and shows its result", () => {
        const view = render(<AnalyticsView />);
        fireEvent.change(screen.getByLabelText("Mode"), { target: { value: "adaptive" } });
        fireEvent.change(screen.getByLabelText("Questions"), { target: { value: "7" } });
        fireEvent.change(screen.getByLabelText("Target difficulty"), { target: { value: "0.75" } });
        fireEvent.change(screen.getByLabelText("Title (optional)"), { target: { value: " Mixed " } });
        rq.mutationResult = {
            questionCount: 2,
            quizId: "q1",
            avgDifficulty: 0.5,
            avgDiscrimination: 0.8,
            skipped: 0,
            difficultyBands: { easy: 1, hard: 1 },
        };
        fireEvent.click(screen.getByRole("button", { name: "Build playable quiz" }));
        view.rerender(<AnalyticsView />);
        expect(buildAdaptiveSet).toHaveBeenCalledWith({
            subject: "MATH",
            size: 7,
            mode: "adaptive",
            targetDifficulty: 0.75,
            title: "Mixed",
        });
        expect(screen.getByText("2 questions ready")).toBeInTheDocument();
        expect(screen.getByText("q1")).toBeInTheDocument();
        expect(screen.getByText(/Average difficulty 50%, discrimination 0.80, 0 skipped/)).toBeInTheDocument();
        expect(screen.getByText("easy: 1")).toBeInTheDocument();
        expect(screen.getByText("hard: 1")).toBeInTheDocument();
    });

    test("omits an empty title and reports build errors", () => {
        render(<AnalyticsView />);
        rq.mutationResult = undefined;
        fireEvent.click(screen.getByRole("button", { name: "Build playable quiz" }));
        expect(buildAdaptiveSet).toHaveBeenCalledWith(
            expect.objectContaining({ title: undefined }),
        );

        rq.mutationState = { isError: true, error: new Error("build failed") };
        const withError = render(<AnalyticsView />);
        expect(screen.getByRole("alert")).toHaveTextContent("build failed");
        withError.unmount();

        rq.mutationState = { isError: true, error: { code: 500 } };
        const unknown = render(<AnalyticsView />);
        expect(screen.getByRole("alert")).toHaveTextContent("Build failed");
        unknown.unmount();

        rq.mutationState = { isPending: true };
        render(<AnalyticsView />);
        expect(screen.getByRole("button", { name: "Building..." })).toBeDisabled();
    });

    test("renders class, student, and item loading states", () => {
        rq.queries["analytics-classes"] = pending();
        rq.queries["analytics-students"] = pending();
        rq.queries["analytics-items"] = pending();
        render(<AnalyticsView />);
        expect(document.querySelectorAll('[class*="h-32"]')).toHaveLength(3);
    });

    test("class error offers retry and session stats render when present", () => {
        rq.queries["analytics-classes"] = failed();
        const first = render(<AnalyticsView />);
        expect(screen.getByText("Could not load the class summary")).toBeInTheDocument();
        fireEvent.click(screen.getAllByRole("button", { name: "Retry" })[0]);
        expect(rq.queries["analytics-classes"].refetch).toHaveBeenCalled();
        first.unmount();

        rq.queries["analytics-classes"] = ok({ ...classReport, sessionStats: undefined });
        const noStats = render(<AnalyticsView />);
        expect(screen.queryByRole("table")).not.toBeInTheDocument();
        noStats.unmount();

        rq.queries["analytics-classes"] = ok(classReport);
        render(<AnalyticsView />);
        expect(screen.getByRole("table")).toBeInTheDocument();
        expect(screen.getByText("s1")).toBeInTheDocument();
        expect(screen.getByText("80%")).toBeInTheDocument();
    });

    test("renders empty reports and student/item rows with fallbacks", () => {
        const empty = render(<AnalyticsView />);
        expect(screen.getByText("No submissions yet")).toBeInTheDocument();
        expect(screen.getByText("No item data yet")).toBeInTheDocument();
        empty.unmount();

        rq.queries["analytics-students"] = ok([
            { playerUuid: "p2", playerName: "", attempts: 2, accuracy: 0.25, avgScore: 9.6, avgAttemptsPerItem: 1.23 },
        ]);
        rq.queries["analytics-items"] = ok([
            {
                bankId: "b2",
                title: "Observed",
                unit: "u",
                attempts: 3,
                pValue: 0.4,
                bankDifficulty: 0.6,
                discrimination: 0.75,
                medianSec: 12.6,
            },
        ]);
        render(<AnalyticsView />);
        expect(screen.getByText("p2")).toBeInTheDocument();
        expect(screen.getByText("1.2")).toBeInTheDocument();
        expect(screen.getByText("40% / 60%")).toBeInTheDocument();
        expect(screen.getByText("0.75")).toBeInTheDocument();
        expect(screen.getByText("13")).toBeInTheDocument();
    });
});
