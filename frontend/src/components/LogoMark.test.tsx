import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { LogoMark } from "./LogoMark";

describe("LogoMark", () => {
    test("renders an accessible logo at the requested size", () => {
        render(<LogoMark size={28} />);
        const logo = screen.getByRole("img", { name: "SprintJudge" });
        expect(logo).toHaveAttribute("width", "28");
        expect(logo).toHaveAttribute("height", "28");
    });

    test("defaults to 34px", () => {
        render(<LogoMark />);
        expect(screen.getByRole("img", { name: "SprintJudge" })).toHaveAttribute("width", "34");
    });
});
