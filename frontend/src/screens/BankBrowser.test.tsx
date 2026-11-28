import type { ReactNode } from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { BankBrowser } from "./BankBrowser";

const rq = vi.hoisted(() => ({
    queries: {} as Record<string, any>,
    keys: [] as string[][],
    fns: {} as Record<string, () => unknown>,
}));

vi.mock("@tanstack/react-query", () => ({
    useQuery: ({ queryKey, queryFn }: { queryKey: string[]; queryFn: () => unknown }) => {
        rq.keys.push(queryKey);
        rq.fns[queryKey[0]] = queryFn;
        return (
            rq.queries[queryKey[0]] ?? {
                isPending: false,
                isError: false,
                data: undefined,
                refetch: vi.fn(),
            }
        );
    },
}));
vi.mock("../components/Shell", () => ({
    Shell: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
const bankApi = vi.hoisted(() => ({
    getBankCoverage: vi.fn(),
    getBankQuestion: vi.fn(),
    searchBank: vi.fn(),
}));
vi.mock("../services/BankApiService", () => bankApi);

const ok = (data: unknown) => ({ isPending: false, isError: false, data, refetch: vi.fn() });
const failed = () => ({ isPending: false, isError: true, data: undefined, refetch: vi.fn() });
const question = (id: string) => ({
    id,
    stem: `Question ${id}`,
    unit: "Unit 1",
    format: "MCQ",
    scope: "core-scope",
});

describe("BankBrowser", () => {
    beforeEach(() => {
        rq.keys = [];
        rq.queries = {
            "bank-search": ok([question("1")]),
            "bank-coverage": ok({
                subject: "JAVA_PROGRAMMING",
                total: 2,
                units: ["Unit 1", "Unit 2"],
            }),
            "bank-question": ok({
                ...question("1"),
                subject: "JAVA_PROGRAMMING",
                topic: "Objects",
                id: "q-1",
            }),
        };
    });

    test("wires each query to the bank API", () => {
        render(<BankBrowser />);
        fireEvent.click(screen.getByRole("button", { name: /Show details/ }));
        rq.fns["bank-search"]();
        rq.fns["bank-coverage"]();
        rq.fns["bank-question"]();
        expect(bankApi.searchBank).toHaveBeenCalledWith({
            subject: "JAVA_PROGRAMMING",
            unit: undefined,
            scope: "everything",
        });
        expect(bankApi.getBankCoverage).toHaveBeenCalledWith("JAVA_PROGRAMMING");
        expect(bankApi.getBankQuestion).toHaveBeenCalledWith("1");
    });

    test("shows loading skeletons and coverage/search failures with retry", () => {
        rq.queries["bank-search"] = {
            isPending: true,
            isError: false,
            data: undefined,
            refetch: vi.fn(),
        };
        rq.queries["bank-coverage"] = failed();
        const { rerender } = render(<BankBrowser />);
        expect(screen.getByLabelText("Loading questions")).toBeInTheDocument();
        expect(screen.getByText("Coverage is unavailable right now.")).toBeInTheDocument();

        rq.queries["bank-search"] = failed();
        rerender(<BankBrowser />);
        expect(screen.getByText("Could not load questions")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(rq.queries["bank-search"].refetch).toHaveBeenCalledOnce();
    });

    test("shows an empty state when the bank has no matching questions", () => {
        rq.queries["bank-search"] = ok([]);
        render(<BankBrowser />);
        expect(screen.getByText("No questions here yet")).toBeInTheDocument();
    });

    test("maps the selected subject and scope to query keys and clears the unit filter", () => {
        render(<BankBrowser />);
        fireEvent.change(screen.getByLabelText("Subject"), { target: { value: "Chemistry" } });
        fireEvent.change(screen.getByLabelText("Which topics"), {
            target: { value: "core-scope" },
        });
        fireEvent.change(screen.getByLabelText("Filter by unit"), {
            target: { value: "  Unit 2 " },
        });
        expect(rq.keys).toContainEqual(["bank-search", "CHEMISTRY", "", "everything"]);
        expect(rq.keys).toContainEqual(["bank-search", "CHEMISTRY", "Unit 2", "core-scope"]);
        expect(rq.keys).toContainEqual(["bank-coverage", "CHEMISTRY"]);
        fireEvent.click(screen.getByRole("button", { name: "Clear" }));
        expect(rq.keys.filter((k) => k[0] === "bank-search").at(-1)).toEqual([
            "bank-search",
            "CHEMISTRY",
            "",
            "core-scope",
        ]);
    });

    test("lists questions with chips, expands details, retries and pages results", () => {
        rq.queries["bank-search"] = ok(
            Array.from({ length: 25 }, (_, i) => question(String(i + 1))),
        );
        render(<BankBrowser />);
        expect(screen.getByText("25 questions")).toBeInTheDocument();
        expect(screen.getAllByText("Core topics").length).toBeGreaterThan(0);
        expect(screen.getAllByRole("button", { name: /Show details/ })[0]).toHaveAttribute(
            "aria-expanded",
            "false",
        );
        expect(screen.getByRole("button", { name: "Show more (1 remaining)" })).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Show more (1 remaining)" }));
        expect(screen.getByText("Question 25")).toBeInTheDocument();

        fireEvent.click(screen.getAllByRole("button", { name: /Show details/ })[0]);
        expect(
            screen.getByText("JAVA_PROGRAMMING, Unit 1, Objects, Core topics"),
        ).toBeInTheDocument();
        expect(screen.getByText("q-1")).toBeInTheDocument();
    });

    test("shows pending, failed and successful detail panels", () => {
        rq.queries["bank-search"] = ok([question("1")]);
        rq.queries["bank-question"] = {
            isPending: true,
            isError: false,
            data: undefined,
            refetch: vi.fn(),
        };
        const { container, rerender } = render(<BankBrowser />);
        fireEvent.click(screen.getByRole("button", { name: /Show details/ }));
        expect(container.querySelector(".skeleton")).toBeInTheDocument();

        rq.queries["bank-question"] = failed();
        rerender(<BankBrowser />);
        expect(screen.getByText("Could not load that question")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(rq.queries["bank-question"].refetch).toHaveBeenCalledOnce();

        rq.queries["bank-question"] = ok({
            ...question("1"),
            subject: "JAVA_PROGRAMMING",
            topic: "Objects",
            scope: "unknown-scope",
            id: "q-1",
        });
        rerender(<BankBrowser />);
        expect(screen.getByText("Question details")).toBeInTheDocument();
        expect(
            screen.getByText("JAVA_PROGRAMMING, Unit 1, Objects, unknown-scope"),
        ).toBeInTheDocument();
    });

    test("uses the singular label for a single result and hides details on toggle", () => {
        rq.queries["bank-coverage"] = ok({ subject: "JAVA_PROGRAMMING", total: 1, units: [] });
        render(<BankBrowser />);
        expect(screen.getByText("1 question")).toBeInTheDocument();
        rq.fns["bank-question"]();
        expect(bankApi.getBankQuestion).toHaveBeenCalledWith("");

        const button = screen.getByRole("button", { name: /Show details/ });
        fireEvent.click(button);
        expect(screen.getByRole("button", { name: /Hide details/ })).toHaveAttribute(
            "aria-expanded",
            "true",
        );
        fireEvent.click(screen.getByRole("button", { name: /Hide details/ }));
        expect(screen.getByRole("button", { name: /Show details/ })).toHaveAttribute(
            "aria-expanded",
            "false",
        );
    });
});
