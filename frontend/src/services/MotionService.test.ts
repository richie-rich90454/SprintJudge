import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("framer-motion", () => ({
    animate: vi.fn((...args: unknown[]) => {
        const opts = args[args.length - 1] as {
            onComplete?: () => void;
            onUpdate?: (v: number) => void;
        };
        if (typeof args[1] === "number" && typeof opts?.onUpdate === "function") {
            opts.onUpdate(args[1]);
        }
        opts?.onComplete?.();
        return { stop: vi.fn() };
    }),
}));

import { animate } from "framer-motion";
import { motion, MotionService } from "./MotionService";

const anim = () => vi.mocked(animate);

function stubMatchMedia(matches: boolean) {
    Object.defineProperty(window, "matchMedia", {
        value: vi.fn().mockReturnValue({ matches }),
        writable: true,
        configurable: true,
    });
}

function stops(): Array<ReturnType<typeof vi.fn>> {
    return anim().mock.results.map((r) => (r.value as { stop: ReturnType<typeof vi.fn> }).stop);
}

beforeEach(() => {
    stubMatchMedia(false);
    motion.setReduced(false);
    anim().mockClear();
});

afterEach(() => {
    motion.setReduced(false);
    vi.restoreAllMocks();
});

describe("MotionService construction", () => {
    test("reduced when the OS prefers reduced motion", () => {
        stubMatchMedia(true);
        const s = new MotionService();
        const el = document.createElement("div");
        s.enter(el, "card");
        expect(anim()).not.toHaveBeenCalled();
    });

    test("full motion when the OS does not prefer reduced", () => {
        stubMatchMedia(false);
        const s = new MotionService();
        s.enter(document.createElement("div"), "card");
        expect(anim()).toHaveBeenCalledTimes(1);
    });

    test("full motion when matchMedia throws", () => {
        Object.defineProperty(window, "matchMedia", {
            value: () => {
                throw new Error("no media");
            },
            writable: true,
            configurable: true,
        });
        const s = new MotionService();
        s.enter(document.createElement("div"), "card");
        expect(anim()).toHaveBeenCalledTimes(1);
    });
});

describe("MotionService enter presets", () => {
    test("null element is a no-op", () => {
        motion.enter(null, "card");
        expect(anim()).not.toHaveBeenCalled();
    });

    test("reduced mode skips enter", () => {
        motion.setReduced(true);
        motion.enter(document.createElement("div"), "card");
        expect(anim()).not.toHaveBeenCalled();
    });

    test("card preset slides up and fades in", () => {
        const el = document.createElement("div");
        motion.enter(el, "card");
        expect(anim()).toHaveBeenCalledWith(
            el,
            { y: [24, 0], opacity: [0, 1] },
            expect.objectContaining({ duration: 0.5, ease: "easeOut" }),
        );
    });

    test("page preset uses a shorter rise", () => {
        const el = document.createElement("div");
        motion.enter(el, "page");
        expect(anim()).toHaveBeenCalledWith(
            el,
            { y: [12, 0], opacity: [0, 1] },
            expect.objectContaining({ duration: 0.4 }),
        );
    });

    test("modal preset drops from further down", () => {
        const el = document.createElement("div");
        motion.enter(el, "modal");
        expect(anim()).toHaveBeenCalledWith(
            el,
            { y: [32, 0], opacity: [0, 1] },
            expect.objectContaining({ duration: 0.45 }),
        );
    });

    test("bar preset scales horizontally", () => {
        const el = document.createElement("div");
        motion.enter(el, "bar");
        expect(anim()).toHaveBeenCalledWith(
            el,
            { scaleX: [0, 1] },
            expect.objectContaining({ duration: 0.6, ease: "easeInOut" }),
        );
    });

    test("pin preset springs from above", () => {
        const el = document.createElement("div");
        motion.enter(el, "pin");
        expect(anim()).toHaveBeenCalledWith(
            el,
            { y: [-14, 0], opacity: [0, 1] },
            expect.objectContaining({ ease: "backOut" }),
        );
    });

    test("podium preset rises by percent", () => {
        const el = document.createElement("div");
        motion.enter(el, "podium");
        expect(anim()).toHaveBeenCalledWith(
            el,
            { yPercent: [-60, 0], opacity: [0, 1] },
            expect.objectContaining({ duration: 0.7 }),
        );
    });

    test("ticker preset slides from the left", () => {
        const el = document.createElement("div");
        motion.enter(el, "ticker");
        expect(anim()).toHaveBeenCalledWith(
            el,
            { x: [-28, 0], opacity: [0, 1] },
            expect.objectContaining({ duration: 0.4 }),
        );
    });

    test("enter clears inline transform and opacity when done", () => {
        const el = document.createElement("div");
        el.style.transform = "translateY(24px)";
        el.style.opacity = "0";
        motion.enter(el, "card");
        expect(el.style.transform).toBe("");
        expect(el.style.opacity).toBe("");
    });

    test("bar completion clears opacity but keeps transform", () => {
        const el = document.createElement("div");
        el.style.transform = "scaleX(0.5)";
        el.style.opacity = "0.5";
        motion.enter(el, "bar");
        expect(el.style.opacity).toBe("");
        expect(el.style.transform).toBe("scaleX(0.5)");
    });
});

