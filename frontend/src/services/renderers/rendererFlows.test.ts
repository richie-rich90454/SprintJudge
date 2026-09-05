import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";

const editorMock = vi.hoisted(() => ({
    create: vi.fn(),
    handles: [] as Array<Record<string, unknown>>,
}));

vi.mock("../CodeEditor", () => ({ createCodeEditor: editorMock.create }));

const { FakeTerminal, FakeFit } = vi.hoisted(() => {
    class FakeTerminal {
        static instances: FakeTerminal[] = [];
        opts: unknown;
        written: string[] = [];
        dataCb: ((d: string) => void) | null = null;
        open = vi.fn();
        dispose = vi.fn();
        constructor(opts: unknown) {
            this.opts = opts;
            FakeTerminal.instances.push(this);
        }
        loadAddon(_a: unknown) {}
        write(s: string) {
            this.written.push(s);
        }
        onData(cb: (d: string) => void) {
            this.dataCb = cb;
        }
    }
    class FakeFit {
        static instances: FakeFit[] = [];
        fit = vi.fn();
        dispose = vi.fn();
        constructor() {
            FakeFit.instances.push(this);
        }
    }
    return { FakeTerminal, FakeFit };
});

vi.mock("@xterm/xterm", () => ({ Terminal: FakeTerminal }));
vi.mock("@xterm/addon-fit", () => ({ FitAddon: FakeFit }));

vi.mock("tone", () => {
    class Voice {
        triggerAttackRelease = vi.fn();
        constructor(..._args: unknown[]) {}
        connect(..._args: unknown[]) {
            return this;
        }
    }
    class Gain {
        constructor(..._args: unknown[]) {}
        toDestination() {
            return this;
        }
        connect(..._args: unknown[]) {
            return this;
        }
    }
    class Filter {
        constructor(..._args: unknown[]) {}
        connect(dest: unknown) {
            return dest;
        }
    }
    class BitCrusher {
        constructor(..._args: unknown[]) {}
        connect(..._args: unknown[]) {
            return this;
        }
    }
    class Loop {
        cb: (t: unknown) => void;
        constructor(cb: (t: unknown) => void, ..._rest: unknown[]) {
            this.cb = cb;
        }
        start(..._args: unknown[]) {}
        stop(..._args: unknown[]) {}
        dispose(..._args: unknown[]) {}
    }
    const transport = { bpm: { value: 0 }, start: vi.fn() };
    const destination = { mute: false };
    return {
        Gain,
        PolySynth: Voice,
        MonoSynth: Voice,
        NoiseSynth: Voice,
        Synth: Voice,
        Filter,
        BitCrusher,
        Loop,
        getTransport: () => transport,
        getDestination: () => destination,
        start: vi.fn(async () => {}),
        now: () => 1,
        Frequency: (n: unknown) => ({ toNote: () => `N${String(n)}` }),
    };
});

vi.mock("framer-motion", () => ({
    animate: vi.fn(() => ({ stop: vi.fn() })),
}));

import { McqRenderer } from "./McqRenderer";
import { TrueFalseRenderer } from "./TrueFalseRenderer";
import { MultipleSelectRenderer } from "./MultipleSelectRenderer";
import { NumericRenderer } from "./NumericRenderer";
import { OutputPredRenderer } from "./OutputPredRenderer";
import { FillBlankRenderer } from "./FillBlankRenderer";
import { DragSortRenderer } from "./DragSortRenderer";
import { ClickBugRenderer } from "./ClickBugRenderer";
import { CodeCompletionRenderer } from "./CodeCompletionRenderer";
import { ComplexityRenderer } from "./ComplexityRenderer";
import { OjFullRenderer } from "./OjFullRenderer";
import { OjPatchRenderer } from "./OjPatchRenderer";
import type { BaseQuestionRenderer } from "./BaseQuestionRenderer";

const fetchMock = vi.fn();
const flush = () => new Promise<void>((r) => setTimeout(r, 0));

function host(): { div: HTMLElement; seen: unknown[] } {
    return { div: document.createElement("div"), seen: [] };
}

function onChange(seen: unknown[]): (v: unknown) => void {
    return (v) => seen.push(v);
}

function lastEditorOnChange(): (v: string) => void {
    const calls = editorMock.create.mock.calls as unknown[][];
    return (calls[calls.length - 1][1] as { onChange: (v: string) => void }).onChange;
}

function lastHandle(): Record<string, ReturnType<typeof vi.fn>> {
    const h = editorMock.handles[editorMock.handles.length - 1];
    return h as Record<string, ReturnType<typeof vi.fn>>;
}

function term(): InstanceType<typeof FakeTerminal> {
    return FakeTerminal.instances[FakeTerminal.instances.length - 1];
}

function termText(): string {
    return term().written.join("");
}

function typeIntoTerm(text: string): void {
    term().dataCb?.(text);
}

function runButton(div: HTMLElement): HTMLButtonElement {
    const btn = [...div.querySelectorAll("button")].find((b) => b.textContent?.includes("Run"));
    if (!btn) throw new Error("run button missing");
    return btn as HTMLButtonElement;
}

function fetchBody(): Record<string, unknown> {
    const calls = fetchMock.mock.calls as unknown[][];
    return JSON.parse((calls[calls.length - 1][1] as { body: string }).body) as Record<string, unknown>;
}

async function mountOjFull(config: unknown, questionId?: string, allowed?: string[] | null) {
    const { div, seen } = host();
    const renderer = new OjFullRenderer(div, config, onChange(seen), questionId, allowed);
    renderer.mount();
    await flush();
    return { div, seen, renderer };
}

