/**
 * Backend WebSocket errors can contain internal ids or implementation text.
 * Students and hosts see a fixed, friendly message instead; unknown errors
 * fall back to a generic retry prompt rather than leaking server details.
 */
const RULES: ReadonlyArray<readonly [RegExp, string]> = [
    [/invalid pin|no active room|unknown pin|room not found/i,
        "That PIN doesn't match an active game. Check the code and try again."],
    [/game (?:has )?ended|game over|already ended/i,
        "This game has ended."],
    [/room is full|game is full|maximum players/i,
        "This game is full. Ask the host for a new code."],
    [/too many attempts|rate.?limit/i,
        "Too many attempts. Please wait a moment and try again."],
    [/forbidden|admin authentication|host role|not the host/i,
        "That action is only available to the host."],
    [/round is locked|not accepting|already locked/i,
        "That round is already locked."],
    [/rejoin|seat|token/i,
        "Your session changed. Reload the page to rejoin."],
    [/host left|host (?:has )?(?:left|disconnected)/i,
        "The host left the game. Ask them to reopen the host screen to continue."],
    [/another tab/i,
        "This game is open in another tab. Close that tab and reload to continue."],
];

const FALLBACK = "Something went wrong. Please try again.";

export function friendlyError(message: unknown): string {
    if (typeof message !== "string" || message.trim().length === 0) return FALLBACK;
    for (const [pattern, friendly] of RULES) {
        if (pattern.test(message)) return friendly;
    }
    // Messages that are already short and free of internal identifiers are
    // safe to show verbatim (e.g. "Unsupported language", "64KB limit").
    if (message.length <= 48 && !/[0-9a-f]{8}-[0-9a-f]{4}|:\s*\S+-\S+|PIN\s+\d/i.test(message)) {
        return message;
    }
    return FALLBACK;
}
