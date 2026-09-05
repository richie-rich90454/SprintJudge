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
