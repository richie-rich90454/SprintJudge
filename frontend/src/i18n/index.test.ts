import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { createElement, type ReactNode } from "react";
import { act, cleanup, renderHook } from "@testing-library/react";
import { detectLocale, applyStoredLocale, I18nProvider, useI18n, LOCALES } from "./index";
import { en } from "./en";
import { ar } from "./ar";

const storage = {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
};

let language = "en-US";
let backing: Record<string, string> = {};
let removed: { key: string; value: string } | null = null;

function removeFromAr(key: string): void {
    removed = { key, value: ar[key] };
    delete ar[key];
}

beforeEach(() => {
    backing = {};
    storage.getItem.mockReset();
    storage.setItem.mockReset();
    storage.removeItem.mockReset();
    storage.getItem.mockImplementation((key: string) => backing[key] ?? null);
    storage.setItem.mockImplementation((key: string, value: string) => {
        backing[key] = String(value);
    });
    language = "en-US";
    vi.spyOn(navigator, "language", "get").mockImplementation(() => language);
    vi.stubGlobal("localStorage", storage);
});

afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    if (removed) {
        ar[removed.key] = removed.value;
        removed = null;
    }
});

describe("locales", () => {
    it("exposes the supported locale metadata", () => {
        expect(LOCALES).toEqual([
            { code: "en", label: "English", dir: "ltr" },
            { code: "ar", label: "العربية", dir: "rtl" },
        ]);
    });
});

describe("detectLocale", () => {
    it("returns a valid stored locale", () => {
        storage.getItem.mockReturnValue("en");
        expect(detectLocale()).toBe("en");
        storage.getItem.mockReturnValue("ar");
        expect(detectLocale()).toBe("ar");
    });

    it("uses the navigator language when nothing valid is stored", () => {
        language = "ar-SA";
        expect(detectLocale()).toBe("ar");
        language = "en-GB";
        expect(detectLocale()).toBe("en");
    });

    it("falls back to English when storage throws", () => {
        storage.getItem.mockImplementation(() => {
            throw new Error("blocked");
        });
        expect(detectLocale()).toBe("en");
    });
});

describe("applyStoredLocale", () => {
    it("sets lang and dir for Arabic", () => {
        storage.getItem.mockReturnValue("ar");
        applyStoredLocale();
        expect(document.documentElement.lang).toBe("ar");
        expect(document.documentElement.dir).toBe("rtl");
    });

    it("sets lang and dir for English", () => {
        storage.getItem.mockReturnValue("en");
        applyStoredLocale();
        expect(document.documentElement.lang).toBe("en");
        expect(document.documentElement.dir).toBe("ltr");
    });
});

const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(I18nProvider, null, children);

describe("I18nProvider / useI18n", () => {
    it("throws when used outside the provider", () => {
        expect(() => renderHook(() => useI18n())).toThrow(/inside I18nProvider/);
    });

    it("exposes English strings and ltr direction by default", () => {
        const { result } = renderHook(() => useI18n(), { wrapper });
        expect(result.current.locale).toBe("en");
        expect(result.current.dir).toBe("ltr");
        expect(result.current.t("a11y.skip")).toBe("Skip to main content");
        expect(result.current.t("missing.key")).toBe("missing.key");
    });

    it("interpolates string and number variables", () => {
        en["test.greet"] = "Hi {name} #{n}";
        try {
            const { result } = renderHook(() => useI18n(), { wrapper });
            expect(result.current.t("test.greet", { name: "Ada", n: 2 })).toBe("Hi Ada #2");
        } finally {
            delete en["test.greet"];
        }
    });

    it("falls back to English for keys missing from the active dictionary", () => {
        removeFromAr("nav.home");
        storage.getItem.mockReturnValue("ar");
        const { result } = renderHook(() => useI18n(), { wrapper });
        expect(result.current.locale).toBe("ar");
        expect(result.current.dir).toBe("rtl");
        expect(result.current.t("nav.home")).toBe("Home");
        expect(result.current.t("a11y.skip")).toBe(ar["a11y.skip"]);
        expect(result.current.t("unknown")).toBe("unknown");
    });

    it("setLocale updates state, storage and document direction", () => {
        const { result } = renderHook(() => useI18n(), { wrapper });
        act(() => result.current.setLocale("ar"));
        expect(result.current.locale).toBe("ar");
        expect(result.current.dir).toBe("rtl");
        expect(storage.setItem).toHaveBeenCalledWith("oq-locale", "ar");
        expect(document.documentElement.dir).toBe("rtl");
    });

    it("setLocale still updates state when storage writes fail", () => {
        storage.setItem.mockImplementationOnce(() => {
            throw new Error("denied");
        });
        const { result } = renderHook(() => useI18n(), { wrapper });
        act(() => result.current.setLocale("ar"));
        expect(result.current.locale).toBe("ar");
    });
});
