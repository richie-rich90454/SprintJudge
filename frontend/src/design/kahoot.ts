/** Kahoot / JuiceMind signature 4-color answer system (CSS vars = single source). */
export const KAHOOT_COLORS = {
    blue: "var(--color-kahoot-blue)",
    green: "var(--color-kahoot-green)",
    yellow: "var(--color-kahoot-yellow)",
    pink: "var(--color-kahoot-pink)",
} as const;

/** Ordered palette used for answer slots 0..3 (and beyond, by index). */
export const ANSWER_PALETTE = [
    KAHOOT_COLORS.blue,
    KAHOOT_COLORS.green,
    KAHOOT_COLORS.yellow,
    KAHOOT_COLORS.pink,
];

/** Returns the Kahoot color for an answer index (wraps if > 3). */
export function answerColor(index: number): string {
    return ANSWER_PALETTE[index % ANSWER_PALETTE.length];
}

/** Letter badge for an answer index: A, B, C, D … */
export function answerLetter(index: number): string {
    return String.fromCharCode(65 + index);
}
