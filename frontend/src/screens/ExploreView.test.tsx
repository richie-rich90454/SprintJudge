import type { ReactNode } from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ExploreView } from "./ExploreView";

const rq = vi.hoisted(() => ({
    state: { isPending: false, isError: false, data: undefined as any, refetch: vi.fn() },
    queryFn: undefined as (() => unknown) | undefined,
    listBanks: vi.fn(),
}));

vi.mock("@tanstack/react-query", () => ({
    useQuery: (options: { queryFn: () => unknown }) => {
        rq.queryFn = options.queryFn;
        return rq.state;
    },
}));
vi.mock("../components/Shell", () => ({
    Shell: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@tanstack/react-router", () => ({
    Link: ({ children, to, search }: { children: ReactNode; to: string; search?: { bank?: string } }) => (
        <a href={search ? `${to}?bank=${search.bank}` : to}>{children}</a>
    ),
}));
vi.mock("../services/AdminApiService", () => ({ adminApi: { listBanks: rq.listBanks } }));

const banks = [
    { id: "b1", title: "Java Programming", description: "Core syntax" },
    { id: "b2", title: "Biology", description: null },
];

describe("ExploreView", () => {
    beforeEach(() => {
        rq.state = { isPending: false, isError: false, data: banks, refetch: vi.fn() };
    });

    test("fetches banks through the configured query", () => {
        render(<ExploreView />);
        rq.queryFn?.();
        expect(rq.listBanks).toHaveBeenCalledOnce();
    });

    test("renders loading skeletons while the library is pending", () => {
        rq.state = { isPending: true, isError: false, data: undefined, refetch: vi.fn() };
        render(<ExploreView />);
        expect(screen.getByLabelText("Loading quizzes")).toBeInTheDocument();
        expect(screen.getByText("Browse quizzes")).toBeInTheDocument();
    });

    test("offers a retry action on a failed fetch", () => {
        rq.state = { isPending: false, isError: true, data: undefined, refetch: vi.fn() };
        render(<ExploreView />);
        expect(screen.getByText("Could not load the library")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(rq.state.refetch).toHaveBeenCalledOnce();
    });

    test("shows an empty state when no quizzes exist", () => {
        rq.state = { isPending: false, isError: false, data: [], refetch: vi.fn() };
        render(<ExploreView />);
        expect(screen.getByText("No quizzes yet")).toBeInTheDocument();
    });

    test("filters by title and description and reports no matches", () => {
        render(<ExploreView />);
        const search = screen.getByRole("textbox", { name: "Search quizzes" });
        fireEvent.change(search, { target: { value: "CORE" } });
        expect(screen.getByText("1 of 2 quizzes")).toBeInTheDocument();
        expect(screen.getByText("Java Programming")).toBeInTheDocument();
        expect(screen.queryByText("Biology")).not.toBeInTheDocument();

        fireEvent.change(search, { target: { value: "  bio " } });
        expect(screen.getByText("1 of 2 quizzes")).toBeInTheDocument();
        expect(screen.getByText("Biology")).toBeInTheDocument();

        fireEvent.change(search, { target: { value: "nothing" } });
        expect(screen.getByText("No matches")).toBeInTheDocument();
        expect(screen.getByText(/nothing/)).toBeInTheDocument();
    });

    test("links each quiz to practice and joining, and pages through results", () => {
        rq.state = {
            isPending: false,
            isError: false,
            data: Array.from({ length: 26 }, (_, i) => ({
                id: `b${i}`,
                title: `Bank ${i}`,
                description: i === 25 ? "described" : null,
            })),
            refetch: vi.fn(),
        };
        render(<ExploreView />);
        expect(screen.getByText(/of 26 quizzes/)).toBeInTheDocument();
        expect(screen.getAllByRole("link", { name: "Practice solo" })[0]).toHaveAttribute("href", "/practice?bank=b0");
        expect(screen.getAllByRole("link", { name: "Join with PIN" })[0]).toHaveAttribute("href", "/join");
        expect(screen.queryByText("Bank 25")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Show more (2 remaining)" }));
        expect(screen.getByText("Bank 25")).toBeInTheDocument();
        expect(screen.getByText("described")).toBeInTheDocument();
    });
});
