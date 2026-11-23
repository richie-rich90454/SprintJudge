import { describe, expect, it } from "vitest";
import { checkItemQuality, QualityInput } from "./itemQuality";

const base: QualityInput = {
    type: "MCQ",
    title: "Values",
    description: "Which option is correct?",
    config: {
        explanation: "The first value is correct because the rule applies only to the first case, so the second case is excluded.",
        hint: "Look at the rule again.",
        options: ["alpha value", "beta value"],
        correctIndex: 0,
        misconceptions: ["believes the first value is always larger than the second", "believes the second value is always larger than the first"],
    },
};

describe("checkItemQuality", () => {
    it("reports no findings for a clean item", () => {
        expect(checkItemQuality(base)).toEqual([]);
    });

    it("mirrors bank advisory and blocking rules", () => {
        const findings = checkItemQuality({
            ...base,
            description: "Wait compute the answer",
            config: {
                explanation: "the answer is the",
                hint: "",
                options: ["same", "same", "a much longer option text here"],
                misconceptions: ["wrong", "believes the opposite of the rule stated above"],
                rich: { imageUrl: "https://example.test/a.png" },
            },
        });
        const joined = findings.map((f) => f.message).join(" | ");
        expect(joined).toContain("Author scratch");
        expect(joined).toContain("under 60 chars");
        expect(joined).toContain("Empty hint");
        expect(joined).toContain("Duplicate option text");
        expect(joined).toContain("No answer");
        expect(joined).toContain("misconception under 12 chars");
        expect(joined).toContain("median length");
        expect(joined).toContain("mediaLicense");
        expect(joined).toContain("mid-phrase");
    });

    it("warns when a code-type stem has no code and when the stem is missing", () => {
        const code = checkItemQuality({ ...base, type: "OJ_FULL" });
        expect(code.map((f) => f.message).join(" ")).toContain("carries no code");
        const empty = checkItemQuality({ ...base, title: "", description: "" });
        expect(empty.some((f) => f.level === "error" && f.message.includes("Stem is empty"))).toBe(true);
    });
});
