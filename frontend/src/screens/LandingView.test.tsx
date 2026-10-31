import { describe, test, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { LandingView } from "./LandingView";

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
    } as Record<string, unknown>,
}));

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
    useNavigate: () => vi.fn(),
}));

describe("LandingView", () => {
    beforeEach(() => {
        gameMock.state.error = null;
    });

    test("renders wordmark and join card", () => {
        render(<LandingView />);
        expect(screen.getByText(/Sprint/)).toBeInTheDocument();
        expect(screen.getByText("Join a live game")).toBeInTheDocument();
    });

    test("links to practice and explore", () => {
        render(<LandingView />);
        expect(screen.getByRole("link", { name: /Practice solo/i })).toHaveAttribute(
            "href",
            "/practice",
        );
        expect(screen.getByRole("link", { name: /Browse quizzes/i })).toHaveAttribute(
            "href",
            "/explore",
        );
    });
});