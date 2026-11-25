import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

const audio = vi.hoisted(() => ({
    resume: vi.fn(),
    startMusic: vi.fn(),
    stopMusic: vi.fn(),
}));
vi.mock("../services/AudioEngine", () => ({ audio }));

import { MusicToggle } from "./MusicToggle";
import { useUIStore } from "../stores/useUIStore";

describe("MusicToggle", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        localStorage.removeItem("oq-music");
        useUIStore.getState().setSound("on");
    });

    afterEach(() => {
        localStorage.removeItem("oq-music");
    });

    test("resumes persisted music on mount and stops it on unmount", () => {
        localStorage.setItem("oq-music", "on");
        const view = render(<MusicToggle />);

        expect(audio.resume).toHaveBeenCalledTimes(1);
        expect(audio.startMusic).toHaveBeenCalledTimes(1);

        view.unmount();
        expect(audio.stopMusic).toHaveBeenCalledTimes(1);
    });

    test("does not start while the master sound switch is off, but follows it later", () => {
        localStorage.setItem("oq-music", "on");
        useUIStore.getState().setSound("off");
        render(<MusicToggle />);

        expect(audio.startMusic).not.toHaveBeenCalled();

        act(() => useUIStore.getState().setSound("on"));
        expect(audio.startMusic).toHaveBeenCalledTimes(1);
    });

    test("turning music off stops it; turning it on starts it", () => {
        render(<MusicToggle />);
        fireEvent.click(screen.getByRole("button", { name: "Turn background music on" }));

        expect(audio.startMusic).toHaveBeenCalledTimes(1);
        expect(localStorage.getItem("oq-music")).toBe("on");

        fireEvent.click(screen.getByRole("button", { name: "Turn background music off" }));
        expect(audio.stopMusic).toHaveBeenCalledTimes(1);
        expect(localStorage.getItem("oq-music")).toBe("off");
    });
});
