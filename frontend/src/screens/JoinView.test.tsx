import { describe, test, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { JoinView } from "./JoinView";

const gameMock = vi.hoisted(() => ({
    state: { error: null } as Record<string, unknown>,
}));

const uiMock = vi.hoisted(() => ({
    state: {
        sound: "on",
        motion: "system",
        pin: null,
        theme: "light",
        toggleSound: vi.fn(),
        setMotion: vi.fn(),
        setPin: vi.fn(),
    } as Record<string, unknown>,
}));

const paramsMock = vi.hoisted(() => ({ pin: undefined as string | undefined }));

vi.mock("../stores/useGameStore", () => ({
    useGameStore: (selector: (s: Record<string, unknown>) => unknown) =>
        selector(gameMock.state),
}));

vi.mock("../stores/useUIStore", () => ({
    useUIStore: (selector: (s: Record<string, unknown>) => unknown) =>
        selector(uiMock.state),
}));

vi.mock("../services/AudioEngine", () => ({
    audio: { play: vi.fn(), setMuted: vi.fn() },
}));

vi.mock("@tanstack/react-router", () => ({
    Link: ({ children, to }: { children: unknown; to: string }) => (
        <a href={to}>{children as any}</a>
    ),
    useParams: () => paramsMock,
    useNavigate: () => vi.fn(),
}));

describe("JoinView", () => {
    beforeEach(() => {
        gameMock.state.error = null;
        paramsMock.pin = undefined;
    });

    test("renders join form", () => {
        render(<JoinView />);
        expect(screen.getByText("Join a live game")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: /Back/i })).toHaveAttribute("href", "/");
    });

    test("shows invited chip when pin provided", () => {
        paramsMock.pin = "1234";
        render(<JoinView />);
        expect(screen.getByText(/You've been invited to game 1234/)).toBeInTheDocument();
    });
});