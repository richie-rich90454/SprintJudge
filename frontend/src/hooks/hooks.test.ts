import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import React, { act } from "react";
import { createRoot, Root } from "react-dom/client";

const motionMock = vi.hoisted(() => ({
    enter: vi.fn(),
    staggerIn: vi.fn(),
    killFor: vi.fn(),
}));

vi.mock("../services/MotionService", () => ({ motion: motionMock }));

import { useVirtualWindow } from "./useVirtualWindow";
import { useEnter, useStaggerIn } from "./useMotion";
import type { MotionPreset } from "../services/MotionService";

(globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true;

let captured = { start: -1, end: -1 };

function WindowProbe(props: { total: number; rowHeight: number; overscan?: number; viewportH?: number }) {
    const { ref, start, end } = useVirtualWindow(props.total, props.rowHeight, props.overscan, props.viewportH);
    captured = { start, end };
    return React.createElement("div", { ref });
}

function BareProbe() {
    useVirtualWindow(10, 20);
    return null;
}

function EnterProbe(props: { preset: MotionPreset; deps?: unknown[] }) {
    const ref = useEnter<HTMLDivElement>(props.preset, props.deps);
    return React.createElement("div", { ref });
}

function EnterDefaultProbe() {
    const ref = useEnter<HTMLDivElement>("card");
    return React.createElement("div", { ref });
}

function StaggerProbe(props: { selector: string; deps?: unknown[]; offset?: number }) {
    const ref = useStaggerIn<HTMLDivElement>(props.selector, props.deps, props.offset);
    return React.createElement("div", { ref });
}

function StaggerMinimalProbe() {
    const ref = useStaggerIn<HTMLDivElement>(".item");
    return React.createElement("div", { ref });
}

function render(node: React.ReactElement): { host: HTMLElement; root: Root } {
    const host = document.createElement("div");
    document.body.appendChild(host);
    const root = createRoot(host);
    act(() => {
        root.render(node);
    });
    return { host, root };
}

function cleanup(host: HTMLElement, root: Root) {
    act(() => {
        root.unmount();
    });
    host.remove();
}

function scrollInner(host: HTMLElement, top: number) {
    const inner = host.firstChild as HTMLDivElement;
    inner.scrollTop = top;
    act(() => {
        inner.dispatchEvent(new Event("scroll", { bubbles: true }));
    });
}

beforeEach(() => {
    vi.clearAllMocks();
    captured = { start: -1, end: -1 };
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("useVirtualWindow", () => {
    test("small lists show every row", () => {
        const { host, root } = render(React.createElement(WindowProbe, { total: 5, rowHeight: 20 }));
        expect(captured).toEqual({ start: 0, end: 5 });
        cleanup(host, root);
    });

    test("large lists clip to the viewport window", () => {
        const { host, root } = render(React.createElement(WindowProbe, { total: 100, rowHeight: 20 }));
        expect(captured).toEqual({ start: 0, end: 35 });
        cleanup(host, root);
    });

    test("scrolling advances the window", () => {
        const { host, root } = render(React.createElement(WindowProbe, { total: 100, rowHeight: 20 }));
        scrollInner(host, 400);
        expect(captured).toEqual({ start: 14, end: 49 });
        cleanup(host, root);
    });

    test("scroll near the top clamps the start at zero", () => {
        const { host, root } = render(React.createElement(WindowProbe, { total: 100, rowHeight: 20 }));
        scrollInner(host, 10);
        expect(captured.start).toBe(0);
        cleanup(host, root);
    });

    test("custom overscan and viewport shrink the window", () => {
        const { host, root } = render(
            React.createElement(WindowProbe, { total: 100, rowHeight: 20, overscan: 2, viewportH: 100 }),
        );
        expect(captured).toEqual({ start: 0, end: 9 });
        scrollInner(host, 200);
        expect(captured.start).toBe(8);
        cleanup(host, root);
    });

    test("end clamps at the total near the bottom", () => {
        const { host, root } = render(React.createElement(WindowProbe, { total: 40, rowHeight: 20 }));
        scrollInner(host, 10000);
        expect(captured.end).toBe(40);
        cleanup(host, root);
    });

    test("unmount detaches the scroll listener", () => {
        const { host, root } = render(React.createElement(WindowProbe, { total: 100, rowHeight: 20 }));
        const inner = host.firstChild as HTMLDivElement;
        cleanup(host, root);
        expect(() => inner.dispatchEvent(new Event("scroll", { bubbles: true }))).not.toThrow();
    });

    test("unmounted ref short-circuits the effect", () => {
        const { host, root } = render(React.createElement(BareProbe));
        expect(captured).toEqual({ start: -1, end: -1 });
        cleanup(host, root);
    });

    test("row height change resubscribes with a new window", () => {
        const { host, root } = render(React.createElement(WindowProbe, { total: 100, rowHeight: 20 }));
        act(() => {
            root.render(React.createElement(WindowProbe, { total: 100, rowHeight: 10 }));
        });
        expect(captured).toEqual({ start: 0, end: 58 });
        cleanup(host, root);
    });
});

describe("useEnter", () => {
    test("enters with the element and preset on mount", () => {
        const { host, root } = render(React.createElement(EnterProbe, { preset: "card", deps: [] }));
        expect(motionMock.enter).toHaveBeenCalledTimes(1);
        const [el, preset] = motionMock.enter.mock.calls[0] as unknown[];
        expect(el).toBe(host.firstChild);
        expect(preset).toBe("card");
        cleanup(host, root);
    });

    test("kills the tween on unmount", () => {
        const { host, root } = render(React.createElement(EnterProbe, { preset: "modal", deps: [] }));
        const el = host.firstChild;
        cleanup(host, root);
        expect(motionMock.killFor).toHaveBeenCalledWith(el);
    });

    test("re-runs when deps change", () => {
        const { host, root } = render(React.createElement(EnterProbe, { preset: "page", deps: ["a"] }));
        act(() => {
            root.render(React.createElement(EnterProbe, { preset: "page", deps: ["b"] }));
        });
        expect(motionMock.enter).toHaveBeenCalledTimes(2);
        cleanup(host, root);
    });

    test("works without explicit deps", () => {
        const { host, root } = render(React.createElement(EnterDefaultProbe));
        expect(motionMock.enter).toHaveBeenCalledWith(host.firstChild, "card");
        cleanup(host, root);
    });
});

describe("useStaggerIn", () => {
    test("staggers with selector and custom offset", () => {
        const { host, root } = render(React.createElement(StaggerProbe, { selector: ".item", deps: [], offset: 0.1 }));
        expect(motionMock.staggerIn).toHaveBeenCalledWith(host.firstChild, ".item", 0.1);
        cleanup(host, root);
    });

    test("defaults offset to 0.05", () => {
        const { host, root } = render(React.createElement(StaggerMinimalProbe));
        expect(motionMock.staggerIn).toHaveBeenCalledWith(host.firstChild, ".item", 0.05);
        cleanup(host, root);
    });

    test("kills the tweens on unmount", () => {
        const { host, root } = render(React.createElement(StaggerProbe, { selector: ".item", deps: [] }));
        cleanup(host, root);
        expect(motionMock.killFor).toHaveBeenCalled();
    });
});

describe("useVirtualWindow edges", () => {
    test("zero total renders an empty window", () => {
        const { host, root } = render(React.createElement(WindowProbe, { total: 0, rowHeight: 20 }));
        expect(captured).toEqual({ start: 0, end: 0 });
        cleanup(host, root);
    });

    test("zero overscan narrows the window to the bare viewport", () => {
        const { host, root } = render(
            React.createElement(WindowProbe, { total: 100, rowHeight: 20, overscan: 0, viewportH: 460 }),
        );
        expect(captured).toEqual({ start: 0, end: 23 });
        cleanup(host, root);
    });

    test("scroll down then back up returns the start to zero", () => {
        const { host, root } = render(React.createElement(WindowProbe, { total: 100, rowHeight: 20 }));
        scrollInner(host, 400);
        expect(captured.start).toBe(14);
        scrollInner(host, 0);
        expect(captured).toEqual({ start: 0, end: 35 });
        cleanup(host, root);
    });

    test("three scroll stops walk the window forward", () => {
        const { host, root } = render(React.createElement(WindowProbe, { total: 200, rowHeight: 20 }));
        const starts: number[] = [];
        for (const top of [200, 600, 1200]) {
            scrollInner(host, top);
            starts.push(captured.start);
        }
        expect(starts).toEqual([4, 24, 54]);
        cleanup(host, root);
    });

    test("shrinking the total clamps the end mid-life", () => {
        const { host, root } = render(React.createElement(WindowProbe, { total: 100, rowHeight: 20 }));
        act(() => {
            root.render(React.createElement(WindowProbe, { total: 10, rowHeight: 20 }));
        });
        expect(captured).toEqual({ start: 0, end: 10 });
        cleanup(host, root);
    });

    test("zero viewport height shows only the overscan rows", () => {
        const { host, root } = render(
            React.createElement(WindowProbe, { total: 100, rowHeight: 20, overscan: 6, viewportH: 0 }),
        );
        expect(captured).toEqual({ start: 0, end: 12 });
        cleanup(host, root);
    });

    test("scrolling past the bottom clamps the end at the total", () => {
        const { host, root } = render(React.createElement(WindowProbe, { total: 30, rowHeight: 20 }));
        scrollInner(host, 50_000);
        expect(captured.end).toBe(30);
        expect(captured.start).toBeGreaterThanOrEqual(0);
        cleanup(host, root);
    });

    test("remount after scrolling starts back at zero", () => {
        const first = render(React.createElement(WindowProbe, { total: 100, rowHeight: 20 }));
        scrollInner(first.host, 800);
        expect(captured.start).toBeGreaterThan(0);
        cleanup(first.host, first.root);
        const second = render(React.createElement(WindowProbe, { total: 100, rowHeight: 20 }));
        expect(captured).toEqual({ start: 0, end: 35 });
        cleanup(second.host, second.root);
    });
});

describe("useMotion edges", () => {
    const PRESETS = ["card", "page", "modal", "bar", "pin", "podium", "ticker"] as const;

    test("every preset enters with its own name", () => {
        for (const preset of PRESETS) {
            motionMock.enter.mockClear();
            const { host, root } = render(React.createElement(EnterProbe, { preset, deps: [] }));
            expect(motionMock.enter).toHaveBeenCalledWith(host.firstChild, preset);
            cleanup(host, root);
        }
    });

    test("identical deps across rerenders run the entrance once", () => {
        const { host, root } = render(React.createElement(EnterProbe, { preset: "card", deps: ["x"] }));
        act(() => {
            root.render(React.createElement(EnterProbe, { preset: "card", deps: ["x"] }));
        });
        expect(motionMock.enter).toHaveBeenCalledTimes(1);
        cleanup(host, root);
    });

    test("zero offset passes straight through to staggerIn", () => {
        const { host, root } = render(React.createElement(StaggerProbe, { selector: ".item", deps: [], offset: 0 }));
        expect(motionMock.staggerIn).toHaveBeenCalledWith(host.firstChild, ".item", 0);
        cleanup(host, root);
    });

    test("deps change re-runs the stagger and kills the old tweens", () => {
        const { host, root } = render(React.createElement(StaggerProbe, { selector: ".a", deps: ["a"] }));
        act(() => {
            root.render(React.createElement(StaggerProbe, { selector: ".a", deps: ["b"] }));
        });
        expect(motionMock.staggerIn).toHaveBeenCalledTimes(2);
        expect(motionMock.killFor).toHaveBeenCalled();
        cleanup(host, root);
    });
});
