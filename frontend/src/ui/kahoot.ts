/** Brutal lockdown palette — single source. Indexes match theme.ts myColor. */
export const myColor = [
    "#fff0e4",
    "#ffe0cf",
    "#fac0a1",
    "#f69e6e",
    "#f28043",
    "#f06e27",
    "#f06418",
    "#d6530c",
    "#bf4906",
    "#a73c00",
] as const;

/** Flat answer fills — all inside myColor, no blue/green/yellow/pink. */
export const KAHOOT_COLORS = {
    blue: "var(--oq-answer-0)",
    green: "var(--oq-answer-1)",
    yellow: "var(--oq-answer-2)",
    pink: "var(--oq-answer-3)",
} as const;

export const ANSWER_PALETTE = [
    KAHOOT_COLORS.blue,
    KAHOOT_COLORS.green,
    KAHOOT_COLORS.yellow,
    KAHOOT_COLORS.pink,
];

export function answerColor(index: number): string {
    return ANSWER_PALETTE[index % ANSWER_PALETTE.length];
}

/** True for host-led live modes where the board stays frozen mid-round. */
export function boardDelayedForMode(mode: string | null | undefined): boolean {
    return mode === "STANDARD" || mode === "TEAM" || mode === "BATTLE" || mode === "AUTO_PILOT";
}

/** Letter badge for an answer index: A, B, C, D … */
export function answerLetter(index: number): string {
    return String.fromCharCode(65 + index);
}

/** Shape per answer slot: triangle, diamond, circle, square. */
export const ANSWER_SHAPES = ["triangle", "diamond", "circle", "square"] as const;

/** Returns the shape for an answer index (wraps if > 3). */
export function answerShape(index: number): (typeof ANSWER_SHAPES)[number] {
    return ANSWER_SHAPES[index % ANSWER_SHAPES.length];
}
