import { describe, it, expect } from "vitest";
import { readRichStem, isSafeMediaUrl, latexToText } from "./richStem";

describe("readRichStem", () => {
    it("returns an empty object for missing or non-object configs", () => {
        expect(readRichStem(null)).toEqual({});
        expect(readRichStem("text")).toEqual({});
    });

    it("returns an empty object when rich is missing or invalid", () => {
        expect(readRichStem({})).toEqual({});
        expect(readRichStem({ rich: null })).toEqual({});
        expect(readRichStem({ rich: "not-an-object" })).toEqual({});
    });

    it("returns the rich object when present", () => {
        const rich = { imageUrl: "/a.png", audioUrl: "/b.mp3" };
        expect(readRichStem({ rich })).toBe(rich);
    });
});

describe("isSafeMediaUrl", () => {
    it("accepts http(s) urls and trims surrounding whitespace", () => {
        expect(isSafeMediaUrl(" https://example.test/a.png ", "image")).toBe(true);
        expect(isSafeMediaUrl("HTTPS://example.test/a.mp3", "audio")).toBe(true);
    });

    it("accepts same-origin paths but rejects protocol-relative and relative urls", () => {
        expect(isSafeMediaUrl("/media/a.png", "image")).toBe(true);
        expect(isSafeMediaUrl("//evil.test/a.png", "image")).toBe(false);
        expect(isSafeMediaUrl("media/a.png", "image")).toBe(false);
        expect(isSafeMediaUrl("javascript:alert(1)", "image")).toBe(false);
    });

    it("accepts only inline raster audio/image data", () => {
        expect(isSafeMediaUrl("data:image/png;base64,AAAA", "image")).toBe(true);
        expect(isSafeMediaUrl("data:image/png;base64,@@@", "image")).toBe(false);
        expect(isSafeMediaUrl("data:audio/mpeg;base64,AAAA", "audio")).toBe(true);
        expect(isSafeMediaUrl("data:image/png;base64,AAAA", "audio")).toBe(false);
    });
});

describe("latexToText", () => {
    it("strips dollar delimiters and replaces known symbols", () => {
        expect(latexToText("$\\times$")).toBe("×");
    });

    it("handles delimiters, exponents, subscripts, symbols and escaping", () => {
        expect(latexToText("\\left x^{2}_{i} \\sum \\foobar \\{y\\}   z\\right")).toBe(
            "x↑2↓i Σ \\foobar {y} z",
        );
    });
});
