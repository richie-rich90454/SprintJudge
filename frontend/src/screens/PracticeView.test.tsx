import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PracticeView } from "./PracticeView";

const rq = vi.hoisted(() => ({
    state: { isPending: false, isError: false, data: undefined as any, refetch: vi.fn() },
}));
const mocks = vi.hoisted(() => ({
    startPractice: vi.fn(),
    connect: vi.fn(),
    join: vi.fn(),
    setPin: vi.fn(),
    play: vi.fn(),
    navigate: vi.fn(),
    listBanks: vi.fn(),
}));

const query = vi.hoisted(() => ({ fn: undefined as (() => unknown) | undefined }));
vi.mock("@tanstack/react-query", () => ({
    useQuery: (options: { queryFn: () => unknown }) => {
        query.fn = options.queryFn;
        return rq.state;
    },
}));
vi.mock("../components/Shell", () => ({
    Shell: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@tanstack/react-router", () => ({ useNavigate: () => mocks.navigate }));
vi.mock("../services/AdminApiService", () => ({
    adminApi: { startPractice: mocks.startPractice, listBanks: mocks.listBanks },
}));
vi.mock("../services/AudioEngine", () => ({ audio: { play: mocks.play, setMuted: vi.fn() } }));
vi.mock("../stores/useGameStore", () => ({
    useGameStore: (selector: (s: Record<string, unknown>) => unknown) =>
        selector({ connect: mocks.connect, join: mocks.join }),
}));
vi.mock("../stores/useUIStore", () => ({
    useUIStore: (selector: (s: Record<string, unknown>) => unknown) =>
        selector({ setPin: mocks.setPin }),
}));

const banks = [
    { id: "b1", title: "Java Programming", description: "Core syntax" },
    { id: "b2", title: "Biology", description: null },
];

describe("PracticeView", () => {
    const originalSearch = window.location.search;

    beforeEach(() => {
        vi.clearAllMocks();
        window.history.replaceState({}, "", "/practice");
        rq.state = { isPending: false, isError: false, data: banks, refetch: vi.fn() };
        mocks.startPractice.mockResolvedValue({ pinCode: "123456" });
    });

    afterEach(() => {
        window.history.replaceState({}, "", originalSearch || "/practice");
        vi.unstubAllGlobals();
    });

    test("fetches the bank list through the configured query", () => {
        render(<PracticeView />);
        query.fn?.();
        expect(mocks.listBanks).toHaveBeenCalledOnce();
    });

    test("renders loading and retry states", () => {
        rq.state = { isPending: true, isError: false, data: undefined, refetch: vi.fn() };
        const { rerender } = render(<PracticeView />);
        expect(screen.getByLabelText("Loading banks")).toBeInTheDocument();

        rq.state = { isPending: false, isError: true, data: undefined, refetch: vi.fn() };
        rerender(<PracticeView />);
        expect(screen.getByText("Could not load banks")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(rq.state.refetch).toHaveBeenCalledOnce();
    });

    test("renders an empty bank list and a no-search-matches message", () => {
        rq.state = { isPending: false, isError: false, data: [], refetch: vi.fn() };
        const { rerender } = render(<PracticeView />);
        expect(screen.getByText("No question banks yet")).toBeInTheDocument();

        rq.state = { isPending: false, isError: false, data: banks, refetch: vi.fn() };
        rerender(<PracticeView />);
        fireEvent.change(screen.getByRole("textbox", { name: "Search question banks" }), {
            target: { value: "nothing" },
        });
        expect(screen.getByText("No banks match that search.")).toBeInTheDocument();
    });

    test("filters banks by title and description and pages results", () => {
        rq.state = {
            isPending: false,
            isError: false,
            refetch: vi.fn(),
            data: Array.from({ length: 21 }, (_, i) => ({
                id: `b${i}`,
                title: `Bank ${i}`,
                description: i === 20 ? "described" : null,
            })),
        };
        render(<PracticeView />);
        expect(screen.getByRole("button", { name: "Show more (1 remaining)" })).toBeInTheDocument();
        expect(screen.queryByLabelText("Bank 20")).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Show more (1 remaining)" }));
        expect(screen.getByText("described")).toBeInTheDocument();

        fireEvent.change(screen.getByRole("textbox", { name: "Search question banks" }), {
            target: { value: "CORE" },
        });
        expect(screen.queryByText("Bank 0")).not.toBeInTheDocument();
    });

    test("respects a bank preselected by the query string", () => {
        window.history.replaceState({}, "", "/practice?bank=b2");
        render(<PracticeView />);
        expect(screen.getByRole("radio", { name: /Biology/ })).toBeChecked();
        expect(screen.getByRole("button", { name: "Start practicing" })).toBeDisabled();
    });

    test("starts a practice room, connects, joins and navigates", async () => {
        render(<PracticeView />);
        fireEvent.click(screen.getByRole("radio", { name: /Java Programming/ }));
        fireEvent.change(screen.getByLabelText("Your name"), { target: { value: " Ada " } });
        const button = screen.getByRole("button", { name: "Start practicing" });
        expect(button).toBeEnabled();
        fireEvent.click(button);

        expect(screen.getByRole("button", { name: "Starting…" })).toBeDisabled();
        await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith({ to: "/play" }));
        expect(mocks.startPractice).toHaveBeenCalledWith("b1");
        expect(mocks.connect).toHaveBeenCalledWith(`ws://${window.location.host}/ws`);
        expect(mocks.join).toHaveBeenCalledWith("123456", "Ada");
        expect(mocks.setPin).toHaveBeenCalledWith("123456");
        expect(mocks.play).toHaveBeenCalledWith("start");
    });

    test("ignores a direct submit without a bank and connects over wss on https", async () => {
        const incomplete = render(<PracticeView />);
        fireEvent.change(screen.getByLabelText("Your name"), { target: { value: "Ada" } });
        fireEvent.submit(screen.getByRole("button", { name: "Start practicing" }).closest("form")!);
        expect(mocks.startPractice).not.toHaveBeenCalled();
        incomplete.unmount();

        vi.stubGlobal("location", { protocol: "https:", host: "example.com", search: "" });
        render(<PracticeView />);
        fireEvent.click(screen.getByRole("radio", { name: /Java Programming/ }));
        fireEvent.change(screen.getByLabelText("Your name"), { target: { value: "Ada" } });
        fireEvent.click(screen.getByRole("button", { name: "Start practicing" }));
        await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith({ to: "/play" }));
        expect(mocks.connect).toHaveBeenCalledWith("wss://example.com/ws");
    });

    test("shows an error and returns to idle when starting practice fails", async () => {
        mocks.startPractice.mockRejectedValue(new Error("offline"));
        render(<PracticeView />);
        fireEvent.click(screen.getByRole("radio", { name: /Biology/ }));
        fireEvent.change(screen.getByLabelText("Your name"), { target: { value: "Ada" } });
        fireEvent.click(screen.getByRole("button", { name: "Start practicing" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(
            "Could not start a practice set",
        );
        expect(screen.getByRole("button", { name: "Start practicing" })).toBeEnabled();
        expect(mocks.navigate).not.toHaveBeenCalled();
    });
});
