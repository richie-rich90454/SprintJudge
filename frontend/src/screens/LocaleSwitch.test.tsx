import { describe, test, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { I18nProvider, useI18n } from "../i18n";
import { LandingView } from "./LandingView";
import { JoinView } from "./JoinView";

const gameMock = vi.hoisted(() => ({ state: { error: null } as Record<string, unknown> }));
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
    useGameStore: (selector: (s: Record<string, unknown>) => unknown) => selector(gameMock.state),
}));
vi.mock("../stores/useUIStore", () => ({
    useUIStore: (selector: (s: Record<string, unknown>) => unknown) => selector(uiMock.state),
}));
vi.mock("../services/AudioEngine", () => ({ audio: { play: vi.fn(), setMuted: vi.fn() } }));
vi.mock("@tanstack/react-router", () => ({
    Link: ({ children, to }: { children: unknown; to: string }) => (
        <a href={to}>{children as any}</a>
    ),
    useParams: () => paramsMock,
    useNavigate: () => vi.fn(),
}));

function LocaleHarness({ children }: { children: ReactNode }) {
    const { setLocale } = useI18n();
    return (
        <>
            <button type="button" onClick={() => setLocale("ar")}>
                Switch to Arabic
            </button>
            {children}
        </>
    );
}

function renderInProvider(ui: ReactNode) {
    return render(
        <I18nProvider>
            <LocaleHarness>{ui}</LocaleHarness>
        </I18nProvider>,
    );
}

describe("screen locale switching", () => {
    beforeEach(() => {
        gameMock.state.error = null;
        paramsMock.pin = undefined;
        localStorage.clear();
    });

    test("LandingView switches its join copy and direction to Arabic", () => {
        renderInProvider(<LandingView />);
        expect(screen.getByText("Join a live game")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: /Practice solo/i })).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Switch to Arabic" }));

        expect(screen.getByText("الانضمام إلى لعبة مباشرة")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: /تدريب فردي/ })).toBeInTheDocument();
        expect(document.documentElement.dir).toBe("rtl");
    });

    test("JoinView switches labels and the interpolated invite message", () => {
        paramsMock.pin = "123456";
        renderInProvider(<JoinView />);
        expect(screen.getByText("Join a live game")).toBeInTheDocument();
        expect(screen.getByText(/You've been invited to game 123456/)).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Switch to Arabic" }));

        expect(screen.getByText("الانضمام إلى لعبة مباشرة")).toBeInTheDocument();
        expect(screen.getByText(/تمت دعوتك إلى اللعبة 123456/)).toBeInTheDocument();
        expect(screen.getByRole("link", { name: /رجوع/ })).toBeInTheDocument();
    });
});