function dropOn(row: Element, from: string): void {
    const ev = new Event("drop", { bubbles: true, cancelable: true });
    Object.defineProperty(ev, "dataTransfer", {
        value: { getData: () => from, setData: () => {} },
    });
    row.dispatchEvent(ev);
}

function dragRows(div: HTMLElement): NodeListOf<Element> {
    return div.querySelectorAll("div[draggable='true']");
}

beforeEach(() => {
    localStorage.clear();
    FakeTerminal.instances.length = 0;
    FakeFit.instances.length = 0;
    editorMock.handles.length = 0;
    fetchMock.mockReset();
    fetchMock.mockResolvedValue({ ok: true, json: async () => ({ ok: true, output: "", status: "OK" }) });
    vi.stubGlobal("fetch", fetchMock);
    window.requestAnimationFrame = ((cb: FrameRequestCallback) => {
        cb(0);
        return 42;
    }) as typeof window.requestAnimationFrame;
    window.cancelAnimationFrame = (() => {}) as typeof window.cancelAnimationFrame;
    editorMock.create.mockReset();
    editorMock.create.mockImplementation(() => {
        const h: Record<string, unknown> = {
            getValue: vi.fn(() => "current"),
            setLanguage: vi.fn(),
            destroy: vi.fn(),
        };
        editorMock.handles.push(h);
        return Promise.resolve(h);
    });
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe("rendererFlows mount-interact-respond-destroy-remount cycles", () => {
    test("MCQ full cycle carries the new selection after remount", () => {
        const first = host();
        const r1 = new McqRenderer(first.div, { options: ["a", "b", "c"] }, onChange(first.seen));
        r1.mount();
        (first.div.querySelectorAll("button")[2] as HTMLButtonElement).click();
        expect(r1.getResponse()).toEqual({ selectedIndex: 2 });
        r1.destroy();
        expect(first.div.innerHTML).toBe("");
        const second = host();
        const r2 = new McqRenderer(second.div, { options: ["a", "b", "c"] }, onChange(second.seen));
        r2.mount();
        expect(r2.getResponse()).toEqual({ selectedIndex: -1 });
        (second.div.querySelectorAll("button")[0] as HTMLButtonElement).click();
        expect(r2.getResponse()).toEqual({ selectedIndex: 0 });
        expect(second.seen).toEqual([{ selectedIndex: 0 }]);
        r2.destroy();
    });

    test("TrueFalse full cycle flips true to false across remount", () => {
        const a = host();
        const r1 = new TrueFalseRenderer(a.div, {}, onChange(a.seen));
        r1.mount();
        (a.div.querySelectorAll("button")[0] as HTMLButtonElement).click();
        expect(r1.getResponse()).toEqual({ value: true });
        r1.destroy();
        const b = host();
        const r2 = new TrueFalseRenderer(b.div, {}, onChange(b.seen));
        r2.mount();
        expect(r2.getResponse()).toEqual({ value: null });
        (b.div.querySelectorAll("button")[1] as HTMLButtonElement).click();
        expect(r2.getResponse()).toEqual({ value: false });
        r2.destroy();
        expect(b.div.innerHTML).toBe("");
    });

    test("MultipleSelect full cycle toggles on off on then remounts clean", () => {
        const a = host();
        const r1 = new MultipleSelectRenderer(a.div, { options: ["a", "b", "c"] }, onChange(a.seen));
        r1.mount();
        const btns = a.div.querySelectorAll("button");
        (btns[0] as HTMLButtonElement).click();
        (btns[0] as HTMLButtonElement).click();
        expect(r1.getResponse()).toEqual({ selectedIndices: [] });
        (btns[1] as HTMLButtonElement).click();
        (btns[0] as HTMLButtonElement).click();
        expect(r1.getResponse()).toEqual({ selectedIndices: [1, 0] });
        expect(a.seen[a.seen.length - 1]).toEqual({ selectedIndices: [0, 1] });
        r1.destroy();
        const b = host();
        const r2 = new MultipleSelectRenderer(b.div, { options: ["a", "b", "c"] }, onChange(b.seen));
        r2.mount();
        expect(r2.getResponse()).toEqual({ selectedIndices: [] });
        r2.destroy();
    });

    test("Numeric full cycle types, clears, retypes then remounts empty", () => {
        const a = host();
        const r1 = new NumericRenderer(a.div, { unit: "ms" }, onChange(a.seen));
        r1.mount();
        const input = a.div.querySelector("input") as HTMLInputElement;
        input.value = "42";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        expect(r1.getResponse()).toEqual({ value: 42 });
        input.value = "";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        expect(r1.getResponse()).toEqual({ value: null });
        input.value = "3.14";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        expect(r1.getResponse()).toEqual({ value: 3.14 });
        r1.destroy();
        const b = host();
        const r2 = new NumericRenderer(b.div, {}, onChange(b.seen));
        r2.mount();
        expect(r2.getResponse()).toEqual({ value: null });
        r2.destroy();
    });

    test("OutputPred full cycle previews code then selects across remount", () => {
        const a = host();
        const r1 = new OutputPredRenderer(a.div, { code: "print(1+1)", options: ["1", "2", "3"] }, onChange(a.seen));
        r1.mount();
        expect(a.div.querySelector("pre")?.textContent).toContain("print(1+1)");
        (a.div.querySelectorAll("button")[1] as HTMLButtonElement).click();
        expect(r1.getResponse()).toEqual({ selectedIndex: 1 });
        r1.destroy();
        const b = host();
        const r2 = new OutputPredRenderer(b.div, { code: "print(1+1)", options: ["1", "2", "3"] }, onChange(b.seen));
        r2.mount();
        expect(r2.getResponse()).toEqual({ selectedIndex: -1 });
        (b.div.querySelectorAll("button")[2] as HTMLButtonElement).click();
        (b.div.querySelectorAll("button")[0] as HTMLButtonElement).click();
        expect(r2.getResponse()).toEqual({ selectedIndex: 0 });
        r2.destroy();
    });

    test("FillBlank full cycle types, wipes, retypes then remounts blank", () => {
        const a = host();
        const r1 = new FillBlankRenderer(a.div, { snippet: "x = ___" }, onChange(a.seen));
        r1.mount();
        const input = a.div.querySelector("input") as HTMLInputElement;
        input.value = "hello";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        expect(r1.getResponse()).toEqual({ text: "hello" });
        input.value = "";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        input.value = "world";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        expect(a.seen).toEqual([{ text: "hello" }, { text: "" }, { text: "world" }]);
        r1.destroy();
        const b = host();
        const r2 = new FillBlankRenderer(b.div, { snippet: "x = ___" }, onChange(b.seen));
        r2.mount();
        expect(r2.getResponse()).toEqual({ text: "" });
        r2.destroy();
    });

    test("DragSort full cycle reorders twice then remounts to the initial order", () => {
        const lines = [
            { id: "l1", text: "first" },
            { id: "l2", text: "second" },
            { id: "l3", text: "third" },
        ];
        const a = host();
        const r1 = new DragSortRenderer(a.div, { lines }, onChange(a.seen));
        r1.mount();
        expect(r1.getResponse()).toEqual({ order: ["l1", "l2", "l3"] });
        dropOn(dragRows(a.div)[2], "0");
        expect(r1.getResponse()).toEqual({ order: ["l2", "l3", "l1"] });
        dropOn(dragRows(a.div)[0], "2");
        expect(r1.getResponse()).toEqual({ order: ["l1", "l2", "l3"] });
        expect(a.seen).toEqual([
            { order: ["l1", "l2", "l3"] },
            { order: ["l2", "l3", "l1"] },
            { order: ["l1", "l2", "l3"] },
        ]);
        r1.destroy();
        const b = host();
        const r2 = new DragSortRenderer(b.div, { lines }, onChange(b.seen));
        r2.mount();
        expect(r2.getResponse()).toEqual({ order: ["l1", "l2", "l3"] });
        r2.destroy();
    });

    test("ClickBug full cycle picks lines then remounts unpicked", () => {
        const cfg = { codeLines: ["a = 1", "b = 2", "c = a + b"] };
        const a = host();
        const r1 = new ClickBugRenderer(a.div, cfg, onChange(a.seen));
        r1.mount();
        const rows = a.div.firstElementChild?.children;
        if (!rows) throw new Error("rows missing");
        (rows[1] as HTMLElement).click();
        expect(r1.getResponse()).toEqual({ line: 1 });
        (rows[2] as HTMLElement).click();
        expect(r1.getResponse()).toEqual({ line: 2 });
        r1.destroy();
        const b = host();
        const r2 = new ClickBugRenderer(b.div, cfg, onChange(b.seen));
        r2.mount();
        expect(r2.getResponse()).toEqual({ line: -1 });
        r2.destroy();
    });

    test("CodeCompletion full cycle edits then remounts back at the skeleton", () => {
        const a = host();
        const r1 = new CodeCompletionRenderer(a.div, { skeleton: "def f():\n  pass" }, onChange(a.seen));
        r1.mount();
        expect(r1.getResponse()).toEqual({ code: "def f():\n  pass" });
        const ta = a.div.querySelector("textarea") as HTMLTextAreaElement;
        ta.value = "def f():\n  return 1";
        ta.dispatchEvent(new Event("input", { bubbles: true }));
        expect(r1.getResponse()).toEqual({ code: "def f():\n  return 1" });
        r1.destroy();
        const b = host();
        const r2 = new CodeCompletionRenderer(b.div, { skeleton: "def f():\n  pass" }, onChange(b.seen));
        r2.mount();
        expect(r2.getResponse()).toEqual({ code: "def f():\n  pass" });
        r2.destroy();
    });

    test("Complexity full cycle selects then remounts unselected", () => {
        const cfg = { options: ["O(1)", "O(n)", "O(n^2)"] };
        const a = host();
        const r1 = new ComplexityRenderer(a.div, cfg, onChange(a.seen));
        r1.mount();
        (a.div.querySelectorAll("button")[2] as HTMLButtonElement).click();
        expect(r1.getResponse()).toEqual({ selectedIndex: 2 });
        r1.destroy();
        const b = host();
        const r2 = new ComplexityRenderer(b.div, cfg, onChange(b.seen));
        r2.mount();
        expect(r2.getResponse()).toEqual({ selectedIndex: -1 });
        (b.div.querySelectorAll("button")[0] as HTMLButtonElement).click();
        expect(b.seen).toEqual([{ selectedIndex: 0 }]);
        r2.destroy();
    });

    test("OjFull full cycle mounts, drafts, responds, destroys and remounts from cache", async () => {
        const first = await mountOjFull({ starter: "print(1)" }, "flow-q1");
        expect(first.seen[0]).toEqual({ source: "print(1)", language: "python" });
        lastEditorOnChange()("print(2)");
        expect(first.seen[first.seen.length - 1]).toEqual({ source: "print(2)", language: "python" });
        expect(localStorage.getItem("sprintjudge_code_flow-q1")).toBe("print(2)");
        first.renderer.destroy();
        expect(first.div.innerHTML).toBe("");
        const second = await mountOjFull({ starter: "print(1)" }, "flow-q1");
        expect(second.seen[0]).toEqual({ source: "print(2)", language: "python" });
        second.renderer.destroy();
    });

    test("OjPatch full cycle shows the note then remounts the buggy function", async () => {
        const a = host();
        const seen: unknown[] = [];
        const r1 = new OjPatchRenderer(a.div, { buggyFunction: "def f(:\n pass" }, onChange(seen), "patch-q1");
        r1.mount();
        await flush();
        expect(a.div.textContent).toContain("highlighted lines");
        expect(seen[0]).toEqual({ source: "def f(:\n pass", language: "python" });
        lastEditorOnChange()("def f():\n  pass");
        expect(r1.getResponse()).toEqual({ source: "def f():\n  pass", language: "python" });
        r1.destroy();
        const b = host();
        const seen2: unknown[] = [];
        const r2 = new OjPatchRenderer(b.div, { buggyFunction: "def f(:\n pass" }, onChange(seen2), "patch-q1");
        r2.mount();
        await flush();
        expect(seen2[0]).toEqual({ source: "def f():\n  pass", language: "python" });
        r2.destroy();
    });
});

describe("rendererFlows timer-expiry submits", () => {
    test("untouched MCQ expiry submits selectedIndex -1", () => {
        const { div, seen } = host();
        const r = new McqRenderer(div, { options: ["a", "b"] }, onChange(seen));
        r.mount();
        const payload = r.getResponse();
        r.destroy();
        expect(payload).toEqual({ selectedIndex: -1 });
    });

    test("mutated MCQ expiry submits the last clicked index", () => {
        const { div } = host();
        const r = new McqRenderer(div, { options: ["a", "b", "c"] }, () => {});
        r.mount();
        (div.querySelectorAll("button")[0] as HTMLButtonElement).click();
        (div.querySelectorAll("button")[2] as HTMLButtonElement).click();
        const payload = r.getResponse();
        r.destroy();
        expect(payload).toEqual({ selectedIndex: 2 });
    });

    test("untouched TrueFalse expiry submits null value", () => {
        const { div } = host();
        const r = new TrueFalseRenderer(div, {}, () => {});
        r.mount();
        expect(r.getResponse()).toEqual({ value: null });
        r.destroy();
    });

    test("untouched MultipleSelect expiry submits an empty list", () => {
        const { div } = host();
        const r = new MultipleSelectRenderer(div, { options: ["a", "b"] }, () => {});
        r.mount();
        expect(r.getResponse()).toEqual({ selectedIndices: [] });
        r.destroy();
    });

    test("untouched Numeric expiry submits null after invalid input", () => {
        const { div } = host();
        const r = new NumericRenderer(div, {}, () => {});
        r.mount();
        const input = div.querySelector("input") as HTMLInputElement;
        input.value = "not-a-number";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        expect(r.getResponse()).toEqual({ value: null });
        r.destroy();
    });

    test("untouched DragSort expiry submits the initial order", () => {
        const { div } = host();
        const r = new DragSortRenderer(div, { lines: [{ id: "a", text: "A" }, { id: "b", text: "B" }] }, () => {});
        r.mount();
        expect(r.getResponse()).toEqual({ order: ["a", "b"] });
        r.destroy();
    });

    test("untouched CodeCompletion expiry submits the skeleton", () => {
        const { div } = host();
        const r = new CodeCompletionRenderer(div, { skeleton: "x = 1" }, () => {});
        r.mount();
        expect(r.getResponse()).toEqual({ code: "x = 1" });
        r.destroy();
    });

    test("untouched OjFull expiry submits starter code with the default language", async () => {
        const { renderer } = await mountOjFull({ starter: "x = 1", defaultLanguage: "java" }, "exp-q1", ["java", "python"]);
        expect(renderer.getResponse()).toEqual({ source: "x = 1", language: "java" });
        renderer.destroy();
    });

    test("mutated OjFull expiry submits edited source after typing", async () => {
        const { renderer } = await mountOjFull({ starter: "x = 1" }, "exp-q2");
        lastEditorOnChange()("x = 2\ny = 3");
        expect(renderer.getResponse()).toEqual({ source: "x = 2\ny = 3", language: "python" });
        renderer.destroy();
    });
});

describe("rendererFlows language switch mid-draft", () => {
    test("switching language preserves the edited source", async () => {
        const { div, seen, renderer } = await mountOjFull({ starter: "print(1)" }, "lang-q1");
        lastEditorOnChange()("print(99)");
        const select = div.querySelector("select") as HTMLSelectElement;
        select.value = "java";
        select.dispatchEvent(new Event("change", { bubbles: true }));
        expect(renderer.getResponse()).toEqual({ source: "print(99)", language: "java" });
        expect(seen[seen.length - 1]).toEqual({ source: "print(99)", language: "java" });
        expect(lastHandle().setLanguage).toHaveBeenCalledWith("java");
        renderer.destroy();
    });

    test("switching to node maps the editor language to javascript", async () => {
        const { div, renderer } = await mountOjFull({ starter: "s" }, "lang-q2");
        const select = div.querySelector("select") as HTMLSelectElement;
        select.value = "node";
        select.dispatchEvent(new Event("change", { bubbles: true }));
        expect(renderer.getResponse()).toEqual({ source: "s", language: "node" });
        expect(lastHandle().setLanguage).toHaveBeenCalledWith("javascript");
        renderer.destroy();
    });

    test("switching twice keeps the latest language with the original source", async () => {
        const { div, renderer } = await mountOjFull({ starter: "base" }, "lang-q3");
        const select = div.querySelector("select") as HTMLSelectElement;
        select.value = "cpp";
        select.dispatchEvent(new Event("change", { bubbles: true }));
        select.value = "c";
        select.dispatchEvent(new Event("change", { bubbles: true }));
        expect(renderer.getResponse()).toEqual({ source: "base", language: "c" });
        renderer.destroy();
    });

    test("unknown select value falls back to plaintext without throwing", async () => {
        const { div, renderer } = await mountOjFull({ starter: "base" }, "lang-q4");
        const select = div.querySelector("select") as HTMLSelectElement;
        select.value = "cobol-not-real";
        select.dispatchEvent(new Event("change", { bubbles: true }));
        expect(lastHandle().setLanguage).toHaveBeenCalledWith("plaintext");
        renderer.destroy();
    });

    test("single allowed language hides the switcher and locks the language", async () => {
        const { div, renderer } = await mountOjFull({ starter: "only" }, "lang-q5", ["python"]);
        expect(div.querySelector("select")).toBeNull();
        expect(renderer.getResponse()).toEqual({ source: "only", language: "python" });
        renderer.destroy();
    });

    test("disallowed defaultLanguage falls back to the first allowed language", async () => {
        const { renderer } = await mountOjFull({ starter: "s", defaultLanguage: "cobol" }, "lang-q6", ["java", "python"]);
        expect(renderer.getResponse()).toEqual({ source: "s", language: "java" });
        renderer.destroy();
    });
});

describe("rendererFlows draft cache across destroy and remount", () => {
    test("draft survives destroy and seeds the next mount", async () => {
        const first = await mountOjFull({ starter: "v1" }, "cache-q1");
        lastEditorOnChange()("v2-edited");
        first.renderer.destroy();
        const second = await mountOjFull({ starter: "v1" }, "cache-q1");
        const calls = editorMock.create.mock.calls as unknown[][];
        const lastOpts = calls[calls.length - 1][1] as { value: string };
        expect(lastOpts.value).toBe("v2-edited");
        expect(second.seen[0]).toEqual({ source: "v2-edited", language: "python" });
        second.renderer.destroy();
    });

    test("different question ids keep independent drafts", async () => {
        const a = await mountOjFull({ starter: "a0" }, "cache-qa");
        lastEditorOnChange()("a1");
        a.renderer.destroy();
        const b = await mountOjFull({ starter: "b0" }, "cache-qb");
        expect(b.seen[0]).toEqual({ source: "b0", language: "python" });
        b.renderer.destroy();
        const a2 = await mountOjFull({ starter: "a0" }, "cache-qa");
        expect(a2.seen[0]).toEqual({ source: "a1", language: "python" });
        a2.renderer.destroy();
    });

    test("destroy before the editor resolves disposes the late handle", async () => {
        const { div, seen } = host();
        const renderer = new OjFullRenderer(div, { starter: "late" }, onChange(seen), "cache-qc");
        renderer.mount();
        renderer.destroy();
        await flush();
        const h = lastHandle();
        expect(h.destroy).toHaveBeenCalled();
        expect(div.innerHTML).toBe("");
    });

    test("storage failure while drafting still emits the response", async () => {
        vi.stubGlobal("localStorage", {
            getItem: () => null,
            setItem: () => {
                throw new Error("denied");
            },
            removeItem: () => {},
            clear: () => {},
            key: () => null,
            length: 0,
        });
        const { seen, renderer } = await mountOjFull({ starter: "s" }, "cache-qd");
        expect(() => lastEditorOnChange()("typed")).not.toThrow();
        expect(seen[seen.length - 1]).toEqual({ source: "typed", language: "python" });
        renderer.destroy();
        vi.unstubAllGlobals();
    });

    test("storage failure while reading the cache falls back to the starter", async () => {
        vi.stubGlobal("localStorage", {
            getItem: () => {
                throw new Error("denied");
            },
            setItem: () => {},
            removeItem: () => {},
            clear: () => {},
            key: () => null,
            length: 0,
        });
        const { seen, renderer } = await mountOjFull({ starter: "fresh-starter" }, "cache-qe");
        expect(seen[0]).toEqual({ source: "fresh-starter", language: "python" });
        renderer.destroy();
        vi.unstubAllGlobals();
    });
});

describe("rendererFlows multi-select toggle sequences", () => {
    test("on off on across three buttons ends sorted", () => {
        const { div } = host();
        const r = new MultipleSelectRenderer(div, { options: ["a", "b", "c", "d"] }, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        (btns[3] as HTMLButtonElement).click();
        (btns[1] as HTMLButtonElement).click();
        (btns[3] as HTMLButtonElement).click();
        (btns[0] as HTMLButtonElement).click();
        (btns[2] as HTMLButtonElement).click();
        expect(r.getResponse()).toEqual({ selectedIndices: [1, 0, 2] });
        r.destroy();
    });

    test("toggling everything off emits an empty selection", () => {
        const { div, seen } = host();
        const r = new MultipleSelectRenderer(div, { options: ["a", "b"] }, onChange(seen));
        r.mount();
        const btns = div.querySelectorAll("button");
        (btns[0] as HTMLButtonElement).click();
        (btns[1] as HTMLButtonElement).click();
        (btns[0] as HTMLButtonElement).click();
        (btns[1] as HTMLButtonElement).click();
        expect(r.getResponse()).toEqual({ selectedIndices: [] });
        expect(seen[seen.length - 1]).toEqual({ selectedIndices: [] });
        r.destroy();
    });

    test("data-selected flags track the toggle state exactly", () => {
        const { div } = host();
        const r = new MultipleSelectRenderer(div, { options: ["a", "b"] }, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        (btns[0] as HTMLButtonElement).click();
        expect(btns[0].getAttribute("data-selected")).toBe("true");
        (btns[0] as HTMLButtonElement).click();
        expect(btns[0].getAttribute("data-selected")).toBeNull();
        (btns[0] as HTMLButtonElement).click();
        expect(btns[0].getAttribute("data-selected")).toBe("true");
        r.destroy();
    });

    test("reveal after a toggle chain marks correct and shakes the wrong pick", () => {
        const { div } = host();
        const r = new MultipleSelectRenderer(div, { options: ["a", "b", "c"], correctIndices: [0, 2] }, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        (btns[0] as HTMLButtonElement).click();
        (btns[1] as HTMLButtonElement).click();
        r.reveal();
        expect(btns[0].classList.contains("is-correct")).toBe(true);
        expect(btns[2].classList.contains("is-correct")).toBe(true);
        expect(btns[0].getAttribute("disabled")).toBe("true");
        r.destroy();
    });
});

describe("rendererFlows drag-sort reorder chains", () => {
    test("three chained drops rotate the whole list", () => {
        const lines = [
            { id: "a", text: "A" },
            { id: "b", text: "B" },
            { id: "c", text: "C" },
            { id: "d", text: "D" },
        ];
        const { div } = host();
        const r = new DragSortRenderer(div, { lines }, () => {});
        r.mount();
        dropOn(dragRows(div)[3], "0");
        expect(r.getResponse()).toEqual({ order: ["b", "c", "d", "a"] });
        dropOn(dragRows(div)[3], "0");
        expect(r.getResponse()).toEqual({ order: ["c", "d", "a", "b"] });
        dropOn(dragRows(div)[0], "3");
        expect(r.getResponse()).toEqual({ order: ["b", "c", "d", "a"] });
        r.destroy();
    });

    test("drop onto the same index is a no-op without emitting", () => {
        const { div, seen } = host();
        const r = new DragSortRenderer(div, { lines: [{ id: "a", text: "A" }] }, onChange(seen));
        r.mount();
        const n = seen.length;
        dropOn(dragRows(div)[0], "0");
        expect(r.getResponse()).toEqual({ order: ["a"] });
        expect(seen).toHaveLength(n);
        r.destroy();
    });

    test("drop with an unreadable payload is a safe no-op", () => {
        const { div, seen } = host();
        const r = new DragSortRenderer(div, { lines: [{ id: "a", text: "A" }, { id: "b", text: "B" }] }, onChange(seen));
        r.mount();
        const ev = new Event("drop", { bubbles: true, cancelable: true });
        Object.defineProperty(ev, "dataTransfer", { value: { getData: () => "nope" } });
        dragRows(div)[0].dispatchEvent(ev);
        expect(r.getResponse()).toEqual({ order: ["a", "b"] });
        r.destroy();
    });

    test("drop with no dataTransfer at all is a safe no-op", () => {
        const { div } = host();
        const r = new DragSortRenderer(div, { lines: [{ id: "a", text: "A" }] }, () => {});
        r.mount();
        expect(() => dragRows(div)[0].dispatchEvent(new Event("drop", { bubbles: true, cancelable: true }))).not.toThrow();
        expect(r.getResponse()).toEqual({ order: ["a"] });
        r.destroy();
    });

    test("lines without ids fall back to positional ids", () => {
        const { div } = host();
        const r = new DragSortRenderer(div, { lines: [{ text: "X" }, { text: "Y" }] }, () => {});
        r.mount();
        expect(r.getResponse()).toEqual({ order: ["0", "1"] });
        dropOn(dragRows(div)[1], "0");
        expect(r.getResponse()).toEqual({ order: ["1", "0"] });
        r.destroy();
    });

    test("row numbers rerender after a reorder", () => {
        const { div } = host();
        const r = new DragSortRenderer(div, { lines: [{ id: "a", text: "A" }, { id: "b", text: "B" }] }, () => {});
        r.mount();
        dropOn(dragRows(div)[1], "0");
        const firstRow = dragRows(div)[0].textContent ?? "";
        expect(firstRow).toContain("1.");
        expect(firstRow).toContain("B");
        r.destroy();
    });
});

describe("rendererFlows OJ run-button state machine", () => {
    test("idle to running to ok back to idle re-enables the button", async () => {
        fetchMock.mockResolvedValueOnce({ ok: true, json: async () => ({ ok: true, output: "42\n", status: "OK" }) });
        const { div, renderer } = await mountOjFull({ starter: "print(42)" }, "run-q1");
        const btn = runButton(div);
        expect(btn.getAttribute("disabled")).toBeNull();
        btn.click();
        await flush();
        await flush();
        expect(btn.getAttribute("disabled")).toBeNull();
        expect(termText()).toContain("42");
        expect(termText()).toContain("$ running");
        renderer.destroy();
    });

    test("running state disables the button until the fetch settles", async () => {
        let resolve!: (v: unknown) => void;
        fetchMock.mockReturnValueOnce(new Promise((r) => (resolve = r)));
        const { div, renderer } = await mountOjFull({ starter: "s" }, "run-q2");
        const btn = runButton(div);
        btn.click();
        await flush();
        expect(btn.getAttribute("disabled")).toBe("true");
        resolve({ ok: true, json: async () => ({ ok: true, output: "done", status: "OK" }) });
        await flush();
        await flush();
        expect(btn.getAttribute("disabled")).toBeNull();
        renderer.destroy();
    });

    test("ok then error then ok distinguishes outputs across runs", async () => {
        fetchMock.mockResolvedValueOnce({ ok: true, json: async () => ({ ok: true, output: "first", status: "OK" }) });
        const { div, renderer } = await mountOjFull({ starter: "s" }, "run-q3");
        const btn = runButton(div);
        btn.click();
        await flush();
        await flush();
        fetchMock.mockResolvedValueOnce({ ok: false, status: 500 });
        btn.click();
        await flush();
        await flush();
        expect(termText()).toContain("first");
        expect(termText()).toContain("server error");
        fetchMock.mockResolvedValueOnce({ ok: true, json: async () => ({ ok: true, output: "third", status: "OK" }) });
        btn.click();
        await flush();
        await flush();
        expect(termText()).toContain("third");
        renderer.destroy();
    });

    test("429 maps to rate limited and 404 maps to runner not found", async () => {
        fetchMock.mockResolvedValueOnce({ ok: false, status: 429 });
        const { div, renderer } = await mountOjFull({ starter: "s" }, "run-q4");
        const btn = runButton(div);
        btn.click();
        await flush();
        await flush();
        expect(termText()).toContain("rate limited");
        fetchMock.mockResolvedValueOnce({ ok: false, status: 404 });
        btn.click();
        await flush();
        await flush();
        expect(termText()).toContain("runner not found");
        expect(btn.getAttribute("disabled")).toBeNull();
        renderer.destroy();
    });

    test("network rejection maps to runner unavailable then recovers on retry", async () => {
        fetchMock.mockRejectedValueOnce(new Error("down"));
        const { div, renderer } = await mountOjFull({ starter: "s" }, "run-q5");
        const btn = runButton(div);
        btn.click();
        await flush();
        await flush();
        expect(termText()).toContain("runner unavailable");
        expect(btn.getAttribute("disabled")).toBeNull();
        fetchMock.mockResolvedValueOnce({ ok: true, json: async () => ({ ok: true, output: "back", status: "OK" }) });
        btn.click();
        await flush();
        await flush();
        expect(termText()).toContain("back");
        renderer.destroy();
    });

    test("non-zero exit writes the status banner but keeps the program output", async () => {
        fetchMock.mockResolvedValueOnce({ ok: true, json: async () => ({ ok: false, output: "partial", status: "RE" }) });
        const { div, renderer } = await mountOjFull({ starter: "s" }, "run-q6");
        runButton(div).click();
        await flush();
        await flush();
        expect(termText()).toContain("partial");
        expect(termText()).toContain("exited non-zero");
        renderer.destroy();
    });

    test("run posts language, source and stdin then clears stdin for the next run", async () => {
        fetchMock.mockResolvedValue({ ok: true, json: async () => ({ ok: true, output: "", status: "OK" }) });
        const { div, renderer } = await mountOjFull({ starter: "s" }, "run-q7");
        lastEditorOnChange()("input()");
        typeIntoTerm("hello");
        runButton(div).click();
        await flush();
        await flush();
        expect(fetchBody()).toMatchObject({ language: "python", sourceCode: "input()", stdin: "hello" });
        runButton(div).click();
        await flush();
        await flush();
        expect(fetchBody()).toMatchObject({ stdin: "" });
        renderer.destroy();
    });
});

describe("rendererFlows console stdin flows", () => {
    test("multi-line paste arrives intact in the run body", async () => {
        const { div, renderer } = await mountOjFull({ starter: "s" }, "stdin-q1");
        typeIntoTerm("line1\nline2\nline3");
        runButton(div).click();
        await flush();
        await flush();
        expect(fetchBody().stdin).toBe("line1\nline2\nline3");
        renderer.destroy();
    });

    test("backspaces erase buffered input but never below empty", async () => {
        const { div, renderer } = await mountOjFull({ starter: "s" }, "stdin-q2");
        typeIntoTerm("ab");
        const bs = String.fromCharCode(127);
        typeIntoTerm(bs);
        typeIntoTerm(bs);
        typeIntoTerm(bs);
        typeIntoTerm("c");
        runButton(div).click();
        await flush();
        await flush();
        expect(fetchBody().stdin).toBe("c");
        renderer.destroy();
    });

    test("arrow-key escapes affect the screen only, never the buffer", async () => {
        const { div, renderer } = await mountOjFull({ starter: "s" }, "stdin-q3");
        typeIntoTerm("x");
        const esc = String.fromCharCode(27);
        typeIntoTerm(esc + "[A");
        typeIntoTerm(esc + "[B");
        typeIntoTerm("y");
        runButton(div).click();
        await flush();
        await flush();
        expect(fetchBody().stdin).toBe("xy");
        renderer.destroy();
    });

    test("windows line endings normalize to unix in the buffer", async () => {
        const { div, renderer } = await mountOjFull({ starter: "s" }, "stdin-q4");
        typeIntoTerm("a\r\nb\rc");
        runButton(div).click();
        await flush();
        await flush();
        expect(fetchBody().stdin).toBe("a\nb\nc");
        renderer.destroy();
    });

    test("stdin resets after a failed run so the next run starts clean", async () => {
        fetchMock.mockRejectedValueOnce(new Error("down"));
        const { div, renderer } = await mountOjFull({ starter: "s" }, "stdin-q5");
        typeIntoTerm("stale");
        runButton(div).click();
        await flush();
        await flush();
        fetchMock.mockResolvedValueOnce({ ok: true, json: async () => ({ ok: true, output: "", status: "OK" }) });
        runButton(div).click();
        await flush();
        await flush();
        expect(fetchBody().stdin).toBe("");
        renderer.destroy();
    });

    test("destroy disposes the terminal, fit addon and editor together", async () => {
        const { renderer } = await mountOjFull({ starter: "s" }, "stdin-q6");
        const t = term();
        const h = lastHandle();
        renderer.destroy();
        expect(t.dispose).toHaveBeenCalled();
        expect(h.destroy).toHaveBeenCalled();
    });
});

describe("rendererFlows reveal and misc interaction chains", () => {
    test("MCQ reveal highlights the correct answer and locks the buttons", () => {
        const { div } = host();
        const r = new McqRenderer(div, { options: ["a", "b"], correctIndex: 1 }, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        (btns[0] as HTMLButtonElement).click();
        r.reveal();
        expect(btns[1].classList.contains("is-correct")).toBe(true);
        expect(btns[0].getAttribute("disabled")).toBe("true");
        expect(btns[1].getAttribute("disabled")).toBe("true");
        r.destroy();
    });

    test("MCQ reveal without a numeric correctIndex leaves buttons alone", () => {
        const { div } = host();
        const r = new McqRenderer(div, { options: ["a", "b"], correctIndex: "x" }, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        r.reveal();
        expect(btns[0].classList.contains("is-correct")).toBe(false);
        expect(btns[0].getAttribute("disabled")).toBeNull();
        r.destroy();
    });

    test("TrueFalse reveal pulses the right button after a wrong pick", () => {
        const { div } = host();
        const r = new TrueFalseRenderer(div, { correct: false }, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        (btns[0] as HTMLButtonElement).click();
        r.reveal();
        expect(btns[1].classList.contains("is-correct")).toBe(true);
        r.destroy();
    });

    test("TrueFalse reveal with a non-boolean correct flag is a no-op", () => {
        const { div } = host();
        const r = new TrueFalseRenderer(div, { correct: "yes" }, () => {});
        r.mount();
        expect(() => r.reveal()).not.toThrow();
        r.destroy();
    });

    test("MCQ reselect moves the highlight and emits twice", () => {
        const { div, seen } = host();
        const r = new McqRenderer(div, { options: ["a", "b", "c"] }, onChange(seen));
        r.mount();
        const btns = div.querySelectorAll("button");
        (btns[0] as HTMLButtonElement).click();
        (btns[1] as HTMLButtonElement).click();
        expect(seen).toEqual([{ selectedIndex: 0 }, { selectedIndex: 1 }]);
        expect(btns[0].getAttribute("data-selected")).toBeNull();
        expect(btns[1].getAttribute("data-selected")).toBe("true");
        r.destroy();
    });

    test("OutputPred reselect swaps the highlight classes", () => {
        const { div } = host();
        const r = new OutputPredRenderer(div, { code: "c", options: ["x", "y"] }, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        (btns[0] as HTMLButtonElement).click();
        (btns[1] as HTMLButtonElement).click();
        expect(btns[0].classList.contains("border-primary")).toBe(false);
        expect(btns[1].classList.contains("border-primary")).toBe(true);
        expect(r.getResponse()).toEqual({ selectedIndex: 1 });
        r.destroy();
    });

    test("ClickBug reselect moves the highlight to the latest row", () => {
        const { div } = host();
        const r = new ClickBugRenderer(div, { codeLines: ["l1", "l2"] }, () => {});
        r.mount();
        const rows = div.firstElementChild?.children;
        if (!rows) throw new Error("rows missing");
        (rows[0] as HTMLElement).click();
        (rows[1] as HTMLElement).click();
        expect(rows[0].classList.contains("border-primary")).toBe(false);
        expect(rows[1].classList.contains("border-primary")).toBe(true);
        expect(r.getResponse()).toEqual({ line: 1 });
        r.destroy();
    });

    test("Numeric unit label renders and negative decimals parse", () => {
        const { div } = host();
        const r = new NumericRenderer(div, { unit: "kg" }, () => {});
        r.mount();
        expect(div.textContent).toContain("Unit: kg");
        const input = div.querySelector("input") as HTMLInputElement;
        input.value = "-12.5";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        expect(r.getResponse()).toEqual({ value: -12.5 });
        r.destroy();
    });

    test("OjPatch mounts without a select when only one language is allowed", async () => {
        const { div } = host();
        const seen: unknown[] = [];
        const r = new OjPatchRenderer(div, { buggyFunction: "x" }, onChange(seen), "patch-1", ["java"]);
        r.mount();
        await flush();
        expect(div.querySelector("select")).toBeNull();
        expect(r.getResponse()).toEqual({ source: "x", language: "java" });
        r.destroy();
    });

    test("every renderer destroy clears its container", () => {
        const cases: Array<() => BaseQuestionRenderer> = [
            () => new McqRenderer(document.createElement("div"), { options: ["a"] }, () => {}),
            () => new TrueFalseRenderer(document.createElement("div"), {}, () => {}),
            () => new NumericRenderer(document.createElement("div"), {}, () => {}),
            () => new OutputPredRenderer(document.createElement("div"), { options: ["a"] }, () => {}),
            () => new FillBlankRenderer(document.createElement("div"), {}, () => {}),
            () => new ClickBugRenderer(document.createElement("div"), { codeLines: ["a"] }, () => {}),
            () => new CodeCompletionRenderer(document.createElement("div"), {}, () => {}),
            () => new ComplexityRenderer(document.createElement("div"), { options: ["a"] }, () => {}),
        ];
        for (const make of cases) {
            const r = make();
            const el = (r as unknown as { container: HTMLElement }).container;
            r.mount();
            expect(el.innerHTML.length).toBeGreaterThan(0);
            r.destroy();
            expect(el.innerHTML).toBe("");
        }
    });
});
