import { ALL_QUESTION_TYPES, QuestionType } from "../../types";

/** Types editable in the wizard beyond the core engine's type union. */
export const EXTRA_QUESTION_TYPES = [
    "HOT_SPOT",
    "MATCHING",
    "TOLERANCE_NUMERIC",
    "MULTI_PART",
] as const;

export type ExtraQuestionType = (typeof EXTRA_QUESTION_TYPES)[number];
export type WizardQuestionType = QuestionType | ExtraQuestionType;

export const WIZARD_QUESTION_TYPES: WizardQuestionType[] = [
    ...ALL_QUESTION_TYPES,
    ...EXTRA_QUESTION_TYPES,
];

export function isExtraQuestionType(value: string): value is ExtraQuestionType {
    return (EXTRA_QUESTION_TYPES as readonly string[]).includes(value);
}

export function extraDefaultConfig(type: ExtraQuestionType): Record<string, unknown> {
    switch (type) {
        case "HOT_SPOT":
            return {
                imageUrl: "",
                width: 100,
                height: 100,
                regions: [{ id: "a", label: "A", x: 50, y: 50, r: 8 }],
                correctRegionIds: ["a"],
            };
        case "MATCHING":
            return {
                left: ["", ""],
                right: ["", ""],
                pairs: [{ left: 0, right: 0 }],
            };
        case "TOLERANCE_NUMERIC":
            return { answer: 0, tolerance: 0, toleranceMode: "absolute", unit: "" };
        case "MULTI_PART":
            return {
                parts: [{ id: "p1", prompt: "", kind: "TEXT", options: [], answer: "", points: 1 }],
            };
    }
}
