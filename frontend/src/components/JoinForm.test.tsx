import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";

const gameMock = vi.hoisted(() => ({
    state: {
        error: null as string | null,
        playerUuid: null as string | null,
        clearError: vi.fn(),
        connect: vi.fn(),
        join: vi.fn(),
    },
}));
const uiMock = vi.hoisted(() => ({ setPin: vi.fn() }));
const audioMock = vi.hoisted(() => ({ play: vi.fn() }));
const navigateMock = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({ useNavigate: () => navigateMock }));
vi.mock("../stores/useGameStore", () => ({
    useGameStore: (selector: (s: Record<string, unknown>) => unknown) => selector(gameMock.state),
}));
vi.mock("../stores/useUIStore", () => ({
    useUIStore: (selector?: (s: Record<string, unknown>) => unknown) =>
        selector ? selector(uiMock) : uiMock,
}));
vi.mock("../services/AudioEngine", () => ({ audio: audioMock }));

import { JoinForm } from "./JoinForm";

describe("JoinForm", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        gameMock.state.error = null;
        gameMock.state.playerUuid = null;
    });

    test("sanitizes the initial PIN and explains what is missing", () => {
        render(<JoinForm initialPin="a1b2c3d4e5f6g7" />);
        expect(screen.getByPlaceholderText("123456")).toHaveValue("123456");
        expect(screen.getByRole("status")).toHaveTextContent("Enter your name to join.");
        expect(screen.getByRole("button", { name: "Join game" })).toBeDisabled();
    });

    test("shows a partial-PIN hint and a server error", () => {
        gameMock.state.error = "Wrong name";
        render(<JoinForm initialPin="12" />);
        fireEvent.change(screen.getByPlaceholderText("Alice"), { target: { value: "Ada" } });
        expect(screen.getByRole("status")).toHaveTextContent(
            "Enter all 6 digits of the PIN (2/6).",
        );
        expect(screen.getByRole("alert")).toHaveTextContent("Wrong name");
    });

    test("submits a clean name and PIN and starts the session", () => {
        render(<JoinForm initialPin="123456" heading="Join" />);
        expect(screen.getByText("Join")).toBeInTheDocument();
        fireEvent.change(screen.getByPlaceholderText("Alice"), { target: { value: "  Ada  " } });
        fireEvent.click(screen.getByRole("button", { name: "Join game" }));

        expect(gameMock.state.clearError).toHaveBeenCalledOnce();
        expect(gameMock.state.connect).toHaveBeenCalledWith(
            expect.stringMatching(/^wss?:\/\/.*\/ws$/),
        );
        expect(gameMock.state.join).toHaveBeenCalledWith("123456", "Ada");
        expect(uiMock.setPin).toHaveBeenCalledWith("123456");
        expect(audioMock.play).toHaveBeenCalledWith("start");
    });

    test("uses wss on https and ignores an incomplete direct submit", () => {
        const realLocation = globalThis.location;
        vi.stubGlobal("location", { protocol: "https:", host: "example.com" });
        const incomplete = render(<JoinForm initialPin="123" />);
        fireEvent.change(incomplete.getByPlaceholderText("Alice"), { target: { value: "Ada" } });
        fireEvent.submit(incomplete.getByRole("button", { name: "Join game" }).closest("form")!);
        expect(gameMock.state.join).not.toHaveBeenCalled();
        incomplete.unmount();

        const complete = render(<JoinForm initialPin="123456" />);
        fireEvent.change(complete.getByPlaceholderText("Alice"), { target: { value: "Ada" } });
        fireEvent.click(complete.getByRole("button", { name: "Join game" }));
        expect(gameMock.state.connect).toHaveBeenCalledWith("wss://example.com/ws");
        vi.stubGlobal("location", realLocation);
    });

    test("a too-short PIN cannot be submitted", () => {
        render(<JoinForm initialPin="123" />);
        fireEvent.change(screen.getByPlaceholderText("Alice"), { target: { value: "Ada" } });
        fireEvent.click(screen.getByRole("button", { name: "Join game" }));
        expect(gameMock.state.join).not.toHaveBeenCalled();
    });
});
