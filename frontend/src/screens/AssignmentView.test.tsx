import type { ReactNode } from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { AssignmentView } from "./AssignmentView";

const rq = vi.hoisted(() => ({
    queries: {} as Record<string, any>,
    queryClient: { invalidateQueries: vi.fn() },
}));

vi.mock("@tanstack/react-query", () => ({
    useQueryClient: () => rq.queryClient,
    useQuery: ({ queryKey }: { queryKey: string[] }) =>
        rq.queries[queryKey[0]] ?? {
            isPending: false,
            isError: false,
            data: undefined,
            error: null,
            refetch: vi.fn(),
        },
    useMutation: () => ({ mutate: vi.fn(), isPending: false, isError: false, error: null, data: undefined }),
}));

vi.mock("../components/Shell", () => ({
    Shell: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("../services/AssignmentApiService", () => ({
    assignmentApi: {
        list: vi.fn(),
        progress: vi.fn(),
        roster: vi.fn(),
        create: vi.fn(),
        importRoster: vi.fn(),
        recordGrade: vi.fn(),
        start: vi.fn(),
    },
}));

describe("AssignmentView", () => {
    beforeEach(() => {
        rq.queries = {
            assignments: {
                isPending: false,
                isError: false,
                data: [{ id: "a1", title: "Quiz A", quizId: "quiz-123", releaseAt: null, dueAt: null }],
                error: null,
                refetch: vi.fn(),
            },
            "assignment-progress": {
                isPending: false,
                isError: false,
                data: [],
                error: null,
                refetch: vi.fn(),
            },
            "assignment-roster": {
                isPending: false,
                isError: false,
                data: [],
                error: null,
                refetch: vi.fn(),
            },
        };
    });

    test("exposes the selected tab to assistive technology", () => {
        render(<AssignmentView />);

        const listTab = screen.getByRole("tab", { name: "All assignments" });
        expect(listTab).toHaveAttribute("aria-selected", "true");
        const createTab = screen.getByRole("tab", { name: "New assignment" });
        expect(createTab).toHaveAttribute("aria-selected", "false");

        fireEvent.click(createTab);

        expect(listTab).toHaveAttribute("aria-selected", "false");
        expect(createTab).toHaveAttribute("aria-selected", "true");
        expect(screen.getByRole("tabpanel")).toHaveAttribute("aria-labelledby", "assignment-create-tab");
    });

    test("reports failed progress and roster fetches instead of empty data", () => {
        rq.queries["assignment-progress"] = {
            isPending: false,
            isError: true,
            data: undefined,
            error: new Error("offline"),
            refetch: vi.fn(),
        };
        rq.queries["assignment-roster"] = {
            isPending: false,
            isError: true,
            data: undefined,
            error: new Error("offline"),
            refetch: vi.fn(),
        };
        render(<AssignmentView />);

        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));

        expect(screen.getByText("Could not load student progress")).toBeInTheDocument();
        expect(screen.queryByText("No progress yet.")).not.toBeInTheDocument();
        expect(screen.getByText("Could not load roster")).toBeInTheDocument();
        expect(screen.queryByText("0 students in roster")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(rq.queries["assignment-progress"].refetch).toHaveBeenCalled();
    });
});
