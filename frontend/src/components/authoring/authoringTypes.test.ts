import { describe, it, expect } from "vitest";
import {
    EXTRA_QUESTION_TYPES,
    WIZARD_QUESTION_TYPES,
    isExtraQuestionType,
    extraDefaultConfig,
    type ExtraQuestionType,
} from "./authoringTypes";

describe("authoringTypes", () => {
    it("exposes the extra types and the full wizard list", () => {
        expect(EXTRA_QUESTION_TYPES).toEqual(["HOT_SPOT", "MATCHING", "TOLERANCE_NUMERIC", "MULTI_PART"]);
        expect(WIZARD_QUESTION_TYPES).toContain("MCQ");
        expect(WIZARD_QUESTION_TYPES).toContain("OJ_PATCH");
        expect(WIZARD_QUESTION_TYPES).toHaveLength(16);
    });

    it("detects extra question types", () => {
        expect(isExtraQuestionType("HOT_SPOT")).toBe(true);
        expect(isExtraQuestionType("MULTI_PART")).toBe(true);
        expect(isExtraQuestionType("MCQ")).toBe(false);
    });

    it("returns the default config for each extra type", () => {
        expect(extraDefaultConfig("HOT_SPOT")).toEqual({
            imageUrl: "",
            width: 100,
            height: 100,
            regions: [{ id: "a", label: "A", x: 50, y: 50, r: 8 }],
            correctRegionIds: ["a"],
        });
        expect(extraDefaultConfig("MATCHING")).toEqual({
            left: ["", ""],
            right: ["", ""],
            pairs: [{ left: 0, right: 0 }],
        });
        expect(extraDefaultConfig("TOLERANCE_NUMERIC")).toEqual({
            answer: 0,
            tolerance: 0,
            toleranceMode: "absolute",
            unit: "",
        });
        expect(extraDefaultConfig("MULTI_PART")).toEqual({
            parts: [{ id: "p1", prompt: "", kind: "TEXT", options: [], answer: "", points: 1 }],
        });
        expect(extraDefaultConfig("UNKNOWN" as ExtraQuestionType)).toBeUndefined();
    });
});
