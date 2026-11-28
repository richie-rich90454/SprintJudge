import type { ReactNode } from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { AssignmentView } from "./AssignmentView";
import { assignmentApi } from "../services/AssignmentApiService";

const rq = vi.hoisted(() => ({
    queries: {} as Record<string, any>,
    queryOptions: {} as Record<string, any>,
    queryClient: { invalidateQueries: vi.fn() },
    mutationOptions: [] as any[],
    mutationCalls: 0,
    mutationStates: {} as Record<number, any>,
    mutationResults: {} as Record<number, any>,
}));

vi.mock("@tanstack/react-query", () => ({
    useQueryClient: () => rq.queryClient,
    useQuery: (options: { queryKey: string[] }) => {
        rq.queryOptions[options.queryKey[0]] = options;
        return (
            rq.queries[options.queryKey[0]] ?? {
                isPending: false,
                isError: false,
                data: undefined,
                error: null,
                refetch: vi.fn(),
            }
        );
    },
    useMutation: (options: any) => {
        const index = rq.mutationCalls++;
        rq.mutationOptions[index] = options;
        const slot = index % 4;
        const state = rq.mutationStates[slot] ?? {};
        return {
            mutate: (arg: unknown) => {
                const result = rq.mutationResults[slot];
                if (result !== undefined) {
                    rq.mutationStates[slot] = { ...(rq.mutationStates[slot] ?? {}), data: result };
                }
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

const ok = (data: unknown) => ({
    isPending: false,
    isError: false,
    data,
    error: null,
    refetch: vi.fn(),
});
const pending = () => ({
    isPending: true,
    isError: false,
    data: undefined,
    error: null,
    refetch: vi.fn(),
});
const failed = (message = "offline") => ({
    isPending: false,
    isError: true,
    data: undefined,
    error: new Error(message),
    refetch: vi.fn(),
});

const assignment = { id: "a1", title: "Quiz A", quizId: "quiz-123", releaseAt: null, dueAt: null };
const mutation = (slot: number) => rq.mutationOptions[rq.mutationCalls - 4 + slot];

describe("AssignmentView", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        rq.mutationCalls = 0;
        rq.mutationOptions = [];
        rq.mutationStates = {};
        rq.mutationResults = {};
        rq.queryOptions = {};
        rq.queries = {
            assignments: ok([assignment]),
            "assignment-progress": ok([]),
            "assignment-roster": ok([]),
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
        expect(screen.getByRole("tabpanel")).toHaveAttribute(
            "aria-labelledby",
            "assignment-create-tab",
        );
        fireEvent.click(listTab);
        expect(screen.getByRole("tabpanel")).toHaveAttribute(
            "aria-labelledby",
            "assignment-list-tab",
        );
    });

    test("supports arrow-key navigation between tabs and ignores other keys", () => {
        render(<AssignmentView />);
        const listTab = screen.getByRole("tab", { name: "All assignments" });
        const createTab = screen.getByRole("tab", { name: "New assignment" });

        expect(listTab).toHaveAttribute("aria-controls", "assignment-list-panel");
        expect(createTab).toHaveAttribute("aria-controls", "assignment-create-panel");
        expect(listTab).toHaveAttribute("tabindex", "0");
        expect(createTab).toHaveAttribute("tabindex", "-1");

        fireEvent.keyDown(listTab, { key: "ArrowRight" });
        expect(createTab).toHaveAttribute("aria-selected", "true");
        expect(createTab).toHaveFocus();
        fireEvent.keyDown(createTab, { key: "ArrowLeft" });
        expect(listTab).toHaveAttribute("aria-selected", "true");
        fireEvent.keyDown(listTab, { key: "ArrowDown" });
        expect(createTab).toHaveAttribute("aria-selected", "true");
        fireEvent.keyDown(createTab, { key: "ArrowUp" });
        expect(listTab).toHaveAttribute("aria-selected", "true");
        fireEvent.keyDown(listTab, { key: "End" });
        expect(createTab).toHaveAttribute("aria-selected", "true");
        fireEvent.keyDown(createTab, { key: "Home" });
        expect(listTab).toHaveAttribute("aria-selected", "true");
        fireEvent.keyDown(listTab, { key: "Enter" });
        expect(listTab).toHaveAttribute("aria-selected", "true");
    });

    test("builds query and mutation functions for selected and unselected assignments", () => {
        render(<AssignmentView />);
        vi.mocked(assignmentApi.progress).mockResolvedValue([] as any);
        vi.mocked(assignmentApi.roster).mockResolvedValue([] as any);

        void rq.queryOptions["assignment-progress"].queryFn();
        void rq.queryOptions["assignment-roster"].queryFn();
        expect(assignmentApi.progress).toHaveBeenCalledWith("");
        expect(assignmentApi.roster).toHaveBeenCalledWith("");

        void mutation(1).mutationFn();
        void mutation(2).mutationFn({ studentId: "s1", score: 1, maxScore: 1 });
        void mutation(3).mutationFn();
        expect(assignmentApi.importRoster).toHaveBeenCalledWith("", "");
        expect(assignmentApi.recordGrade).toHaveBeenCalledWith("", "s1", 1, 1);
        expect(assignmentApi.start).toHaveBeenCalledWith("", "");

        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));
        void rq.queryOptions["assignment-progress"].queryFn();
        void rq.queryOptions["assignment-roster"].queryFn();
        expect(assignmentApi.progress).toHaveBeenCalledWith("a1");
        expect(assignmentApi.roster).toHaveBeenCalledWith("a1");
    });

    test("reports failed progress and roster fetches instead of empty data", () => {
        rq.queries["assignment-progress"] = failed();
        rq.queries["assignment-roster"] = failed();
        render(<AssignmentView />);

        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));

        expect(screen.getByText("Could not load student progress")).toBeInTheDocument();
        expect(screen.queryByText("No progress yet.")).not.toBeInTheDocument();
        expect(screen.getByText("Could not load roster")).toBeInTheDocument();
        expect(screen.queryByText("0 students in roster")).not.toBeInTheDocument();

        fireEvent.click(screen.getAllByRole("button", { name: "Retry" })[0]);
        expect(rq.queries["assignment-progress"].refetch).toHaveBeenCalled();
    });

    test("covers list loading, error, empty, and formatted assignment rows", () => {
        rq.queries.assignments = pending();
        const first = render(<AssignmentView />);
        expect(screen.getByRole("status", { name: "Loading assignments" })).toBeInTheDocument();
        first.unmount();

        rq.queries.assignments = { ...failed("server down"), refetch: vi.fn() };
        const second = render(<AssignmentView />);
        expect(screen.getByText("Could not load assignments")).toBeInTheDocument();
        expect(screen.getByText("server down")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(rq.queries.assignments.refetch).toHaveBeenCalled();
        second.unmount();

        rq.queries.assignments = ok([]);
        const third = render(<AssignmentView />);
        expect(screen.getByText("No assignments yet")).toBeInTheDocument();
        third.unmount();

        rq.queries.assignments = ok([{ ...assignment, releaseAt: 1700000000, dueAt: 1700003600 }]);
        render(<AssignmentView />);
        expect(screen.getByText(/Releases/)).toBeInTheDocument();
        expect(screen.getByText(/Due/)).toBeInTheDocument();
        expect(screen.getByText("Quiz quiz-123")).toBeInTheDocument();
    });

    test("create form trims values, converts dates, and resets on success", () => {
        render(<AssignmentView />);
        fireEvent.click(screen.getByRole("tab", { name: "New assignment" }));

        const createButton = screen.getByRole("button", { name: "Create assignment" });
        expect(createButton).toBeDisabled();
        fireEvent.change(screen.getByLabelText("Quiz"), { target: { value: " quiz-9 " } });
        expect(createButton).toBeDisabled();
        fireEvent.change(screen.getByLabelText("Title"), { target: { value: " Week 3 " } });
        expect(createButton).toBeEnabled();
        fireEvent.change(screen.getByLabelText("Description"), { target: { value: " practice " } });
        fireEvent.change(screen.getByLabelText("Release at"), {
            target: { value: "2024-01-01T00:00" },
        });
        fireEvent.change(screen.getByLabelText("Due at"), {
            target: { value: "2024-02-02T00:00" },
        });

        vi.mocked(assignmentApi.create).mockResolvedValue({ id: "a2" } as any);
        void mutation(0).mutationFn();
        expect(assignmentApi.create).toHaveBeenCalledWith({
            quizId: "quiz-9",
            title: "Week 3",
            description: "practice",
            releaseAt: Math.floor(new Date("2024-01-01T00:00").getTime() / 1000),
            dueAt: Math.floor(new Date("2024-02-02T00:00").getTime() / 1000),
        });
        fireEvent.change(screen.getByLabelText("Description"), { target: { value: "   " } });
        void mutation(0).mutationFn();
        expect(assignmentApi.create).toHaveBeenLastCalledWith(
            expect.objectContaining({ description: undefined }),
        );

        rq.mutationResults[0] = { id: "a2" };
        fireEvent.click(createButton);
        expect(screen.getByRole("tab", { name: "All assignments" })).toHaveAttribute(
            "aria-selected",
            "true",
        );
        expect(screen.getByRole("button", { name: "Quiz A" })).toBeInTheDocument();
        expect(rq.queryClient.invalidateQueries).toHaveBeenCalledWith({
            queryKey: ["assignments"],
        });
        expect(rq.queryClient.invalidateQueries).toHaveBeenCalledWith({
            queryKey: ["assignment-progress", null],
        });

        fireEvent.click(screen.getByRole("tab", { name: "New assignment" }));
        expect(screen.getByLabelText("Quiz")).toHaveValue("");
        expect(screen.getByLabelText("Title")).toHaveValue("");
    });

    test("toEpoch handles empty and invalid datetime values", () => {
        render(<AssignmentView />);
        fireEvent.click(screen.getByRole("tab", { name: "New assignment" }));
        fireEvent.change(screen.getByLabelText("Quiz"), { target: { value: "quiz-1" } });
        fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Title" } });
        void mutation(0).mutationFn();
        expect(assignmentApi.create).toHaveBeenLastCalledWith(
            expect.objectContaining({ releaseAt: null, dueAt: null }),
        );

        const due = screen.getByLabelText("Due at") as HTMLInputElement;
        Object.defineProperty(due, "value", {
            configurable: true,
            writable: true,
            value: "garbage",
        });
        fireEvent.change(due);
        void mutation(0).mutationFn();
        expect(assignmentApi.create).toHaveBeenLastCalledWith(
            expect.objectContaining({ dueAt: null }),
        );
    });

    test("renders create error for Error and non-Error rejections, including pending state", () => {
        rq.mutationStates[0] = { isError: true, error: new Error("invalid quiz") };
        const first = render(<AssignmentView />);
        fireEvent.click(screen.getByRole("tab", { name: "New assignment" }));
        expect(screen.getByText("invalid quiz")).toBeInTheDocument();
        first.unmount();

        rq.mutationStates[0] = { isError: true, error: "bad" };
        const view = render(<AssignmentView />);
        fireEvent.click(screen.getByRole("tab", { name: "New assignment" }));
        expect(screen.getByText("Something went wrong")).toBeInTheDocument();
        view.unmount();

        rq.mutationStates[0] = { isPending: true };
        render(<AssignmentView />);
        fireEvent.click(screen.getByRole("tab", { name: "New assignment" }));
        expect(screen.getByRole("button", { name: "Creating…" })).toBeDisabled();
    });

    test("imports a roster, reports errors and pending state, and refreshes", () => {
        const first = render(<AssignmentView />);
        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));

        const importButton = screen.getByRole("button", { name: "Import CSV" });
        expect(importButton).toBeDisabled();
        fireEvent.change(screen.getByLabelText("Roster CSV"), { target: { value: "s1\ns2" } });
        expect(importButton).toBeEnabled();
        expect(screen.getByText("0 students in roster")).toBeInTheDocument();

        vi.mocked(assignmentApi.importRoster).mockResolvedValue(undefined as any);
        void mutation(1).mutationFn();
        expect(assignmentApi.importRoster).toHaveBeenCalledWith("a1", "s1\ns2");

        rq.mutationResults[1] = undefined;
        fireEvent.click(importButton);
        expect(screen.getByLabelText("Roster CSV")).toHaveValue("");
        expect(rq.queryClient.invalidateQueries).toHaveBeenCalledWith({
            queryKey: ["assignment-roster", "a1"],
        });

        first.unmount();
        rq.queries["assignment-roster"] = ok([{ studentId: "s1" }]);
        const view = render(<AssignmentView />);
        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));
        expect(screen.getByText("1 students in roster")).toBeInTheDocument();
        view.unmount();

        rq.queries["assignment-roster"] = ok(undefined);
        const missing = render(<AssignmentView />);
        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));
        expect(screen.getByText("0 students in roster")).toBeInTheDocument();
        missing.unmount();

        rq.mutationStates[1] = { isPending: true };
        const pendingView = render(<AssignmentView />);
        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));
        expect(screen.getByRole("button", { name: "Importing…" })).toBeDisabled();
        pendingView.unmount();

        rq.mutationStates[1] = { isError: true, error: { code: 400 } };
        render(<AssignmentView />);
        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));
        expect(screen.getByText("Something went wrong")).toBeInTheDocument();
    });

    test("renders progress rows in every status and saves grades", () => {
        rq.queries["assignment-progress"] = ok([
            { id: "r1", studentId: "s1", status: "GRADED", score: 80, maxScore: 100 },
            { id: "", studentId: "s2", status: "IN_PROGRESS", score: null, maxScore: null },
            { id: "r3", studentId: "s3", status: "SUBMITTED", score: 60, maxScore: null },
            { id: "r4", studentId: "s4", status: "ABSENT", score: null, maxScore: 120 },
        ]);
        render(<AssignmentView />);
        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));
        expect(screen.queryByText("No progress yet.")).not.toBeInTheDocument();
        expect(screen.getByText("GRADED")).toBeInTheDocument();
        expect(screen.getByText("IN PROGRESS")).toBeInTheDocument();
        expect(screen.getByText("SUBMITTED")).toBeInTheDocument();
        expect(screen.getByText("ABSENT")).toBeInTheDocument();

        expect(screen.getByLabelText("Score for s1")).toHaveValue(80);
        expect(screen.getByLabelText("Score for s2")).toHaveValue(100);
        expect(screen.getByLabelText("Score for s4")).toHaveValue(100);
        fireEvent.change(screen.getByLabelText("Score for s1"), { target: { value: "90" } });
        expect(screen.getByLabelText("Score for s1")).toHaveValue(90);

        vi.mocked(assignmentApi.recordGrade).mockResolvedValue(undefined as any);
        void mutation(2).mutationFn({ studentId: "s1", score: 90, maxScore: 100 });
        expect(assignmentApi.recordGrade).toHaveBeenCalledWith("a1", "s1", 90, 100);

        fireEvent.click(screen.getAllByRole("button", { name: "Save grade" })[0]);
        expect(assignmentApi.recordGrade).toHaveBeenCalledWith("a1", "s1", 90, 100);
        expect(rq.queryClient.invalidateQueries).toHaveBeenCalledWith({
            queryKey: ["assignment-progress", "a1"],
        });

        fireEvent.click(screen.getAllByRole("button", { name: "Save grade" })[2]);
        void mutation(2).mutationFn({ studentId: "s3", score: 60, maxScore: 100 });
        expect(assignmentApi.recordGrade).toHaveBeenCalledWith("a1", "s3", 60, 100);
    });

    test("shows progress loading, grade error, and grade pending state", () => {
        rq.queries["assignment-progress"] = pending();
        render(<AssignmentView />);
        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));
        expect(screen.queryByText("No progress yet.")).not.toBeInTheDocument();
        expect(document.querySelector('[class*="h-20"]')).toBeInTheDocument();
    });

    test("grade errors use the shared error text", () => {
        rq.queries["assignment-progress"] = ok([
            { id: "r1", studentId: "s1", status: "GRADED", score: 80, maxScore: 100 },
        ]);
        rq.mutationStates[2] = { isError: true, error: new Error("grade failed") };
        render(<AssignmentView />);
        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));
        expect(screen.getByText("grade failed")).toBeInTheDocument();
    });

    test("starts an assignment, shows the server result, and handles errors", () => {
        const view = render(<AssignmentView />);
        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));

        const startButton = screen.getByRole("button", { name: "Start assignment" });
        expect(startButton).toBeDisabled();
        fireEvent.change(screen.getByLabelText("Student id"), { target: { value: " s5 " } });
        expect(startButton).toBeEnabled();

        vi.mocked(assignmentApi.start).mockResolvedValue({
            studentId: "s5",
            status: "ACTIVE",
        } as any);
        void mutation(3).mutationFn();
        expect(assignmentApi.start).toHaveBeenCalledWith("a1", "s5");

        rq.mutationResults[3] = { studentId: "s5", status: "ACTIVE" };
        fireEvent.click(startButton);
        view.rerender(<AssignmentView />);
        expect(screen.getByText("s5: ACTIVE")).toBeInTheDocument();
    });

    test("start errors and pending state are reported", () => {
        rq.mutationStates[3] = { isPending: true };
        const pendingView = render(<AssignmentView />);
        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));
        expect(screen.getByRole("button", { name: "Start assignment" })).toBeDisabled();
        pendingView.unmount();

        rq.mutationStates[3] = { isError: true, error: new Error("start failed") };
        render(<AssignmentView />);
        fireEvent.click(screen.getByRole("button", { name: "Quiz A" }));
        expect(screen.getByText("start failed")).toBeInTheDocument();
    });

    test("list query calls the API without losing its receiver", async () => {
        render(<AssignmentView />);
        await rq.queryOptions["assignments"].queryFn();
        expect(assignmentApi.list).toHaveBeenCalled();
    });

    test("deselects an assignment when the open row is clicked again", () => {
        render(<AssignmentView />);
        const row = screen.getByRole("button", { name: "Quiz A" });
        fireEvent.click(row);
        expect(row).toHaveAttribute("aria-expanded", "true");
        fireEvent.click(row);
        expect(row).toHaveAttribute("aria-expanded", "false");
        expect(screen.queryByLabelText("Roster CSV")).not.toBeInTheDocument();
    });
});
