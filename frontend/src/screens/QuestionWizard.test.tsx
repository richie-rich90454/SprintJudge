import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { QuestionWizard } from "./QuestionWizard";
import { useAdminStore } from "../stores/useAdminStore";

describe("QuestionWizard step focus", () => {
    beforeEach(() => {
        useAdminStore.setState({
            wizardStep: "statement",
            wizardType: "MCQ",
            draft: { title: "A question", questionType: "MCQ", config: {} },
        });
    });

    afterEach(() => {
        useAdminStore.setState({ wizardStep: "type", wizardOpen: false });
    });

    test("exposes the active step and moves focus when the advancing button unmounts", () => {
        render(<QuestionWizard />);

        const statement = screen.getByRole("button", { name: "Statement" });
        expect(statement).toHaveAttribute("aria-current", "step");
        expect(screen.getByRole("button", { name: "Type" })).not.toHaveAttribute("aria-current");

        fireEvent.click(screen.getByRole("button", { name: /Next: configure/i }));

        const config = screen.getByRole("button", { name: "Config" });
        expect(config).toHaveAttribute("aria-current", "step");
        expect(config).toHaveFocus();
        expect(statement).not.toHaveAttribute("aria-current");
    });
});
