import { describe, test, expect, vi, afterEach } from "vitest";
import {
    answerColor,
    answerLetter,
    answerShape,
    boardDelayedForMode,
    ANSWER_PALETTE,
    ANSWER_SHAPES,
    KAHOOT_COLORS,
    myColor,
} from "./kahoot";

afterEach(() => {
    vi.restoreAllMocks();
});

describe("answerColor", () => {
    test("first four indexes map to the palette in order", () => {
        expect(answerColor(0)).toBe(ANSWER_PALETTE[0]);
        expect(answerColor(1)).toBe(ANSWER_PALETTE[1]);
        expect(answerColor(2)).toBe(ANSWER_PALETTE[2]);
        expect(answerColor(3)).toBe(ANSWER_PALETTE[3]);
    });

    test("index wraps around the palette", () => {
        expect(answerColor(4)).toBe(ANSWER_PALETTE[0]);
        expect(answerColor(7)).toBe(ANSWER_PALETTE[3]);
        expect(answerColor(9)).toBe(ANSWER_PALETTE[1]);
    });
});

describe("answerLetter", () => {
    test("zero maps to A", () => {
        expect(answerLetter(0)).toBe("A");
    });

    test("three maps to D", () => {
        expect(answerLetter(3)).toBe("D");
    });

    test("twenty-five maps to Z", () => {
        expect(answerLetter(25)).toBe("Z");
    });
});

describe("answerShape", () => {
    test("first four indexes map in order", () => {
        expect(answerShape(0)).toBe("triangle");
        expect(answerShape(1)).toBe("diamond");
        expect(answerShape(2)).toBe("circle");
        expect(answerShape(3)).toBe("square");
    });

    test("index wraps past the fourth shape", () => {
        expect(answerShape(4)).toBe("triangle");
        expect(answerShape(5)).toBe("diamond");
    });

    test("shapes list has four entries", () => {
        expect(ANSWER_SHAPES).toHaveLength(4);
    });
});

describe("boardDelayedForMode", () => {
    test("STANDARD freezes the board", () => {
        expect(boardDelayedForMode("STANDARD")).toBe(true);
    });

    test("TEAM freezes the board", () => {
        expect(boardDelayedForMode("TEAM")).toBe(true);
    });

    test("BATTLE freezes the board", () => {
        expect(boardDelayedForMode("BATTLE")).toBe(true);
    });

    test("AUTO_PILOT freezes the board", () => {
        expect(boardDelayedForMode("AUTO_PILOT")).toBe(true);
    });

    test("PRACTICE does not freeze the board", () => {
        expect(boardDelayedForMode("PRACTICE")).toBe(false);
    });

    test("EXAM does not freeze the board", () => {
        expect(boardDelayedForMode("EXAM")).toBe(false);
    });

    test("null mode does not freeze the board", () => {
        expect(boardDelayedForMode(null)).toBe(false);
    });

    test("undefined mode does not freeze the board", () => {
        expect(boardDelayedForMode(undefined)).toBe(false);
    });

    test("unknown mode does not freeze the board", () => {
        expect(boardDelayedForMode("ARCADE")).toBe(false);
    });
});

describe("kahoot constants", () => {
    test("palette holds the four answer fills", () => {
        expect(ANSWER_PALETTE).toEqual([
            KAHOOT_COLORS.blue,
            KAHOOT_COLORS.green,
            KAHOOT_COLORS.yellow,
            KAHOOT_COLORS.pink,
        ]);
    });

    test("brand ramp has ten stops", () => {
        expect(myColor).toHaveLength(10);
    });
});