describe("MotionService staggerIn", () => {
    test("null container is a no-op", () => {
        motion.staggerIn(null, ".item");
        expect(anim()).not.toHaveBeenCalled();
    });

    test("reduced mode skips stagger", () => {
        motion.setReduced(true);
        const c = document.createElement("div");
        c.innerHTML = "<span class='item'></span>";
        motion.staggerIn(c, ".item");
        expect(anim()).not.toHaveBeenCalled();
    });

    test("empty match list animates nothing", () => {
        const c = document.createElement("div");
        motion.staggerIn(c, ".missing");
        expect(anim()).not.toHaveBeenCalled();
    });

    test("each child animates with an incremental delay", () => {
        const c = document.createElement("div");
        c.innerHTML = "<span class='item'></span><span class='item'></span><span class='item'></span>";
        motion.staggerIn(c, ".item", 0.1);
        expect(anim()).toHaveBeenCalledTimes(3);
        expect(anim()).toHaveBeenNthCalledWith(
            1,
            expect.anything(),
            { x: [-14, 0], opacity: [0, 1] },
            expect.objectContaining({ delay: 0 }),
        );
        expect(anim()).toHaveBeenNthCalledWith(
            3,
            expect.anything(),
            expect.anything(),
            expect.objectContaining({ delay: 0.2 }),
        );
    });

    test("default offset is 0.05", () => {
        const c = document.createElement("div");
        c.innerHTML = "<span class='item'></span><span class='item'></span>";
        motion.staggerIn(c, ".item");
        expect(anim()).toHaveBeenNthCalledWith(
            2,
            expect.anything(),
            expect.anything(),
            expect.objectContaining({ delay: 0.05 }),
        );
    });

    test("staggered nodes are cleared when done", () => {
        const c = document.createElement("div");
        c.innerHTML = "<span class='item' style='opacity: 0'></span>";
        motion.staggerIn(c, ".item");
        expect((c.firstElementChild as HTMLElement).style.opacity).toBe("");
    });
});

