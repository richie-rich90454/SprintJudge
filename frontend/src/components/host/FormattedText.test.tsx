import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { FormattedText } from "./FormattedText";

describe("FormattedText", () => {
    test("renders nothing for empty text", () => {
        const { container } = render(<FormattedText text="" />);
        expect(container).toBeEmptyDOMElement();
    });

    test("renders inline code between backticks and plain fragments around it", () => {
        render(<FormattedText text="Use `let x` here, then `y`." className="stem" />);
        expect(screen.getByText("let x")).toHaveTextContent("let x");
        expect(screen.getByText("let x").tagName).toBe("CODE");
        expect(screen.getByText("y").tagName).toBe("CODE");
        expect(screen.getByText(/Use/).closest("div")).toHaveClass("stem");
    });

    test("renders fenced blocks with a language tag, line numbers and blank lines", () => {
        const { container } = render(<FormattedText text={"intro\n```java\nint a = 1;\n\nint b = 2;  \n```\noutro"} />);
        const lines = container.querySelectorAll("pre code > span");
        expect(lines).toHaveLength(3);
        expect(lines[0]).toHaveTextContent("1int a = 1;");
        expect(lines[1]).toHaveTextContent("2");
        expect(lines[2]).toHaveTextContent("3int b = 2;");
        expect(screen.getByText(/intro/)).toBeInTheDocument();
        expect(screen.getByText(/outro/)).toBeInTheDocument();
    });

    test("renders a fenced block without a language line", () => {
        const { container } = render(<FormattedText text={"```\nplain\n```"} />);
        expect(container.querySelectorAll("pre code > span")).toHaveLength(1);
        expect(screen.getByText("plain")).toBeInTheDocument();
    });
});
