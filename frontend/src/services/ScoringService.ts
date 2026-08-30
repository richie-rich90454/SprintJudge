import { QuestionType } from "../types";

/**
 * Client-side helpers shared by player views.
 */
export function isCoding(type: QuestionType): boolean {
    return type === "OJ_FULL" || type === "OJ_PATCH";
}
