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

describe("kahoot boundary sweeps", () => {
    test("answerColor maps 0 through 7 to exact palette entries", () => {
        const expected = [0, 1, 2, 3, 0, 1, 2, 3].map((i) => ANSWER_PALETTE[i]);
        expect([0, 1, 2, 3, 4, 5, 6, 7].map(answerColor)).toEqual(expected);
    });

    test("answerColor at 100 wraps to the first entry", () => {
        expect(answerColor(100)).toBe(ANSWER_PALETTE[0]);
        expect(answerColor(101)).toBe(ANSWER_PALETTE[1]);
        expect(answerColor(102)).toBe(ANSWER_PALETTE[2]);
        expect(answerColor(103)).toBe(ANSWER_PALETTE[3]);
    });

    test("answerColor at 8 wraps exactly like 0", () => {
        expect(answerColor(8)).toBe(answerColor(0));
        expect(answerColor(12)).toBe(answerColor(0));
    });

    test("answerColor with a negative index yields undefined", () => {
        expect(answerColor(-1)).toBeUndefined();
        expect(answerColor(-4)).toBe(ANSWER_PALETTE[0]);
    });

    test("answerLetter covers A through H exactly", () => {
        expect([0, 1, 2, 3, 4, 5, 6, 7].map(answerLetter)).toEqual(["A", "B", "C", "D", "E", "F", "G", "H"]);
    });

    test("answerLetter past Z continues through char codes", () => {
        expect(answerLetter(26)).toBe("[");
        expect(answerLetter(27)).toBe("\\");
        expect(answerLetter(100)).toBe(String.fromCharCode(165));
    });

    test("answerShape wraps at 4 and 100 exactly", () => {
        expect(answerShape(4)).toBe("triangle");
        expect(answerShape(8)).toBe("triangle");
        expect(answerShape(100)).toBe("triangle");
        expect(answerShape(101)).toBe("diamond");
        expect(answerShape(102)).toBe("circle");
        expect(answerShape(103)).toBe("square");
    });

    test("answerShape with a negative index yields undefined", () => {
        expect(answerShape(-1)).toBeUndefined();
    });

    test("boardDelayed covers every game mode exactly", () => {
        expect(["STANDARD", "TEAM", "BATTLE", "AUTO_PILOT"].map((m) => boardDelayedForMode(m))).toEqual([true, true, true, true]);
        expect(["PRACTICE", "EXAM"].map((m) => boardDelayedForMode(m))).toEqual([false, false]);
    });

    test("boardDelayed rejects empty and miscased modes", () => {
        expect(boardDelayedForMode("")).toBe(false);
        expect(boardDelayedForMode("standard")).toBe(false);
        expect(boardDelayedForMode(" TEAM")).toBe(false);
        expect(boardDelayedForMode("BATTLE ")).toBe(false);
    });

    test("brand ramp holds exact endpoint stops", () => {
        expect(myColor[0]).toBe("#fff0e4");
        expect(myColor[9]).toBe("#a73c00");
        expect(myColor[5]).toBe("#f06e27");
    });

    test("answer fills reference the four CSS answer variables", () => {
        expect(KAHOOT_COLORS.blue).toBe("var(--oq-answer-0)");
        expect(KAHOOT_COLORS.green).toBe("var(--oq-answer-1)");
        expect(KAHOOT_COLORS.yellow).toBe("var(--oq-answer-2)");
        expect(KAHOOT_COLORS.pink).toBe("var(--oq-answer-3)");
        expect(new Set(ANSWER_PALETTE).size).toBe(4);
    });

    test("four-option round composes color letter and shape per slot", () => {
        const slots = [0, 1, 2, 3].map((i) => ({
            color: answerColor(i),
            letter: answerLetter(i),
            shape: answerShape(i),
        }));
        expect(slots).toEqual([
            { color: ANSWER_PALETTE[0], letter: "A", shape: "triangle" },
            { color: ANSWER_PALETTE[1], letter: "B", shape: "diamond" },
            { color: ANSWER_PALETTE[2], letter: "C", shape: "circle" },
            { color: ANSWER_PALETTE[3], letter: "D", shape: "square" },
        ]);
    });

    test("wrapped slot 4 mirrors slot 0 across all three helpers", () => {
        expect(answerColor(4)).toBe(answerColor(0));
        expect(answerShape(4)).toBe(answerShape(0));
        expect(answerLetter(4)).toBe("E");
    });
});
