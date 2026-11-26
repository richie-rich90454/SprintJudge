import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

const audioMock = vi.hoisted(() => ({ setMuted: vi.fn() }));
vi.mock("../services/AudioEngine", () => ({ audio: audioMock }));

import { SoundToggle } from "./SoundToggle";
import { useUIStore } from "../stores/useUIStore";

describe("SoundToggle", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        localStorage.removeItem("oq-sound");
        useUIStore.getState().setSound("on");
        vi.clearAllMocks();
    });

    afterEach(() => {
        localStorage.removeItem("oq-sound");
        useUIStore.getState().setSound("on");
    });

    test("on state exposes mute label/title and unmutes the engine", () => {
        render(<SoundToggle />);
        const button = screen.getByRole("button", { name: "Mute sounds" });
        expect(button).toHaveAttribute("title", "Sound: on");
        expect(button).toHaveAttribute("aria-pressed", "true");
        expect(audioMock.setMuted).toHaveBeenCalledWith(false);
    });

    test("clicking toggles to the off state and mutes the engine", () => {
        render(<SoundToggle className="custom" />);
        fireEvent.click(screen.getByRole("button", { name: "Mute sounds" }));

        const button = screen.getByRole("button", { name: "Unmute sounds" });
        expect(button).toHaveAttribute("title", "Sound: off");
        expect(button).toHaveAttribute("aria-pressed", "false");
        expect(button).toHaveClass("custom");
        expect(audioMock.setMuted).toHaveBeenLastCalledWith(true);
    });

    test("a throwing audio engine never breaks the switch", () => {
        audioMock.setMuted.mockImplementationOnce(() => {
            throw new Error("no AudioContext");
        });
        render(<SoundToggle />);
        expect(screen.getByRole("button", { name: "Mute sounds" })).toBeInTheDocument();
    });
});
