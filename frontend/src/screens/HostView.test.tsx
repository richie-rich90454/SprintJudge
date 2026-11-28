import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { HostView } from "./HostView";

const m = vi.hoisted(() => {
    const state: Record<string, any> = {
        pin: null,
        connect: vi.fn(),
        join: vi.fn(),
        room: null,
        status: "LOBBY",
        error: null,
        teams: [],
        bracket: null,
        champion: null,
        leaderboard: [],
        gameMode: "CLASSIC",
        requestTeams: vi.fn(),
        requestBracket: vi.fn(),
        hostCommand: vi.fn(),
        extendTimer: vi.fn(),
        kick: vi.fn(),
    };
    return {
        state,
        search: { pin: undefined as string | undefined },
        messageHandler: undefined as ((msg: any) => void) | undefined,
        unsubscribe: vi.fn(),
        disconnect: vi.fn(),
        send: vi.fn(),
        joinUrl: "http://localhost/j/1234",
    };
});

vi.mock("@tanstack/react-router", () => ({
    Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
        <a href={to}>{children}</a>
    ),
    useSearch: () => m.search,
}));
vi.mock("../stores/useGameStore", () => ({
    useGameStore: (selector: (s: Record<string, any>) => unknown) => selector(m.state),
}));
vi.mock("../stores/useUIStore", () => ({
    useUIStore: (selector: (s: Record<string, any>) => unknown) => selector({ pin: m.state.pin }),
}));
vi.mock("../hooks/useJoinUrl", () => ({ useJoinUrl: () => m.joinUrl }));
vi.mock("../game/WebSocketService", () => ({
    webSocketService: {
        disconnect: m.disconnect,
        send: m.send,
        onMessage: () => ({
            subscribe: (handler: (msg: any) => void) => {
                m.messageHandler = handler;
                return { unsubscribe: m.unsubscribe };
            },
        }),
    },
}));
vi.mock("../components/host/HostStage", () => ({
    HostStage: ({ pin }: { pin: string }) => <div data-testid="stage">Stage {pin}</div>,
}));
vi.mock("../components/AboutDialog", () => ({ AboutDialog: () => <div>About</div> }));
vi.mock("../components/SoundToggle", () => ({ SoundToggle: () => <div>Sound</div> }));
vi.mock("../components/MusicToggle", () => ({ MusicToggle: () => <div>Music</div> }));
vi.mock("../components/MotionToggle", () => ({ MotionToggle: () => <div>Motion</div> }));
vi.mock("../components/host/StageTheme", () => ({ StageThemePicker: () => <div>Theme</div> }));
vi.mock("../components/FairnessControls", () => ({
    FairnessControls: () => <div>Fairness</div>,
    readFairness: () => ({ shuffleOptions: true, shuffleQuestions: false }),
}));
vi.mock("../hooks/useVirtualWindow", () => ({
    useVirtualWindow: (_count: number, _rowH: number, _over: number, _maxH: number) => ({
        ref: vi.fn(),
        start: 0,
        end: 100,
    }),
}));
vi.mock("../motion/useStageMotion", () => ({
    useFlipList: () => vi.fn(),
}));

const player = (uuid: string, name: string, score: number) => ({ uuid, name, score });

