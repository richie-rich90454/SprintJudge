import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";

function stubMatchMedia(matches: boolean) {
    Object.defineProperty(window, "matchMedia", {
        value: vi.fn().mockReturnValue({ matches }),
        writable: true,
        configurable: true,
    });
}

function metaContent(): string | null {
    return document.head.querySelector('meta[name="theme-color"]')?.getAttribute("content") ?? null;
}

async function fresh() {
    vi.resetModules();
    return await import("./useUIStore");
}

beforeEach(() => {
    localStorage.clear();
    document.head.querySelector('meta[name="theme-color"]')?.remove();
    document.documentElement.classList.remove("dark");
    stubMatchMedia(false);
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("useUIStore init reads", () => {
    test("defaults with empty storage", async () => {
        const { useUIStore } = await fresh();
        const s = useUIStore.getState();
        expect(s.theme).toBe("light");
        expect(s.sound).toBe("on");
        expect(s.motion).toBe("system");
        expect(s.pin).toBeNull();
    });

    test("persisted sound off honored", async () => {
        localStorage.setItem("oq-sound", "off");
        const { useUIStore } = await fresh();
        expect(useUIStore.getState().sound).toBe("off");
    });

    test("invalid sound falls back to on", async () => {
        localStorage.setItem("oq-sound", "loud");
        const { useUIStore } = await fresh();
        expect(useUIStore.getState().sound).toBe("on");
    });

    test("sound read error falls back to on", async () => {
        vi.spyOn(window.localStorage, "getItem").mockImplementation(() => {
            throw new Error("denied");
        });
        const { useUIStore } = await fresh();
        expect(useUIStore.getState().sound).toBe("on");
    });

    test("persisted motion full honored", async () => {
        localStorage.setItem("oq-motion", "full");
        const { useUIStore } = await fresh();
        expect(useUIStore.getState().motion).toBe("full");
    });

    test("persisted motion reduced honored", async () => {
        localStorage.setItem("oq-motion", "reduced");
        const { useUIStore } = await fresh();
        expect(useUIStore.getState().motion).toBe("reduced");
    });

    test("invalid motion falls back to system", async () => {
        localStorage.setItem("oq-motion", "wild");
        const { useUIStore } = await fresh();
        expect(useUIStore.getState().motion).toBe("system");
    });

    test("motion read error falls back to system", async () => {
        vi.spyOn(window.localStorage, "getItem").mockImplementation(() => {
            throw new Error("denied");
        });
        const { useUIStore } = await fresh();
        expect(useUIStore.getState().motion).toBe("system");
    });
});

describe("useUIStore theme lockdown", () => {
    test("setTheme always forces light even when dark passed", async () => {
        const { useUIStore } = await fresh();
        document.documentElement.classList.add("dark");
        useUIStore.getState().setTheme("dark");
        const s = useUIStore.getState();
        expect(s.theme).toBe("light");
        expect(localStorage.getItem("oq-theme")).toBe("light");
        expect(document.documentElement.classList.contains("dark")).toBe(false);
    });

    test("setTheme creates theme-color meta when missing", async () => {
        const { useUIStore } = await fresh();
        expect(document.head.querySelector('meta[name="theme-color"]')).toBeNull();
        useUIStore.getState().setTheme("light");
        expect(metaContent()).toBe("#fff0e4");
    });

    test("setTheme reuses existing meta element", async () => {
        const { useUIStore } = await fresh();
        useUIStore.getState().setTheme("light");
        const first = document.head.querySelector('meta[name="theme-color"]');
        useUIStore.getState().setTheme("light");
        expect(document.head.querySelector('meta[name="theme-color"]')).toBe(first);
        expect(metaContent()).toBe("#fff0e4");
    });

    test("applyStoredTheme removes dark class and sets meta", async () => {
        const { applyStoredTheme } = await fresh();
        document.documentElement.classList.add("dark");
        applyStoredTheme();
        expect(document.documentElement.classList.contains("dark")).toBe(false);
        expect(metaContent()).toBe("#fff0e4");
    });

    test("applyStoredTheme tolerates DOM errors", async () => {
        const { applyStoredTheme } = await fresh();
        vi.spyOn(document, "querySelector").mockImplementation(() => {
            throw new Error("no dom");
        });
        vi.spyOn(document, "createElement").mockImplementation(() => {
            throw new Error("no dom");
        });
        expect(() => applyStoredTheme()).not.toThrow();
    });

    test("watchSystemTheme syncs meta without throwing", async () => {
        const { watchSystemTheme } = await fresh();
        watchSystemTheme();
        expect(metaContent()).toBe("#fff0e4");
    });

    test("watchSystemTheme tolerates errors", async () => {
        const { watchSystemTheme } = await fresh();
        vi.spyOn(document, "querySelector").mockImplementation(() => {
            throw new Error("no dom");
        });
        vi.spyOn(document, "createElement").mockImplementation(() => {
            throw new Error("no dom");
        });
        expect(() => watchSystemTheme()).not.toThrow();
    });

    test("toggleTheme delegates to light", async () => {
        const { useUIStore } = await fresh();
        useUIStore.getState().toggleTheme();
        expect(useUIStore.getState().theme).toBe("light");
    });
});

describe("useUIStore sound and motion", () => {
    test("setSound persists on and off", async () => {
        const { useUIStore } = await fresh();
        useUIStore.getState().setSound("off");
        expect(useUIStore.getState().sound).toBe("off");
        expect(localStorage.getItem("oq-sound")).toBe("off");
        useUIStore.getState().setSound("on");
        expect(useUIStore.getState().sound).toBe("on");
    });

    test("setSound tolerates storage errors", async () => {
        const { useUIStore } = await fresh();
        vi.spyOn(window.localStorage, "setItem").mockImplementation(() => {
            throw new Error("denied");
        });
        useUIStore.getState().setSound("off");
        expect(useUIStore.getState().sound).toBe("off");
    });

    test("toggleSound flips on to off", async () => {
        const { useUIStore } = await fresh();
        useUIStore.getState().setSound("on");
        useUIStore.getState().toggleSound();
        expect(useUIStore.getState().sound).toBe("off");
    });

    test("toggleSound flips off to on", async () => {
        const { useUIStore } = await fresh();
        useUIStore.getState().setSound("off");
        useUIStore.getState().toggleSound();
        expect(useUIStore.getState().sound).toBe("on");
    });

    test("setMotion persists all three values", async () => {
        const { useUIStore } = await fresh();
        for (const m of ["full", "reduced", "system"] as const) {
            useUIStore.getState().setMotion(m);
            expect(useUIStore.getState().motion).toBe(m);
            expect(localStorage.getItem("oq-motion")).toBe(m);
        }
    });

    test("setMotion tolerates storage errors", async () => {
        const { useUIStore } = await fresh();
        vi.spyOn(window.localStorage, "setItem").mockImplementation(() => {
            throw new Error("denied");
        });
        useUIStore.getState().setMotion("reduced");
        expect(useUIStore.getState().motion).toBe("reduced");
    });

    test("setPin sets and clears", async () => {
        const { useUIStore } = await fresh();
        useUIStore.getState().setPin("1234");
        expect(useUIStore.getState().pin).toBe("1234");
        useUIStore.getState().setPin(null);
        expect(useUIStore.getState().pin).toBeNull();
    });
});

describe("motionReduced", () => {
    test("true when reduced stored even if OS disagrees", async () => {
        const { motionReduced } = await fresh();
        stubMatchMedia(false);
        localStorage.setItem("oq-motion", "reduced");
        expect(motionReduced()).toBe(true);
    });

    test("false when full stored even if OS prefers reduced", async () => {
        const { motionReduced } = await fresh();
        stubMatchMedia(true);
        localStorage.setItem("oq-motion", "full");
        expect(motionReduced()).toBe(false);
    });

    test("follows OS when system stored and OS prefers reduced", async () => {
        const { motionReduced } = await fresh();
        stubMatchMedia(true);
        localStorage.setItem("oq-motion", "system");
        expect(motionReduced()).toBe(true);
    });

    test("false when system stored and OS does not prefer reduced", async () => {
        const { motionReduced } = await fresh();
        stubMatchMedia(false);
        localStorage.removeItem("oq-motion");
        expect(motionReduced()).toBe(false);
    });

    test("false when storage throws", async () => {
        const { motionReduced } = await fresh();
        vi.spyOn(window.localStorage, "getItem").mockImplementation(() => {
            throw new Error("denied");
        });
        expect(motionReduced()).toBe(false);
    });

    test("false when matchMedia throws", async () => {
        const { motionReduced } = await fresh();
        localStorage.removeItem("oq-motion");
        Object.defineProperty(window, "matchMedia", {
            value: () => {
                throw new Error("no media");
            },
            writable: true,
            configurable: true,
        });
        expect(motionReduced()).toBe(false);
    });
});

describe("useUIStore preference workflows", () => {
    test("sound off survives a reload then toggles back on across reloads", async () => {
        const first = await fresh();
        first.useUIStore.getState().setSound("off");
        const second = await fresh();
        expect(second.useUIStore.getState().sound).toBe("off");
        second.useUIStore.getState().toggleSound();
        expect(second.useUIStore.getState().sound).toBe("on");
        const third = await fresh();
        expect(third.useUIStore.getState().sound).toBe("on");
    });

    test("double toggle returns to the original with persistence", async () => {
        const mod = await fresh();
        mod.useUIStore.getState().setSound("on");
        mod.useUIStore.getState().toggleSound();
        mod.useUIStore.getState().toggleSound();
        expect(mod.useUIStore.getState().sound).toBe("on");
        expect(localStorage.getItem("oq-sound")).toBe("on");
        const reloaded = await fresh();
        expect(reloaded.useUIStore.getState().sound).toBe("on");
    });

    test("motion chain persists each step and reload reads the last", async () => {
        const mod = await fresh();
        for (const m of ["reduced", "system", "full"] as const) {
            mod.useUIStore.getState().setMotion(m);
            expect(localStorage.getItem("oq-motion")).toBe(m);
        }
        const reloaded = await fresh();
        expect(reloaded.useUIStore.getState().motion).toBe("full");
        reloaded.useUIStore.getState().setMotion("reduced");
        const again = await fresh();
        expect(again.useUIStore.getState().motion).toBe("reduced");
    });

    test("pin is session-only and never survives a reload", async () => {
        const mod = await fresh();
        mod.useUIStore.getState().setPin("4242");
        expect(mod.useUIStore.getState().pin).toBe("4242");
        const reloaded = await fresh();
        expect(reloaded.useUIStore.getState().pin).toBeNull();
    });

    test("pin overwrite chain keeps the latest then clears", async () => {
        const mod = await fresh();
        mod.useUIStore.getState().setPin("1111");
        mod.useUIStore.getState().setPin("2222");
        expect(mod.useUIStore.getState().pin).toBe("2222");
        mod.useUIStore.getState().setPin(null);
        expect(mod.useUIStore.getState().pin).toBeNull();
    });

    test("theme dark request persists as light and reloads light", async () => {
        const mod = await fresh();
        mod.useUIStore.getState().setTheme("dark");
        expect(localStorage.getItem("oq-theme")).toBe("light");
        const reloaded = await fresh();
        expect(reloaded.useUIStore.getState().theme).toBe("light");
    });

    test("toggleTheme twice stays light with the meta intact", async () => {
        const mod = await fresh();
        mod.useUIStore.getState().toggleTheme();
        mod.useUIStore.getState().toggleTheme();
        expect(mod.useUIStore.getState().theme).toBe("light");
        expect(metaContent()).toBe("#fff0e4");
    });

    test("sound and motion matrix persists jointly across a reload", async () => {
        const mod = await fresh();
        const combos = [
            ["off", "reduced"],
            ["off", "full"],
            ["on", "system"],
        ] as const;
        for (const [sound, motion] of combos) {
            mod.useUIStore.getState().setSound(sound);
            mod.useUIStore.getState().setMotion(motion);
            const reloaded = await fresh();
            expect(reloaded.useUIStore.getState().sound).toBe(sound);
            expect(reloaded.useUIStore.getState().motion).toBe(motion);
        }
    });

    test("motionReduced tracks the full preference chain against the OS", async () => {
        const mod = await fresh();
        stubMatchMedia(true);
        mod.useUIStore.getState().setMotion("full");
        expect(mod.motionReduced()).toBe(false);
        mod.useUIStore.getState().setMotion("reduced");
        expect(mod.motionReduced()).toBe(true);
        mod.useUIStore.getState().setMotion("system");
        expect(mod.motionReduced()).toBe(true);
        stubMatchMedia(false);
        expect(mod.motionReduced()).toBe(false);
    });

    test("repeated identical sets are idempotent", async () => {
        const mod = await fresh();
        mod.useUIStore.getState().setSound("off");
        mod.useUIStore.getState().setSound("off");
        mod.useUIStore.getState().setMotion("system");
        mod.useUIStore.getState().setMotion("system");
        expect(mod.useUIStore.getState().sound).toBe("off");
        expect(mod.useUIStore.getState().motion).toBe("system");
    });

    test("subscribe notifies on every preference change in order then stops", async () => {
        const mod = await fresh();
        const events: string[] = [];
        const unsub = mod.useUIStore.subscribe((s) => events.push(`${s.sound}/${s.motion}/${s.pin}`));
        mod.useUIStore.getState().setSound("off");
        mod.useUIStore.getState().setMotion("reduced");
        mod.useUIStore.getState().setPin("9999");
        expect(events).toEqual(["off/system/null", "off/reduced/null", "off/reduced/9999"]);
        unsub();
        mod.useUIStore.getState().setSound("on");
        expect(events).toHaveLength(3);
    });

    test("theme meta content stays locked across sets and reloads", async () => {
        const mod = await fresh();
        mod.useUIStore.getState().setTheme("light");
        mod.useUIStore.getState().setTheme("dark");
        expect(metaContent()).toBe("#fff0e4");
        expect(document.head.querySelectorAll('meta[name="theme-color"]')).toHaveLength(1);
    });

    test("corrupt persisted values fall back then accept fresh sets", async () => {
        localStorage.setItem("oq-sound", "loud");
        localStorage.setItem("oq-motion", "turbo");
        const mod = await fresh();
        expect(mod.useUIStore.getState().sound).toBe("on");
        expect(mod.useUIStore.getState().motion).toBe("system");
        mod.useUIStore.getState().setSound("off");
        mod.useUIStore.getState().setMotion("full");
        const reloaded = await fresh();
        expect(reloaded.useUIStore.getState().sound).toBe("off");
        expect(reloaded.useUIStore.getState().motion).toBe("full");
    });

    test("full preference reset flow returns to defaults", async () => {
        const mod = await fresh();
        mod.useUIStore.getState().setSound("off");
        mod.useUIStore.getState().setMotion("reduced");
        mod.useUIStore.getState().setPin("1234");
        mod.useUIStore.getState().setTheme("dark");
        localStorage.clear();
        document.head.querySelector('meta[name="theme-color"]')?.remove();
        const reloaded = await fresh();
        expect(reloaded.useUIStore.getState().sound).toBe("on");
        expect(reloaded.useUIStore.getState().motion).toBe("system");
        expect(reloaded.useUIStore.getState().pin).toBeNull();
        expect(reloaded.useUIStore.getState().theme).toBe("light");
    });
});