describe("MotionService pulse and shake", () => {
    test("pulse scales up and back", () => {
        const el = document.createElement("div");
        motion.pulse(el);
        expect(anim()).toHaveBeenCalledWith(
            el,
            { scale: [1, 1.08, 1] },
            expect.objectContaining({ duration: 0.36 }),
        );
    });

    test("pulse clears inline styles when done", () => {
        const el = document.createElement("div");
        el.style.transform = "scale(1.08)";
        motion.pulse(el);
        expect(el.style.transform).toBe("");
    });

    test("pulse ignores null", () => {
        motion.pulse(null);
        expect(anim()).not.toHaveBeenCalled();
    });

    test("pulse is skipped in reduced mode", () => {
        motion.setReduced(true);
        motion.pulse(document.createElement("div"));
        expect(anim()).not.toHaveBeenCalled();
    });

    test("shake wobbles horizontally", () => {
        const el = document.createElement("div");
        motion.shake(el);
        expect(anim()).toHaveBeenCalledWith(
            el,
            { x: [0, 7, -7, 7, -7, 4, -4, 0] },
            expect.objectContaining({ duration: 0.42 }),
        );
    });

    test("shake clears inline styles when done", () => {
        const el = document.createElement("div");
        el.style.transform = "translateX(7px)";
        motion.shake(el);
        expect(el.style.transform).toBe("");
    });

    test("shake ignores null", () => {
        motion.shake(null);
        expect(anim()).not.toHaveBeenCalled();
    });

    test("shake is skipped in reduced mode", () => {
        motion.setReduced(true);
        motion.shake(document.createElement("div"));
        expect(anim()).not.toHaveBeenCalled();
    });
});

describe("MotionService countUp", () => {
    test("null container is a no-op", () => {
        motion.countUp(null);
        expect(anim()).not.toHaveBeenCalled();
    });

    test("reduced mode skips counting", () => {
        motion.setReduced(true);
        const c = document.createElement("div");
        c.innerHTML = "<span data-score='42'>0</span>";
        motion.countUp(c);
        expect(anim()).not.toHaveBeenCalled();
        expect(c.textContent).toBe("0");
    });

    test("score elements count to their target", () => {
        const c = document.createElement("div");
        c.innerHTML = "<span data-score='42'>0</span><span data-score='7'>0</span>";
        motion.countUp(c);
        expect(anim()).toHaveBeenCalledTimes(2);
        const spans = c.querySelectorAll("span");
        expect(spans[0].textContent).toBe("42");
        expect(spans[1].textContent).toBe("7");
    });

    test("element without a score counts to zero via custom selector", () => {
        const c = document.createElement("div");
        c.innerHTML = "<span>9</span>";
        motion.countUp(c, "span");
        expect(c.querySelector("span")?.textContent).toBe("0");
    });

    test("custom selector is honored", () => {
        const c = document.createElement("div");
        c.innerHTML = "<b class='pts' data-score='5'>0</b><span data-score='99'>0</span>";
        motion.countUp(c, ".pts");
        expect(anim()).toHaveBeenCalledTimes(1);
        expect(c.querySelector("b")?.textContent).toBe("5");
        expect(c.querySelector("span")?.textContent).toBe("0");
    });

    test("non-numeric score renders NaN rather than crashing", () => {
        const c = document.createElement("div");
        c.innerHTML = "<span data-score='abc'>0</span>";
        motion.countUp(c);
        expect(c.querySelector("span")?.textContent).toBe("NaN");
    });
});

describe("MotionService killFor", () => {
    test("null element is a safe no-op", () => {
        expect(() => motion.killFor(null)).not.toThrow();
    });

    test("element with no animations is a safe no-op", () => {
        expect(() => motion.killFor(document.createElement("div"))).not.toThrow();
    });

    test("stops tracked animations on the element", () => {
        const el = document.createElement("div");
        motion.enter(el, "card");
        motion.pulse(el);
        motion.killFor(el);
        const stopped = stops();
        expect(stopped).toHaveLength(2);
        expect(stopped[0]).toHaveBeenCalled();
        expect(stopped[1]).toHaveBeenCalled();
    });

    test("stops animations on descendants too", () => {
        const c = document.createElement("div");
        c.innerHTML = "<span class='item'></span><span class='item'></span>";
        motion.staggerIn(c, ".item");
        motion.killFor(c);
        expect(stops().every((s) => s.mock.calls.length === 1)).toBe(true);
    });

    test("second kill finds nothing to stop", () => {
        const el = document.createElement("div");
        motion.enter(el, "card");
        motion.killFor(el);
        anim().mockClear();
        motion.killFor(el);
        expect(anim()).not.toHaveBeenCalled();
    });
});
