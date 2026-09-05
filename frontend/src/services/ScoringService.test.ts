import { describe, test, expect, vi, afterEach } from "vitest";
import { isCoding } from "./ScoringService";

afterEach(() => {
    vi.restoreAllMocks();
});

describe("isCoding", () => {
    test("OJ_FULL is coding", () => {
        expect(isCoding("OJ_FULL")).toBe(true);
    });

    test("OJ_PATCH is coding", () => {
        expect(isCoding("OJ_PATCH")).toBe(true);
    });

    test("MCQ is not coding", () => {
        expect(isCoding("MCQ")).toBe(false);
    });

    test("TRUE_FALSE is not coding", () => {
        expect(isCoding("TRUE_FALSE")).toBe(false);
    });

    test("MULTIPLE_SELECT is not coding", () => {
        expect(isCoding("MULTIPLE_SELECT")).toBe(false);
    });

    test("NUMERIC is not coding", () => {
        expect(isCoding("NUMERIC")).toBe(false);
    });

    test("OUTPUT_PRED is not coding", () => {
        expect(isCoding("OUTPUT_PRED")).toBe(false);
    });

    test("FILL_BLANK is not coding", () => {
        expect(isCoding("FILL_BLANK")).toBe(false);
    });

    test("DRAG_SORT is not coding", () => {
        expect(isCoding("DRAG_SORT")).toBe(false);
    });

    test("CLICK_BUG is not coding", () => {
        expect(isCoding("CLICK_BUG")).toBe(false);
    });

    test("CODE_COMPLETION is not coding", () => {
        expect(isCoding("CODE_COMPLETION")).toBe(false);
    });

    test("COMPLEXITY is not coding", () => {
        expect(isCoding("COMPLEXITY")).toBe(false);
    });
});

describe("isCoding boundaries", () => {
    test("full twelve-type sweep matches exactly the two coding formats", () => {
        const types = ["MCQ", "TRUE_FALSE", "MULTIPLE_SELECT", "NUMERIC", "OUTPUT_PRED", "FILL_BLANK", "DRAG_SORT", "CLICK_BUG", "CODE_COMPLETION", "COMPLEXITY", "OJ_FULL", "OJ_PATCH"] as const;
        expect(types.filter((t) => isCoding(t))).toEqual(["OJ_FULL", "OJ_PATCH"]);
        expect(types.filter((t) => !isCoding(t))).toHaveLength(10);
    });

    test("undefined input is not coding", () => {
        expect(isCoding(undefined as unknown as Parameters<typeof isCoding>[0])).toBe(false);
    });

    test("null input is not coding", () => {
        expect(isCoding(null as unknown as Parameters<typeof isCoding>[0])).toBe(false);
    });

    test("empty string is not coding", () => {
        expect(isCoding("" as unknown as Parameters<typeof isCoding>[0])).toBe(false);
    });

    test("lowercase oj_full is not coding", () => {
        expect(isCoding("oj_full" as unknown as Parameters<typeof isCoding>[0])).toBe(false);
    });

    test("padded MCQ is not coding and not equal to MCQ", () => {
        expect(isCoding("MCQ " as unknown as Parameters<typeof isCoding>[0])).toBe(false);
        expect(isCoding(" MCQ" as unknown as Parameters<typeof isCoding>[0])).toBe(false);
    });

    test("numeric and object inputs are not coding", () => {
        expect(isCoding(0 as unknown as Parameters<typeof isCoding>[0])).toBe(false);
        expect(isCoding({} as unknown as Parameters<typeof isCoding>[0])).toBe(false);
    });

    test("OJ prefix alone is not coding", () => {
        expect(isCoding("OJ" as unknown as Parameters<typeof isCoding>[0])).toBe(false);
        expect(isCoding("OJ_" as unknown as Parameters<typeof isCoding>[0])).toBe(false);
    });
});
