import { beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { SoloView } from "./SoloView";

const gameMock = vi.hoisted(() => ({
    state: {
        error: null as string | null,
        playerUuid: null as string | null,
        clearError: vi.fn(),
        connect: vi.fn(),
        join: vi.fn(),
    },
}));
const uiMock = vi.hoisted(() => ({
    state: { sound: "on", motion: "system", theme: "light", pin: null, setPin: vi.fn() },
}));
const navigate = vi.fn();

vi.mock("../stores/useGameStore", () => ({
    useGameStore: (selector: (s: Record<string, unknown>) => unknown) => selector(gameMock.state),
}));
vi.mock("../stores/useUIStore", () => ({
    useUIStore: (selector: (s: Record<string, unknown>) => unknown) => selector(uiMock.state),
}));
vi.mock("../services/AudioEngine", () => ({ audio: { play: vi.fn(), setMuted: vi.fn() } }));
vi.mock("@tanstack/react-router", () => ({
    Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
        <a href={to}>{children}</a>
    ),
    useNavigate: () => navigate,
}));

describe("SoloView", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        gameMock.state.error = null;
        gameMock.state.playerUuid = null;
    });

    test("renders the practice PIN form and a link to a fresh practice set", () => {
        render(<SoloView />);
        expect(screen.getByText("Enter your practice PIN")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "start a practice set" })).toHaveAttribute(
            "href",
            "/practice",
        );
        expect(screen.getByRole("button", { name: "Join game" })).toBeDisabled();
    });

    test("joins as a player once a six-digit PIN and a name are entered", () => {
        render(<SoloView />);
        fireEvent.change(screen.getByLabelText("Your name"), { target: { value: " Ada " } });
        fireEvent.change(screen.getByLabelText("Game PIN"), { target: { value: "12ab3456" } });
        fireEvent.click(screen.getByRole("button", { name: "Join game" }));

        expect(gameMock.state.clearError).toHaveBeenCalledOnce();
        expect(gameMock.state.connect).toHaveBeenCalledWith(`ws://${window.location.host}/ws`);
        expect(gameMock.state.join).toHaveBeenCalledWith("123456", "Ada");
        expect(uiMock.state.setPin).toHaveBeenCalledWith("123456");
    });

    test("navigates to the game after the server confirms the seat", () => {
        render(<SoloView />);
        gameMock.state.playerUuid = "uuid-1";
        fireEvent.change(screen.getByLabelText("Your name"), { target: { value: "Ada" } });
        fireEvent.change(screen.getByLabelText("Game PIN"), { target: { value: "123456" } });
        fireEvent.click(screen.getByRole("button", { name: "Join game" }));
        expect(navigate).toHaveBeenCalledWith({ to: "/play" });
    });
});
