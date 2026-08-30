/** Kahoot / JuiceMind signature 4-color answer system. */
export const KAHOT_COLORS = {
    blue: "#2e7cf6",
    green: "#1fbe6b",
    yellow: "#ffc62e",
    pink: "#ff4da6",
} as const;

/** Ordered palette used for answer slots 0..3 (and beyond, by index). */
export const ANSWER_PALETTE = [
    KAHOT_COLORS.blue,
    KAHOT_COLORS.green,
    KAHOT_COLORS.yellow,
    KAHOT_COLORS.pink,
];

/** Returns the Kahoot color for an answer index (wraps if > 3). */
export function answerColor(index: number): string {
    return ANSWER_PALETTE[index % ANSWER_PALETTE.length];
}

/** Letter badge for an answer index: A, B, C, D … */
export function answerLetter(index: number): string {
    return String.fromCharCode(65 + index);
}
