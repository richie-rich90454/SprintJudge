import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

const sw = vi.hoisted(() => {
    let listener: ((status: string) => void) | undefined;
    const unsubscribe = vi.fn();
    return {
        subscribe: vi.fn((cb: (status: string) => void) => {
            listener = cb;
            return { unsubscribe };
        }),
        emit: (status: string) => listener?.(status),
        unsubscribe,
    };
});
const linkMock = vi.hoisted(() => vi.fn(() => null));

vi.mock("../game/WebSocketService", () => ({
    webSocketService: { onStatus: () => ({ subscribe: sw.subscribe }) },
}));
vi.mock("@tanstack/react-router", () => ({ Link: linkMock }));
vi.mock("./LogoMark", () => ({ LogoMark: () => null }));
vi.mock("./SoundToggle", () => ({ SoundToggle: () => null }));
vi.mock("./MotionToggle", () => ({ MotionToggle: () => null }));
vi.mock("./AboutDialog", () => ({ AboutDialog: () => null }));

import { Shell } from "./Shell";

describe("Shell", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        localStorage.clear();
    });

    afterEach(() => localStorage.clear());

    test("renders the home link and an offline chip until the socket opens", () => {
        const view = render(<Shell>content</Shell>);
        expect(linkMock).toHaveBeenCalledWith(
            expect.objectContaining({ to: "/", "aria-label": "SprintJudge home" }),
            undefined,
        );
        expect(screen.getByText("content")).toBeInTheDocument();
        const chip = screen.getByText("Offline");
        expect(chip).toBeInTheDocument();
        expect(chip).toHaveAttribute("title", "Not connected");

        act(() => sw.emit("open"));
        const live = screen.getByText("Live");
        expect(live).toBeInTheDocument();
        expect(live).toHaveAttribute("title", "Connected");

        view.unmount();
        expect(sw.unsubscribe).toHaveBeenCalledOnce();
    });

    test("minimal mode hides the connection chip", () => {
        render(<Shell minimal>minimal content</Shell>);
        expect(screen.queryByText("Offline")).not.toBeInTheDocument();
        expect(screen.queryByText("Live")).not.toBeInTheDocument();
        expect(screen.getByText("minimal content")).toBeInTheDocument();
    });

    test("non-open statuses keep the offline chip", () => {
        render(<Shell>content</Shell>);
        act(() => sw.emit("closed"));
        expect(screen.getByText("Offline")).toBeInTheDocument();
        act(() => sw.emit("connecting"));
        expect(screen.getByText("Offline")).toBeInTheDocument();
    });
});
