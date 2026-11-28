import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import type { QuestionDto, QuestionType, RoundResult } from "../../types";
import { QuestionPreview } from "./QuestionPreview";

function question(
    type: QuestionType,
    config: Record<string, unknown>,
    description = "",
): QuestionDto {
    return {
        id: "q1",
        type,
        title: "Question Title",
        description,
        timeLimitSec: 30,
        pointsBase: 100,
        languagesAllowed: null,
        config,
    };
}

function round(correctAnswer: Record<string, unknown> | null, revealed = true): RoundResult {
    return {
        type: "ROUND_RESULT",
        questionId: "q1",
        revealed,
        correctAnswer,
        scores: [],
    };
}

const ACCENT = "color-mix";

describe("QuestionPreview", () => {
    test("uses csaUnit, topic, or type for the label and toggles the description", () => {
        const { rerender, container } = render(
            <QuestionPreview question={question("MCQ", {})} round={null} />,
        );
        expect(screen.getByText("MCQ")).toBeInTheDocument();
        expect(screen.queryByText("A description")).not.toBeInTheDocument();

        rerender(
            <QuestionPreview
                question={question("MCQ", { topic: "Topic" }, "A description")}
                round={null}
            />,
        );
        expect(screen.getByText("Topic")).toBeInTheDocument();
        expect(screen.getByText("A description")).toBeInTheDocument();

        rerender(
            <QuestionPreview
                question={question("MCQ", { topic: "Question Title" })}
                round={null}
            />,
        );
        expect(screen.getByText("MCQ")).toBeInTheDocument();

        rerender(
            <QuestionPreview
                question={question("MCQ", { csaUnit: "Unit", topic: "Topic" })}
                round={null}
            />,
        );
        expect(screen.getByText("Unit")).toBeInTheDocument();
        expect(container.firstChild).not.toHaveClass("extra");
    });

    test("applies the className to the root", () => {
        const { container } = render(
            <QuestionPreview question={question("MCQ", {})} round={null} className="extra" />,
        );
        expect(container.firstChild).toHaveClass("extra");
    });

    test("renders code lines and highlights the buggy line only when revealed", () => {
        const q = question("CLICK_BUG", { lines: ["first", "second"], bugLine: 1 });
        const revealed = render(<QuestionPreview question={q} round={round({ bugLine: 1 })} />);
        const spans = revealed.container.querySelectorAll("pre code > span");
        expect(spans).toHaveLength(2);
        expect(spans[0].textContent).toContain("first");
        expect(spans[1].className).toContain(ACCENT);
        revealed.unmount();

        const hidden = render(<QuestionPreview question={q} round={round(null, false)} />);
        expect(hidden.container.querySelectorAll("pre code > span")[1].className).not.toContain(
            ACCENT,
        );
        hidden.unmount();

        const empty = render(<QuestionPreview question={question("CLICK_BUG", {})} round={null} />);
        expect(empty.container.querySelector("pre")).not.toBeInTheDocument();
    });

    test("renders starter code for OJ types, with a fallback", () => {
        const withStarter = render(
            <QuestionPreview
                question={question("OJ_FULL", { starter: "class Main {}" })}
                round={null}
            />,
        );
        expect(withStarter.getByText("Starter")).toBeInTheDocument();
        expect(withStarter.getByText("class Main {}")).toBeInTheDocument();
        withStarter.unmount();

        const fallback = render(
            <QuestionPreview question={question("OJ_PATCH", {})} round={null} />,
        );
        expect(fallback.getByText("Write your solution in the editor.")).toBeInTheDocument();
    });

    test("highlights the revealed MCQ index from the answer and tolerates bad values", () => {
        const q = question("MCQ", { options: ["Alpha", "Beta"], correctIndex: 0 });
        const { container, unmount } = render(
            <QuestionPreview question={q} round={round({ correctIndex: 1 })} />,
        );
        const rows = container.querySelectorAll(".grid > div");
        expect(rows[0].className).not.toContain(ACCENT);
        expect(rows[1].className).toContain(ACCENT);
        expect(screen.getByText("B")).toBeInTheDocument();
        unmount();

        const { container: hidden } = render(
            <QuestionPreview question={q} round={round(null, false)} />,
        );
        expect(hidden.querySelectorAll(".grid > div")[0].className).not.toContain(ACCENT);

        const bad = render(
            <QuestionPreview
                question={question("MCQ", { options: [1, 2], correctIndex: "x" })}
                round={round({})}
            />,
        );
        expect(bad.getByText("1")).toBeInTheDocument();
        expect(bad.container.querySelectorAll(".grid > div")[0].className).not.toContain(ACCENT);
    });

    test("reveals multiple-select indices from the answer, falling back to config", () => {
        const q = question("MULTIPLE_SELECT", { options: ["A", "B", "C"], correctIndices: [0, 1] });
        const { container, unmount } = render(
            <QuestionPreview question={q} round={round({ correctIndices: [2] })} />,
        );
        const rows = container.querySelectorAll(".grid > div");
        expect(rows[0].className).not.toContain(ACCENT);
        expect(rows[1].className).not.toContain(ACCENT);
        expect(rows[2].className).toContain(ACCENT);
        unmount();

        const fallback = render(<QuestionPreview question={q} round={round({})} />);
        const rows2 = fallback.container.querySelectorAll(".grid > div");
        expect(rows2[0].className).toContain(ACCENT);
        expect(rows2[1].className).toContain(ACCENT);
    });

    test("renders option rows for OUTPUT_PRED and COMPLEXITY", () => {
        render(
            <QuestionPreview
                question={question("OUTPUT_PRED", { options: ["x", "y"] })}
                round={null}
            />,
        );
        expect(screen.getByText("x")).toBeInTheDocument();
        expect(screen.getByText("y")).toBeInTheDocument();
    });

    test("renders true/false with reveal from answer then config", () => {
        const q = question("TRUE_FALSE", { correct: false });
        const fromAnswer = render(
            <QuestionPreview question={q} round={round({ correct: true })} />,
        );
        const rows = fromAnswer.container.querySelectorAll(".grid > div");
        expect(rows[0].className).toContain(ACCENT);
        expect(screen.getByText("True")).toBeInTheDocument();
        expect(screen.getByText("False")).toBeInTheDocument();
        fromAnswer.unmount();

        const fromConfig = render(<QuestionPreview question={q} round={round({})} />);
        expect(fromConfig.container.querySelectorAll(".grid > div")[1].className).toContain(ACCENT);
        fromConfig.unmount();

        const hidden = render(<QuestionPreview question={q} round={round(null, false)} />);
        expect(hidden.container.querySelectorAll(".grid > div")[0].className).not.toContain(ACCENT);
    });

    test("renders numeric and fill-blank answers with fallbacks", () => {
        const numeric = render(
            <QuestionPreview
                question={question("NUMERIC", { answer: 7 })}
                round={round({ answer: 42 })}
            />,
        );
        expect(numeric.getByText("42")).toBeInTheDocument();
        numeric.unmount();

        const numericConfig = render(
            <QuestionPreview question={question("NUMERIC", { answer: 7 })} round={round({})} />,
        );
        expect(numericConfig.getByText("7")).toBeInTheDocument();
        numericConfig.unmount();

        const numericUnknown = render(
            <QuestionPreview question={question("NUMERIC", {})} round={round({})} />,
        );
        expect(numericUnknown.getByText("?")).toBeInTheDocument();
        numericUnknown.unmount();

        const hidden = render(
            <QuestionPreview question={question("NUMERIC", {})} round={round(null, false)} />,
        );
        expect(hidden.getByText("Enter a number")).toBeInTheDocument();
        hidden.unmount();

        const blank = render(
            <QuestionPreview
                question={question("FILL_BLANK", { answer: "word" })}
                round={round({ answer: "yes" })}
            />,
        );
        expect(blank.getByText("yes")).toBeInTheDocument();
        expect(blank.getByText("Blank")).toBeInTheDocument();
        blank.unmount();

        const blankHidden = render(
            <QuestionPreview question={question("FILL_BLANK", {})} round={round(null, false)} />,
        );
        expect(blankHidden.getByText("One answer")).toBeInTheDocument();
    });

    test("handles blank lines, a null config and the fill-blank config fallback", () => {
        const blankLine = render(
            <QuestionPreview question={question("CLICK_BUG", { lines: [""] })} round={null} />,
        );
        expect(blankLine.container.textContent).toContain(" ");
        blankLine.unmount();

        const nullConfig = render(
            <QuestionPreview question={question("MCQ", null as never)} round={null} />,
        );
        expect(nullConfig.getByText("MCQ")).toBeInTheDocument();
        nullConfig.unmount();

        const blankFallback = render(
            <QuestionPreview
                question={question("FILL_BLANK", { answer: "word" })}
                round={round({})}
            />,
        );
        expect(blankFallback.getByText("word")).toBeInTheDocument();
        blankFallback.unmount();

        const blankUnknown = render(
            <QuestionPreview question={question("FILL_BLANK", {})} round={round({})} />,
        );
        expect(blankUnknown.getByText("?")).toBeInTheDocument();
    });

    test("renders the generic correct-answer block for other types", () => {
        const exemplar = render(
            <QuestionPreview
                question={question("CODE_COMPLETION", {})}
                round={round({ exemplar: "done" })}
            />,
        );
        expect(exemplar.getByText("done")).toBeInTheDocument();
        exemplar.unmount();

        const formatted = render(
            <QuestionPreview
                question={question("DRAG_SORT", {})}
                round={round({ correctOrder: [1, 0] })}
            />,
        );
        expect(formatted.getByText("Correct order: 1. 1, 2. 0")).toBeInTheDocument();
        formatted.unmount();

        const hidden = render(
            <QuestionPreview
                question={question("CODE_COMPLETION", {})}
                round={round(null, false)}
            />,
        );
        expect(hidden.queryByText("Correct answer")).not.toBeInTheDocument();
    });
});
