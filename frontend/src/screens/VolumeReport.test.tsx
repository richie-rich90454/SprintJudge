import type { ReactNode } from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { VolumeReport } from "./VolumeReport";

const rq = vi.hoisted(() => ({ state: { isPending: false, isError: false, data: undefined as any, refetch: vi.fn() } }));

vi.mock("@tanstack/react-query", () => ({ useQuery: () => rq.state }));
vi.mock("../components/Shell", () => ({
    Shell: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("../services/BankApiService", () => ({ getBankVolume: vi.fn() }));

describe("VolumeReport", () => {
    beforeEach(() => {
        rq.state = { isPending: false, isError: false, data: undefined, refetch: vi.fn() };
    });

    test("shows a loading state", () => {
        rq.state = { isPending: true, isError: false, data: undefined, refetch: vi.fn() };
        render(<VolumeReport />);
        expect(screen.getByLabelText("Loading question counts")).toBeInTheDocument();
    });

    test("retries a failed count fetch", () => {
        rq.state = { isPending: false, isError: true, data: undefined, refetch: vi.fn() };
        render(<VolumeReport />);
        expect(screen.getByText("Could not load question counts")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(rq.state.refetch).toHaveBeenCalledOnce();
    });

    test("shows an empty state for an empty response", () => {
        rq.state = { isPending: false, isError: false, data: {}, refetch: vi.fn() };
        render(<VolumeReport />);
        expect(screen.getByText("No subjects to show yet")).toBeInTheDocument();
    });

    test("maps known codes, sorts rows, totals counts and marks readiness", () => {
        rq.state = {
            isPending: false,
            isError: false,
            refetch: vi.fn(),
            data: { CHEMISTRY: 3000, CUSTOM_CODE: 1, ALGEBRA_I: 2999 },
        };
        const { container } = render(<VolumeReport />);
        expect(container.querySelector('[aria-live="polite"]')).toHaveTextContent(
            "6,000 questions in total, 1 of 3 subjects at 3,000",
        );
        const names = screen.getAllByRole("row").slice(1).map((row) => (row as HTMLTableRowElement).cells[0].textContent);
        expect(names).toEqual(["Algebra I", "Chemistry", "CUSTOM_CODE"]);
        expect((screen.getAllByRole("row")[2] as HTMLTableRowElement).cells[1]).toHaveTextContent("3,000");
        expect(screen.getByText("2,999")).toBeInTheDocument();
        expect(screen.getByText("Ready")).toBeInTheDocument();
        expect(screen.getAllByText("Still adding")).toHaveLength(2);
    });
});
