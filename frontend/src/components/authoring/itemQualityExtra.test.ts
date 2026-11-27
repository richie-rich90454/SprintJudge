import { describe, it, expect } from "vitest";
import { checkItemQuality, type QualityInput } from "./itemQuality";

const base: QualityInput = {
    type: "MCQ",
    title: "Select",
    description: "Which options apply?",
    config: {
        explanation:
            "A sufficiently long explanation that teaches why the first options are the correct ones here.",
        hint: "Look at each option in turn.",
        options: ["alpha value here", "beta value here", "gamma value here"],
        correctIndex: 0,
        misconceptions: [
            "believes alpha is always wrong here",
            "believes beta is always wrong here",
            "believes gamma is always wrong here",
        ],
    },
};

describe("checkItemQuality answer indices", () => {
    it("accepts a correctIndices array with mixed values", () => {
        const findings = checkItemQuality({
            ...base,
            type: "MULTIPLE_SELECT",
            config: {
                ...base.config,
                correctIndex: undefined,
                correctIndices: [0, "skip", 1],
            },
        });
        expect(Array.isArray(findings)).toBe(true);
        expect(findings.some((f) => f.message.includes("No answer"))).toBe(false);
    });

    it("flags a non-finite correct index", () => {
        const findings = checkItemQuality({
            ...base,
            config: { ...base.config, correctIndex: Number.NaN },
        });
        expect(findings.some((f) => f.message.includes("No answer"))).toBe(true);
    });
});

describe("checkItemQuality option and misconception shapes", () => {
    it("ignores non-string options and flags too few usable options", () => {
        const findings = checkItemQuality({
            ...base,
            config: { ...base.config, options: ["only-one", 42, null] },
        });
        expect(findings.some((f) => f.message.includes("Need 2+ options"))).toBe(true);
    });

    it("handles configs with no options and no misconceptions", () => {
        const findings = checkItemQuality({
            type: "OJ_FULL",
            title: "Code",
            description: "int x = 1;",
            config: {
                explanation:
                    "A sufficiently long explanation that teaches the reasoning behind the answer here.",
                hint: "A short nudge.",
            },
        });
        expect(findings.some((f) => f.message.includes("Need 2+ options"))).toBe(false);
    });

    it("skips length advice when all option text is empty", () => {
        const findings = checkItemQuality({
            ...base,
            config: {
                explanation:
                    "A sufficiently long explanation that teaches the correct answer here today.",
                hint: "h",
                options: ["", "", ""],
                correctIndex: 0,
            },
        });
        expect(Array.isArray(findings)).toBe(true);
    });
});

describe("checkItemQuality explanation and media", () => {
    it("advises adding terminal punctuation", () => {
        const findings = checkItemQuality({
            ...base,
            config: {
                ...base.config,
                explanation:
                    "This explanation teaches the complete reasoning without any final mark",
            },
        });
        expect(findings.some((f) => f.message.includes("no terminal punctuation"))).toBe(true);
    });

    it("accepts audio media with a recorded license", () => {
        const findings = checkItemQuality({
            ...base,
            config: {
                ...base.config,
                rich: { audioUrl: "/media/a.mp3", mediaLicense: "CC0" },
            },
        });
        expect(findings.some((f) => f.message.includes("mediaLicense"))).toBe(false);
    });

    it("does not run code-stem advice when the stem is empty", () => {
        const findings = checkItemQuality({
            ...base,
            type: "OJ_FULL",
            title: "",
            description: "",
        });
        expect(findings.some((f) => f.message.includes("carries no code"))).toBe(false);
    });
});
