import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { AboutDialog } from "./AboutDialog";

describe("AboutDialog", () => {
    test("opens the about panel and closes it again", () => {
        render(<AboutDialog />);
        expect(screen.queryByText("SprintJudge 1.0.0")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "About SprintJudge" }));
        expect(screen.getByText("SprintJudge 1.0.0")).toBeInTheDocument();
        expect(screen.getByText("Self-hosted classroom quiz and code judge.")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Close dialog" }));
        expect(screen.queryByText("SprintJudge 1.0.0")).not.toBeInTheDocument();
    });

    test("returns focus to the opener when the dialog closes", async () => {
        render(<AboutDialog />);
        const trigger = screen.getByRole("button", { name: "About SprintJudge" });
        trigger.focus();
        fireEvent.click(trigger);
        fireEvent.click(screen.getByRole("button", { name: "Close dialog" }));
        await waitFor(() => expect(trigger).toHaveFocus());
    });
});
