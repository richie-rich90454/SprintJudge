import { describe, expect, test } from "vitest";
import { friendlyError } from "./friendlyError";

describe("friendlyError", () => {
    test("maps invalid-PIN and unknown-room errors", () => {
        expect(friendlyError("Invalid PIN")).toMatch(/doesn't match/);
        expect(friendlyError("No active room for PIN 123456")).toMatch(/doesn't match/);
        expect(friendlyError("room not found")).toMatch(/doesn't match/);
    });

    test("maps ended and full rooms", () => {
        expect(friendlyError("This game has ended")).toBe("This game has ended.");
        expect(friendlyError("Game over")).toBe("This game has ended.");
        expect(friendlyError("Room is full")).toMatch(/full/);
    });

    test("maps rate limits, host checks and locked rounds", () => {
        expect(friendlyError("Too many attempts. Try again shortly.")).toMatch(/Too many/);
        expect(friendlyError("rate limited")).toMatch(/Too many/);
        expect(friendlyError("Forbidden: host authentication required")).toMatch(/host/);
        expect(friendlyError("Round is locked")).toMatch(/locked/);
    });

    test("maps rejoin, seat and host-left errors", () => {
        expect(friendlyError("rejoin token expired")).toMatch(/session changed/);
        expect(
            friendlyError(
                "The host left the game. Ask them to reopen the host screen to continue.",
            ),
        ).toMatch(/host left/);
        expect(
            friendlyError(
                "This game is open in another tab. Close that tab and reload to continue.",
            ),
        ).toMatch(/another tab/);
    });

    test("keeps short safe messages verbatim", () => {
        expect(friendlyError("Unsupported language")).toBe("Unsupported language");
        expect(friendlyError("64KB limit")).toBe("64KB limit");
    });

    test("hides internal ids, long and non-string messages", () => {
        expect(friendlyError("Quiz not found: q-12345-abcd")).toBe(
            "Something went wrong. Please try again.",
        );
        expect(friendlyError("Unknown player: 550e8400-e29b-41d4-a716-446655440000")).toBe(
            "Something went wrong. Please try again.",
        );
        expect(friendlyError("x".repeat(100))).toBe("Something went wrong. Please try again.");
        expect(friendlyError(null)).toBe("Something went wrong. Please try again.");
        expect(friendlyError(42)).toBe("Something went wrong. Please try again.");
        expect(friendlyError("   ")).toBe("Something went wrong. Please try again.");
    });
});
