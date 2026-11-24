import type { ReactNode } from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { AnalyticsView } from "./AnalyticsView";

const rq = vi.hoisted(() => ({
    queries: {} as Record<string, any>,
    mutation: { mutate: vi.fn(), isPending: false, isError: false, error: null, data: undefined as any },
}));

vi.mock("@tanstack/react-query", () => ({
    useQuery: ({ queryKey }: { queryKey: string[] }) =>
        rq.queries[queryKey[0]] ?? {
            isPending: false,
            isError: false,
            data: undefined,
            error: null,
            refetch: vi.fn(),
        },
    useMutation: () => rq.mutation,
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
const failed = () => ({
    isPending: false,
    isError: true,
    data: undefined,
    error: new Error("offline"),
    refetch: vi.fn(),
});

describe("AnalyticsView", () => {
    beforeEach(() => {
        rq.queries = {
            "bank-volume": ok({ MATH: 5 }),
            "analytics-students": ok([]),
            "analytics-classes": ok({
                players: 1,
                sessionCount: 1,
                attempts: 1,
                questions: 1,
                accuracy: 0.5,
                avgScore: 10,
                sessionStats: [],
            }),
            "analytics-items": ok([]),
        };
        rq.mutation = { mutate: vi.fn(), isPending: false, isError: false, error: null, data: undefined };
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
        rq.mutation.data = { questionCount: 2, quizId: "q1", avgDifficulty: 0.5, skipped: 0 };

        expect(() => render(<AnalyticsView />)).not.toThrow();
        expect(screen.getAllByText("-").length).toBeGreaterThanOrEqual(3);
        expect(screen.getByText(/Average difficulty/)).toBeInTheDocument();
        expect(screen.getByText("2 questions ready")).toBeInTheDocument();
    });
});
