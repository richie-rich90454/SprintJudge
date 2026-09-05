import { describe, expect, test } from "vitest";
import { formatAnswer } from "./reviewFormat";

const OPTS = ["Alpha", "Beta", "Gamma", "Delta"];

describe("formatAnswer", () => {
    test("nullish answers show a dash", () => {
        expect(formatAnswer("MCQ", null, OPTS)).toBe("—");
        expect(formatAnswer("MCQ", undefined, OPTS)).toBe("—");
    });

    test("non-object answers stringify", () => {
        expect(formatAnswer("MCQ", "B", OPTS)).toBe("B");
        expect(formatAnswer("MCQ", 2, OPTS)).toBe("2");
    });

    test("MCQ resolves letter and text", () => {
        expect(formatAnswer("MCQ", { correctIndex: 1 }, OPTS)).toBe("Correct: B — Beta");
    });

    test("OUTPUT_PRED and COMPLEXITY share MCQ shape", () => {
        expect(formatAnswer("OUTPUT_PRED", { correctIndex: 0 }, OPTS)).toBe("Correct: A — Alpha");
        expect(formatAnswer("COMPLEXITY", { correctIndex: 3 }, OPTS)).toBe("Correct: D — Delta");
    });

    test("index without options falls back to letter", () => {
        expect(formatAnswer("MCQ", { correctIndex: 2 }, null)).toBe("Correct: C");
        expect(formatAnswer("MCQ", { correctIndex: 2 }, ["A"])).toBe("Correct: C");
    });

    test("garbage index shows raw", () => {
        expect(formatAnswer("MCQ", { correctIndex: "x" }, OPTS)).toBe("Correct: x");
        expect(formatAnswer("MCQ", { correctIndex: -1 }, OPTS)).toBe("Correct: -1");
        expect(formatAnswer("MCQ", {}, OPTS)).toBe("Correct: ?");
    });

    test("TRUE_FALSE spells out the verdict", () => {
        expect(formatAnswer("TRUE_FALSE", { correct: true }, null)).toBe("Correct: True");
        expect(formatAnswer("TRUE_FALSE", { correct: false }, null)).toBe("Correct: False");
        expect(formatAnswer("TRUE_FALSE", {}, null)).toBe("Correct: ?");
    });

    test("MULTIPLE_SELECT joins letters", () => {
        expect(formatAnswer("MULTIPLE_SELECT", { correctIndices: [0, 2] }, OPTS)).toBe(
            "Correct: A — Alpha, C — Gamma",
        );
    });

    test("MULTIPLE_SELECT without indices shows unknown", () => {
        expect(formatAnswer("MULTIPLE_SELECT", {}, null)).toBe("Correct: ?");
        expect(formatAnswer("MULTIPLE_SELECT", { correctIndices: "x" }, null)).toBe("Correct: ?");
    });

    test("NUMERIC shows value and tolerance", () => {
        expect(formatAnswer("NUMERIC", { answer: 42, tolerance: 0.5 }, null)).toBe(
            "Correct: 42 (± 0.5)",
        );
    });

    test("NUMERIC without tolerance omits it", () => {
        expect(formatAnswer("NUMERIC", { answer: 42 }, null)).toBe("Correct: 42");
        expect(formatAnswer("NUMERIC", { answer: 42, tolerance: "" }, null)).toBe("Correct: 42");
        expect(formatAnswer("NUMERIC", {}, null)).toBe("Correct: ?");
    });

    test("FILL_BLANK shows text", () => {
        expect(formatAnswer("FILL_BLANK", { answer: "Paris" }, null)).toBe("Correct: Paris");
        expect(formatAnswer("FILL_BLANK", {}, null)).toBe("Correct: ?");
    });

    test("DRAG_SORT numbers lines with texts", () => {
        expect(
            formatAnswer("DRAG_SORT", { correctOrder: ["1", "0"] }, ["first", "second"]),
        ).toBe("Correct order: 1. second · 2. first");
    });

    test("DRAG_SORT falls back to raw ids", () => {
        expect(formatAnswer("DRAG_SORT", { correctOrder: ["l9"] }, null)).toBe(
            "Correct order: 1. l9",
        );
        expect(formatAnswer("DRAG_SORT", {}, null)).toBe("Correct order: ?");
        expect(formatAnswer("DRAG_SORT", { correctOrder: "x" }, null)).toBe("Correct order: ?");
    });

    test("CLICK_BUG shows line", () => {
        expect(formatAnswer("CLICK_BUG", { bugLine: 7 }, null)).toBe("Buggy line: 7");
        expect(formatAnswer("CLICK_BUG", {}, null)).toBe("Buggy line: ?");
    });

    test("CODE_COMPLETION shows expected block", () => {
        expect(formatAnswer("CODE_COMPLETION", { expected: "return 1;" }, null)).toBe(
            "Expected:\nreturn 1;",
        );
        expect(formatAnswer("CODE_COMPLETION", {}, null)).toBe("Expected:\n?");
    });

    test("unknown types dump JSON", () => {
        expect(formatAnswer("WAT", { a: 1 }, null)).toBe('Correct: {"a":1}');
    });
});
