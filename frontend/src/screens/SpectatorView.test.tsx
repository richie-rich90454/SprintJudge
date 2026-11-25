import { beforeEach, describe, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { SpectatorView } from "./SpectatorView";

const mocks = vi.hoisted(() => ({
    pin: "1234" as string | undefined,
    connect: vi.fn(),
    join: vi.fn(),
    disconnect: vi.fn(),
    error: null as string | null,
    room: null as any,
    joinUrl: "http://localhost/j/1234",
}));

vi.mock("@tanstack/react-router", () => ({
    Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
    useParams: () => ({ pin: mocks.pin }),
}));
vi.mock("../stores/useGameStore", () => ({
    useGameStore: (selector: (s: Record<string, unknown>) => unknown) =>
        selector({ connect: mocks.connect, join: mocks.join, room: mocks.room, error: mocks.error }),
}));
vi.mock("../hooks/useJoinUrl", () => ({ useJoinUrl: () => mocks.joinUrl }));
vi.mock("../game/WebSocketService", () => ({ webSocketService: { disconnect: mocks.disconnect } }));
vi.mock("../components/host/HostStage", () => ({
    HostStage: ({ pin, joinUrl, projector }: { pin: string; joinUrl: string; projector?: boolean }) => (
        <div data-testid="stage">{`${pin}|${joinUrl}|${projector ? "projector" : "host"}`}</div>
    ),
}));
vi.mock("../components/AboutDialog", () => ({ AboutDialog: () => <div>About</div> }));

describe("SpectatorView", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mocks.pin = "1234";
        mocks.room = null;
        mocks.error = null;
    });

    test("does not connect without a PIN", () => {
        mocks.pin = undefined;
        render(<SpectatorView />);
        expect(mocks.connect).not.toHaveBeenCalled();
        expect(screen.getByText("Projector")).toBeInTheDocument();
    });

    test("shows a connecting message while the room is absent", () => {
        render(<SpectatorView />);
        expect(mocks.connect).toHaveBeenCalledWith(`ws://${window.location.host}/ws`);
        expect(mocks.join).toHaveBeenCalledWith("1234", "Projector", "spectator");
        expect(screen.getByText("Connecting to room 1234…")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "Back" })).toHaveAttribute("href", "/");
    });

    test("shows the connection error and hint when the join fails", () => {
        mocks.error = "Room not found";
        render(<SpectatorView />);
        expect(screen.getByText("Projector unavailable")).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent("Room not found");
        expect(screen.getByText(/check the PIN/i)).toBeInTheDocument();
    });

    test("renders a read-only host stage once the room arrives and disconnects on unmount", () => {
        mocks.room = { pin: "1234" };
        const { unmount } = render(<SpectatorView />);
        expect(screen.getByTestId("stage")).toHaveTextContent("1234|http://localhost/j/1234|projector");
        expect(screen.getByText("Projector view. Read only.")).toBeInTheDocument();
        unmount();
        expect(mocks.disconnect).toHaveBeenCalledOnce();
    });
});