describe("HostView", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        m.messageHandler = undefined;
        m.search = { pin: undefined };
        Object.assign(m.state, {
            pin: null,
            room: null,
            status: "LOBBY",
            error: null,
            teams: [],
            bracket: null,
            champion: null,
            leaderboard: [],
            gameMode: "CLASSIC",
        });
        Object.defineProperty(navigator, "clipboard", {
            configurable: true,
            value: { writeText: vi.fn().mockResolvedValue(undefined) },
        });
        Object.defineProperty(document, "fullscreenElement", { configurable: true, value: null });
        document.documentElement.requestFullscreen = vi.fn().mockResolvedValue(undefined);
        document.exitFullscreen = vi.fn().mockResolvedValue(undefined);
    });

    afterEach(() => vi.unstubAllGlobals());

    test("prompts for a game created in the admin dashboard when no PIN is available", () => {
        render(<HostView />);
        expect(screen.getByRole("link", { name: "Admin dashboard" })).toHaveAttribute(
            "href",
            "/admin",
        );
        expect(m.state.connect).not.toHaveBeenCalled();
    });

    test("uses wss on https and shows the game-over panel when the store status is ENDED", () => {
        m.search = { pin: "1234" };
        m.state.status = "ENDED";
        vi.stubGlobal("location", { protocol: "https:", host: "example.com" });
        render(<HostView />);
        expect(m.state.connect).toHaveBeenCalledWith("wss://example.com/ws");
        expect(screen.getByRole("link", { name: "View results" })).toHaveAttribute(
            "href",
            "/results",
        );
    });

    test("connects with a PIN from search, shows loading and reports errors", () => {
        m.search = { pin: "1234" };
        m.state.error = "offline";
        const { unmount } = render(<HostView />);
        expect(m.state.connect).toHaveBeenCalledWith(`ws://${window.location.host}/ws`);
        expect(m.state.join).toHaveBeenCalledWith("1234", "Host", "host");
        expect(screen.getByLabelText("Loading room")).toBeInTheDocument();
        expect(screen.getAllByRole("alert")[0]).toHaveTextContent("Connection error: offline");
        unmount();
        expect(m.disconnect).toHaveBeenCalledOnce();
    });

    test("copies the join link through the clipboard and reports success", async () => {
        m.search = { pin: "1234" };
        render(<HostView />);
        expect(screen.getByText(m.joinUrl)).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Copy join link" }));
        expect(navigator.clipboard.writeText).toHaveBeenCalledWith(m.joinUrl);
        expect(await screen.findByRole("button", { name: "Copied" })).toBeInTheDocument();
    });

    test("falls back to execCommand when the clipboard API is unavailable and reports failure", () => {
        m.search = { pin: "1234" };
        Object.defineProperty(navigator, "clipboard", { configurable: true, value: undefined });
        document.execCommand = vi.fn(() => false);
        render(<HostView />);
        fireEvent.click(screen.getByRole("button", { name: "Copy join link" }));
        expect(document.execCommand).toHaveBeenCalledWith("copy");
        expect(screen.getByRole("button", { name: "Copy join link" })).toBeInTheDocument();
    });

    test("handles a rejected clipboard write and toggles full screen both ways", async () => {
        m.search = { pin: "1234" };
        (navigator.clipboard.writeText as ReturnType<typeof vi.fn>).mockRejectedValue(
            new Error("denied"),
        );
        const { rerender } = render(<HostView />);
        fireEvent.click(screen.getByRole("button", { name: "Copy join link" }));
        await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalledOnce());

        fireEvent.click(screen.getByRole("button", { name: "Full screen" }));
        expect(document.documentElement.requestFullscreen).toHaveBeenCalledOnce();
        Object.defineProperty(document, "fullscreenElement", {
            configurable: true,
            value: document.documentElement,
        });
        rerender(<HostView />);
        fireEvent.click(screen.getByRole("button", { name: "Full screen" }));
        expect(document.exitFullscreen).toHaveBeenCalledOnce();
    });

    test("renders the stage, team board, bracket and leaderboard with live controls", () => {
        m.search = { pin: "1234" };
        m.state.status = "ACTIVE";
        m.state.room = {
            status: "ACTIVE",
            gameMode: "BATTLE",
            players: [player("h", "Host", 0), player("p1", "Ada", 10), player("p2", "Bob", 20)],
        };
        m.state.teams = [
            { id: "t1", name: "Red", memberUuids: ["p1"], score: 100 },
            { id: "t2", name: "Blue", memberUuids: ["p1", "p2"], score: Number.NaN },
        ];
        m.state.bracket = {
            rounds: [
                {
                    number: 1,
                    pairs: [
                        { p1Uuid: "p1", p2Uuid: "p2", winnerUuid: "p1" },
                        { p1Uuid: "p1", p2Uuid: null, winnerUuid: "p1", bye: true },
                    ],
                },
            ],
        };
        m.state.champion = { championName: "Ada" };
        m.state.leaderboard = [
            { uuid: "p1", name: "Ada", score: 10, rank: 1 },
            { uuid: "p2", name: "Bob", score: 5, rank: 2 },
        ];
        render(<HostView />);

        expect(screen.getByTestId("stage")).toBeInTheDocument();
        expect(screen.getByText(/1 round/)).toBeInTheDocument();
        expect(screen.getByText("bye")).toBeInTheDocument();
        expect(screen.getByText("Champion: Ada")).toBeInTheDocument();
        expect(screen.getAllByText(/2 students/).length).toBeGreaterThan(0);
        expect(m.state.requestBracket).toHaveBeenCalled();

        fireEvent.click(screen.getByRole("button", { name: "Reveal answer" }));
        expect(m.state.hostCommand).toHaveBeenCalledWith("FORCE_SUBMIT");
        fireEvent.click(screen.getByRole("button", { name: "+30 seconds" }));
        expect(m.state.extendTimer).toHaveBeenCalledWith(30);
        fireEvent.click(screen.getByRole("button", { name: "Kick Ada" }));
        expect(m.state.kick).toHaveBeenCalledWith("p1");
    });

    test("renders the team board with singular and plural member counts", () => {
        m.search = { pin: "1234" };
        m.state.room = { status: "LOBBY", gameMode: "TEAM", players: [] };
        m.state.teams = [
            { id: "t1", name: "Red", memberUuids: ["p1"], score: 100 },
            { id: "t2", name: "Blue", memberUuids: ["p1", "p2"], score: Number.NaN },
        ];
        render(<HostView />);
        expect(screen.getByText(/2 teams/)).toBeInTheDocument();
        expect(screen.getByText(/1 member/)).toBeInTheDocument();
        expect(screen.getByText(/2 members/)).toBeInTheDocument();
        expect(screen.getByText("0")).toBeInTheDocument();
        expect(screen.getByText("100")).toBeInTheDocument();
    });

    test("team board uses the singular team label", () => {
        m.search = { pin: "1234" };
        m.state.room = { status: "LOBBY", gameMode: "TEAM", players: [] };
        m.state.teams = [{ id: "t1", name: "Red", memberUuids: [], score: 0 }];
        m.state.gameMode = "TEAM";
        m.state.error = "socket lost";
        render(<HostView />);
        expect(screen.getAllByText(/1 team/).length).toBeGreaterThan(0);
        expect(screen.getAllByText(/socket lost/).length).toBeGreaterThan(0);
    });

    test("starts a round with fairness settings and ignores a double click", () => {
        m.search = { pin: "1234" };
        m.state.room = {
            status: "LOBBY",
            gameMode: "BATTLE",
            players: [player("p1", "Ada", 0)],
        };
        m.state.status = "LOBBY";
        m.state.gameMode = "BATTLE";
        render(<HostView />);
        expect(screen.getAllByText(/1 student/).length).toBeGreaterThan(0);
        expect(screen.getByText("Awaiting start")).toBeInTheDocument();

        const start = screen.getByRole("button", { name: "Start round" });
        fireEvent.click(start);
        fireEvent.click(start);
        expect(m.state.hostCommand).toHaveBeenCalledTimes(1);
        expect(m.state.hostCommand).toHaveBeenCalledWith("NEXT_QUESTION", {
            shuffleOptions: true,
            shuffleQuestions: false,
        });
        fireEvent.click(screen.getByRole("button", { name: "Start battle" }));
        expect(m.send).toHaveBeenCalledWith({
            type: "START_BATTLE",
            shuffleOptions: true,
            shuffleQuestions: false,
        });
    });

    test("allows ending the game only after confirmation, and shows final standings", () => {
        m.search = { pin: "1234" };
        m.state.room = { status: "REVIEW", gameMode: "CLASSIC", players: [] };
        m.state.status = "REVIEW";
        const { rerender } = render(<HostView />);
        expect(screen.getByText("Reviewing answers")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Next question" })).toBeInTheDocument();
        expect(screen.getAllByText(/0 students/).length).toBeGreaterThan(0);

        fireEvent.click(screen.getByRole("button", { name: "End game" }));
        expect(screen.getByText("End the game?")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Keep playing" }));
        expect(m.state.hostCommand).not.toHaveBeenCalledWith("END_GAME");

        fireEvent.click(screen.getByRole("button", { name: "End game" }));
        const dialogButtons = screen.getAllByRole("button", { name: "End game" });
        fireEvent.click(dialogButtons[dialogButtons.length - 1]);
        expect(m.state.hostCommand).toHaveBeenCalledWith("END_GAME");

        m.state.room = { status: "ENDED", gameMode: "CLASSIC", players: [] };
        rerender(<HostView />);
        expect(screen.getByRole("link", { name: "View results" })).toHaveAttribute(
            "href",
            "/results",
        );
        expect(screen.queryByRole("button", { name: "End game" })).not.toBeInTheDocument();
    });

    test("handles team creation, loading, failures and empty states from socket messages", () => {
        m.search = { pin: "1234" };
        m.state.room = { status: "LOBBY", gameMode: "TEAM", players: [player("p1", "Ada", 0)] };
        m.state.gameMode = "TEAM";
        render(<HostView />);
        expect(screen.getAllByText(/No teams yet/).length).toBeGreaterThan(0);
        expect(screen.queryByText("Share the PIN to invite players.")).not.toBeInTheDocument();

        const input = screen.getByRole("textbox", { name: "Team name" });
        fireEvent.change(input, { target: { value: "  Red  " } });
        fireEvent.keyDown(input, { key: "Enter" });
        expect(m.send).toHaveBeenCalledWith({ type: "CREATE_TEAM", name: "Red" });

        act(() => m.messageHandler?.({ type: "TEAM_CREATED" }));
        expect(screen.getByText("Loading teams")).toBeInTheDocument();
        expect(m.send).toHaveBeenCalledWith({ type: "GET_TEAMS" });

        act(() =>
            m.messageHandler?.({
                type: "ERROR",
                message: "Too many teams",
            }),
        );
        expect(screen.getByText("Too many teams")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(screen.queryByText("Too many teams")).not.toBeInTheDocument();

        act(() =>
            m.messageHandler?.({
                type: "TEAM_LIST",
                teams: [{ id: "t1", name: "Red", memberUuids: [], score: 5 }],
            }),
        );
        expect(screen.getByText("0 members, 5 pts")).toBeInTheDocument();

        act(() => m.messageHandler?.({ type: "TEAM_JOINED" }));
        expect(m.send).toHaveBeenCalledTimes(4);
    });

    test("sorts room players into a leaderboard fallback and ignores empty team creation", () => {
        m.search = { pin: "1234" };
        m.state.room = {
            status: "LOBBY",
            gameMode: "CLASSIC",
            players: [player("h", "Host", 999), player("p1", "Ada", 5), player("p2", "Bob", 20)],
        };
        m.state.gameMode = "CLASSIC";
        render(<HostView />);
        const names = screen.getAllByText(/Ada|Bob/).map((el) => el.textContent);
        expect(names.indexOf("Bob")).toBeLessThan(names.indexOf("Ada"));
        expect(screen.queryByText("999")).not.toBeInTheDocument();
    });

    test("empty team name does not send a message", () => {
        m.search = { pin: "1234" };
        m.state.room = { status: "LOBBY", gameMode: "TEAM", players: [] };
        m.state.gameMode = "TEAM";
        render(<HostView />);
        const input = screen.getByRole("textbox", { name: "Team name" });
        fireEvent.keyDown(input, { key: "Enter" });
        expect(m.send).not.toHaveBeenCalled();
    });

    test("advance lock releases after 600ms so the next click works", () => {
        vi.useFakeTimers();
        try {
            m.search = { pin: "1234" };
            m.state.room = { status: "REVIEW", gameMode: "CLASSIC", players: [] };
            m.state.status = "REVIEW";
            render(<HostView />);
            const next = screen.getByRole("button", { name: "Next question" });
            fireEvent.click(next);
            fireEvent.click(next);
            expect(m.state.hostCommand).toHaveBeenCalledTimes(1);
            act(() => vi.advanceTimersByTime(600));
            fireEvent.click(next);
            expect(m.state.hostCommand).toHaveBeenCalledTimes(2);
        } finally {
            vi.useRealTimers();
        }
    });

    test("copy badge expires and is reset by a second copy", async () => {
        vi.useFakeTimers();
        try {
            m.search = { pin: "1234" };
            render(<HostView />);
            const copy = screen.getByRole("button", { name: "Copy join link" });
            fireEvent.click(copy);
            await act(async () => {});
            expect(screen.getByRole("button", { name: "Copied" })).toBeInTheDocument();
            fireEvent.click(screen.getByRole("button", { name: "Copied" }));
            await act(async () => {});
            act(() => vi.advanceTimersByTime(2000));
            expect(screen.getByRole("button", { name: "Copy join link" })).toBeInTheDocument();
        } finally {
            vi.useRealTimers();
        }
    });

    test("clipboard and full screen rejections are swallowed", async () => {
        m.search = { pin: "1234" };
        Object.defineProperty(navigator, "clipboard", { configurable: true, value: undefined });
        document.execCommand = vi.fn(() => {
            throw new Error("denied");
        });
        document.documentElement.requestFullscreen = vi.fn().mockRejectedValue(new Error("denied"));
        document.exitFullscreen = vi.fn().mockRejectedValue(new Error("denied"));
        render(<HostView />);
        fireEvent.click(screen.getByRole("button", { name: "Copy join link" }));
        expect(screen.getByRole("button", { name: "Copy join link" })).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Full screen" }));
        Object.defineProperty(document, "fullscreenElement", {
            configurable: true,
            value: document.documentElement,
        });
        fireEvent.click(screen.getByRole("button", { name: "Full screen" }));
        expect(document.exitFullscreen).toHaveBeenCalled();
        await Promise.resolve();
    });

    test("bracket uses player fallbacks, winner emphasis, and a singular team count", () => {
        m.search = { pin: "1234" };
        m.state.room = {
            status: "ACTIVE",
            gameMode: "BATTLE",
            players: [player("u2", "Grace", 0)],
        };
        m.state.teams = [{ id: "t1", name: "Red", memberUuids: ["u1"], score: 5 }];
        m.state.error = "socket lost";
        m.state.bracket = {
            rounds: [
                {
                    number: 1,
                    pairs: [
                        { p1Uuid: "unknown", p2Uuid: "u2", winnerUuid: "u2", bye: false },
                        { p1Uuid: null, p2Uuid: null, winnerUuid: null, bye: true },
                    ],
                },
            ],
        };
        render(<HostView />);
        expect(screen.getByText("Player")).toBeInTheDocument();
        const bold = Array.from(document.querySelectorAll(".font-bold")).map(
            (el) => el.textContent,
        );
        expect(bold).toContain("Grace");
        expect(screen.getAllByText(/socket lost/).length).toBeGreaterThanOrEqual(1);
    });

    test("leaderboard falls back to an empty player list and tolerates missing scores", () => {
        m.search = { pin: "1234" };
        m.state.room = { status: "LOBBY", gameMode: "CLASSIC" };
        m.state.leaderboard = [];
        const empty = render(<HostView />);
        expect(screen.getByText("No players yet - share the PIN.")).toBeInTheDocument();
        empty.unmount();

        m.state.leaderboard = [
            { uuid: "p4", name: "Fourth", rank: 4 },
            { uuid: "p5", name: "Fifth", rank: 5, score: null },
        ];
        render(<HostView />);
        expect(screen.getByText("Fourth")).toBeInTheDocument();
        expect(screen.getAllByText("0").length).toBeGreaterThanOrEqual(1);
    });

    test("team and error socket messages tolerate missing payloads", () => {
        m.search = { pin: "1234" };
        m.state.room = { status: "LOBBY", gameMode: "TEAM", players: [] };
        m.state.gameMode = "TEAM";
        render(<HostView />);
        act(() => m.messageHandler?.({ type: "TEAM_LIST" }));
        expect(screen.getAllByText(/No teams yet/).length).toBeGreaterThan(0);
        act(() => m.messageHandler?.({ type: "TEAM_CREATED" }));
        act(() => m.messageHandler?.({ type: "ERROR" }));
        expect(screen.getAllByText(/Failed to load teams/).length).toBeGreaterThanOrEqual(1);
    });

    test("renders bracket and leaderboard placeholders without data", () => {
        m.search = { pin: "1234" };
        m.state.room = { status: "LOBBY", gameMode: "BATTLE", players: [player("h", "Host", 0)] };
        render(<HostView />);
        expect(
            screen.getByText("Start the battle to build the first bracket."),
        ).toBeInTheDocument();
        expect(screen.getByText("0 rounds")).toBeInTheDocument();
        expect(screen.getByText("No players yet - share the PIN.")).toBeInTheDocument();
        expect(screen.getAllByText(/0 students/).length).toBeGreaterThan(0);
    });
});
