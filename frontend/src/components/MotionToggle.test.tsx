import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { MotionToggle } from "./MotionToggle";
import { useUIStore } from "../stores/useUIStore";

describe("MotionToggle", () => {
    beforeEach(() => {
        localStorage.removeItem("oq-motion");
        useUIStore.getState().setMotion("system");
    });
    afterEach(() => localStorage.removeItem("oq-motion"));

    test("cycles system -> full -> reduced -> system", () => {
        render(<MotionToggle className="header-icon" />);
        const button = screen.getByRole("button", { name: "Motion: system. Activate for full." });
        expect(button).toHaveAttribute("title", "Motion: system");
        expect(button).toHaveAttribute("aria-pressed", "false");
        expect(button).toHaveClass("header-icon");

        fireEvent.click(button);
        fireEvent.click(
            screen.getByRole("button", { name: "Motion: full. Activate for reduced." }),
        );
        const reduced = screen.getByRole("button", {
            name: "Motion: reduced. Activate for system.",
        });
        expect(reduced).toHaveAttribute("aria-pressed", "true");

        fireEvent.click(reduced);
        expect(
            screen.getByRole("button", { name: "Motion: system. Activate for full." }),
        ).toBeInTheDocument();
    });
});
