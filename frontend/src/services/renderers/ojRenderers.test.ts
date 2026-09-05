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
        addon: unknown = null;
        open = vi.fn();
        dispose = vi.fn();
        constructor(opts: unknown) {
            this.opts = opts;
            FakeTerminal.instances.push(this);
        }
        loadAddon(a: unknown) {
            this.addon = a;
        }
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

import { OjFullRenderer } from "./OjFullRenderer";
import { OjPatchRenderer } from "./OjPatchRenderer";
import { audio } from "../AudioEngine";

const fetchMock = vi.fn();

const flush = () => new Promise<void>((r) => setTimeout(r, 0));

let rafCb: FrameRequestCallback | null = null;
let cancelSpy: ReturnType<typeof vi.fn>;

interface Mounted {
    div: HTMLElement;
    seen: unknown[];
    renderer: OjFullRenderer | OjPatchRenderer;
}

function lastEditorOpts(): Record<string, unknown> {
    const calls = editorMock.create.mock.calls as unknown[][];
    return calls[calls.length - 1][1] as Record<string, unknown>;
}

function lastHandle(): Record<string, { mock: { calls: unknown[][] } }> & Record<string, unknown> {
    const h = editorMock.handles[editorMock.handles.length - 1];
    return h as Record<string, { mock: { calls: unknown[][] } }> & Record<string, unknown>;
}

function typeIntoTerminal(text: string) {
    FakeTerminal.instances[FakeTerminal.instances.length - 1].dataCb?.(text);
}

function runButton(div: HTMLElement): HTMLButtonElement {
    const btn = [...div.querySelectorAll("button")].find((b) => b.textContent?.includes("Run"));
    if (!btn) throw new Error("run button missing");
    return btn as HTMLButtonElement;
}

function termOutput(): string {
    return FakeTerminal.instances[FakeTerminal.instances.length - 1].written.join("");
}

async function mountFull(
    config: unknown,
    questionId?: string,
    allowed?: string[] | null,
): Promise<Mounted> {
    const div = document.createElement("div");
    const seen: unknown[] = [];
    const renderer = new OjFullRenderer(div, config, (v) => seen.push(v), questionId, allowed);
    renderer.mount();
    await flush();
    return { div, seen, renderer };
}

function okRun(output: string, ok = true, status = "OK") {
    fetchMock.mockResolvedValueOnce({ ok: true, json: async () => ({ ok, output, status }) });
}

beforeEach(() => {
    localStorage.clear();
    FakeTerminal.instances.length = 0;
    FakeFit.instances.length = 0;
    editorMock.handles.length = 0;
    fetchMock.mockReset();
    fetchMock.mockResolvedValue({ ok: true, json: async () => ({ ok: true, output: "", status: "OK" }) });
    vi.stubGlobal("fetch", fetchMock);
    rafCb = null;
    cancelSpy = vi.fn();
    window.requestAnimationFrame = ((cb: FrameRequestCallback) => {
        rafCb = cb;
        return 42;
    }) as typeof window.requestAnimationFrame;
    window.cancelAnimationFrame = cancelSpy as unknown as typeof window.cancelAnimationFrame;
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

describe("OjFullRenderer mounting", () => {
    test("shows all five languages with python default", async () => {
        const { div, seen } = await mountFull({ starter: "print(1)" });
        const select = div.querySelector("select") as HTMLSelectElement;
        expect(select.options).toHaveLength(5);
        expect(select.value).toBe("python");
        expect(seen[0]).toEqual({ source: "print(1)", language: "python" });
    });

    test("single allowed language hides the selector", async () => {
        const { div, seen } = await mountFull({ starter: "x" }, "q1", ["python"]);
        expect(div.querySelector("select")).toBeNull();
        expect(seen[0]).toEqual({ source: "x", language: "python" });
    });

    test("unknown language ids are filtered out", async () => {
        const { div } = await mountFull({ starter: "x" }, "q1", ["python", "cobol"]);
        expect(div.querySelector("select")).toBeNull();
    });

    test("requested default wins when permitted", async () => {
        const { div, seen } = await mountFull({ starter: "x", defaultLanguage: "java" });
        expect((div.querySelector("select") as HTMLSelectElement).value).toBe("java");
        expect(seen[0]).toEqual({ source: "x", language: "java" });
    });

    test("unpermitted default falls back to the first allowed language", async () => {
        const { div, seen } = await mountFull({ starter: "x", defaultLanguage: "python" }, "q1", ["java", "node"]);
        expect((div.querySelector("select") as HTMLSelectElement).value).toBe("java");
        expect(seen[0]).toEqual({ source: "x", language: "java" });
    });

    test("missing starter and default fall back to empty python", async () => {
        const { seen } = await mountFull({});
        expect(seen[0]).toEqual({ source: "", language: "python" });
    });

    test("cached draft overrides the starter code", async () => {
        localStorage.setItem("sprintjudge_code_q9", "cached-code");
        const { seen } = await mountFull({ starter: "fresh" }, "q9");
        expect(seen[0]).toEqual({ source: "cached-code", language: "python" });
    });

    test("editor mounts with the monaco-mapped language and a min height", async () => {
        const { div } = await mountFull({ starter: "x", defaultLanguage: "node" });
        expect(editorMock.create).toHaveBeenCalledTimes(1);
        const opts = lastEditorOpts();
        expect(opts["language"]).toBe("javascript");
        expect(opts["value"]).toBe("x");
        expect((div.firstElementChild as HTMLElement).tagName).toBe("SELECT");
    });

    test("editor typing updates source, persists the draft and emits", async () => {
        const { seen, renderer } = await mountFull({ starter: "a" }, "qd");
        (lastEditorOpts()["onChange"] as (v: string) => void)("typed!");
        expect(localStorage.getItem("sprintjudge_code_qd")).toBe("typed!");
        expect(seen[seen.length - 1]).toEqual({ source: "typed!", language: "python" });
        expect(renderer.getResponse()).toEqual({ source: "typed!", language: "python" });
    });

    test("typing without a question id skips persistence but still emits", async () => {
        const { seen } = await mountFull({ starter: "a" });
        (lastEditorOpts()["onChange"] as (v: string) => void)("v2");
        expect(seen[seen.length - 1]).toEqual({ source: "v2", language: "python" });
    });

    test("language switch remaps monaco language and emits", async () => {
        const { div, seen } = await mountFull({ starter: "x" });
        const select = div.querySelector("select") as HTMLSelectElement;
        select.value = "node";
        select.dispatchEvent(new Event("change", { bubbles: true }));
        expect(lastHandle()["setLanguage"]?.mock.calls).toEqual([["javascript"]]);
        expect(seen[seen.length - 1]).toEqual({ source: "x", language: "node" });
    });

    test("language switch before the editor resolves skips setLanguage", async () => {
        editorMock.create.mockReset();
        let resolveIt!: (h: unknown) => void;
        editorMock.create.mockReturnValueOnce(
            new Promise((r) => {
                resolveIt = r;
            }),
        );
        const div = document.createElement("div");
        const seen: unknown[] = [];
        const r = new OjFullRenderer(div, { starter: "x" }, (v) => seen.push(v));
        r.mount();
        const select = div.querySelector("select") as HTMLSelectElement;
        select.value = "java";
        select.dispatchEvent(new Event("change", { bubbles: true }));
        expect(seen).toHaveLength(2);
        resolveIt({ getValue: () => "", setLanguage: vi.fn(), destroy: vi.fn() });
        await flush();
        r.destroy();
    });

    test("unknown select value falls back to plaintext highlighting", async () => {
        const { div, seen } = await mountFull({ starter: "x" });
        const select = div.querySelector("select") as HTMLSelectElement;
        select.value = "cobol";
        select.dispatchEvent(new Event("change", { bubbles: true }));
        expect(lastHandle()["setLanguage"]?.mock.calls).toEqual([["plaintext"]]);
        expect(seen[seen.length - 1]).toEqual({ source: "x", language: "" });
    });

    test("broken draft cache still mounts with the starter", async () => {
        vi.spyOn(window.localStorage, "getItem").mockImplementation(() => {
            throw new Error("denied");
        });
        const { seen } = await mountFull({ starter: "starter!" }, "qb");
        expect(seen[0]).toEqual({ source: "starter!", language: "python" });
    });

    test("broken draft persistence still emits edits", async () => {
        vi.spyOn(window.localStorage, "setItem").mockImplementation(() => {
            throw new Error("denied");
        });
        const { seen } = await mountFull({ starter: "a" }, "qc");
        expect(() => (lastEditorOpts()["onChange"] as (v: string) => void)("v")).not.toThrow();
        expect(seen[seen.length - 1]).toEqual({ source: "v", language: "python" });
    });
});

describe("OjPatchRenderer", () => {
    test("shows the fix-the-bug note and mounts the buggy function", async () => {
        const div = document.createElement("div");
        const seen: unknown[] = [];
        const r = new OjPatchRenderer(div, { buggyFunction: "def f():\n  retrun 1" }, (v) => seen.push(v), "qp");
        r.mount();
        await flush();
        expect(div.textContent).toContain("Edit only the highlighted lines");
        expect(seen[0]).toEqual({ source: "def f():\n  retrun 1", language: "python" });
        expect(r.getResponse()).toEqual({ source: "def f():\n  retrun 1", language: "python" });
        r.destroy();
    });

    test("missing buggy function defaults to empty", async () => {
        const div = document.createElement("div");
        const seen: unknown[] = [];
        const r = new OjPatchRenderer(div, {}, (v) => seen.push(v));
        r.mount();
        await flush();
        expect(seen[0]).toEqual({ source: "", language: "python" });
        r.destroy();
    });
});

describe("OjBase console", () => {
    test("terminal mounts with a dark theme and fit addon", async () => {
        await mountFull({ starter: "x" });
        expect(FakeTerminal.instances).toHaveLength(1);
        expect(FakeFit.instances).toHaveLength(1);
        const term = FakeTerminal.instances[0];
        expect((term.opts as { theme: { background: string } }).theme.background).toBe("#000000");
        expect(term.addon).toBe(FakeFit.instances[0]);
        expect(term.open).toHaveBeenCalled();
    });

    test("fit runs after layout settles via rAF", async () => {
        await mountFull({ starter: "x" });
        expect(rafCb).not.toBeNull();
        expect(FakeFit.instances[0].fit).not.toHaveBeenCalled();
        rafCb?.(0);
        expect(FakeFit.instances[0].fit).toHaveBeenCalled();
    });

    test("resize refits the terminal", async () => {
        await mountFull({ starter: "x" });
        FakeFit.instances[0].fit.mockClear();
        window.dispatchEvent(new Event("resize"));
        expect(FakeFit.instances[0].fit).toHaveBeenCalled();
    });

    test("typed stdin is echoed and sent on run", async () => {
        const { div } = await mountFull({ starter: "print(1)" });
        typeIntoTerminal("hello");
        okRun("out\n");
        runButton(div).click();
        await flush();
        const body = JSON.parse((fetchMock.mock.calls[0][1] as { body: string }).body) as Record<string, unknown>;
        expect(body["stdin"]).toBe("hello");
        expect(body["language"]).toBe("python");
        expect(body["sourceCode"]).toBe("print(1)");
        expect(body["timeoutSec"]).toBe(10);
        expect(termOutput()).toContain("hello");
    });

    test("backspace erases buffered stdin", async () => {
        const { div } = await mountFull({ starter: "x" });
        typeIntoTerminal("ab");
        typeIntoTerminal("\x7f");
        okRun("");
        runButton(div).click();
        await flush();
        const body = JSON.parse((fetchMock.mock.calls[0][1] as { body: string }).body) as Record<string, unknown>;
        expect(body["stdin"]).toBe("a");
        expect(termOutput()).toContain("\b \b");
    });

    test("backspace on empty stdin does nothing", async () => {
        await mountFull({ starter: "x" });
        typeIntoTerminal("\x7f");
        expect(termOutput()).not.toContain("\b \b");
    });

    test("escape sequences are screen-only and never enter stdin", async () => {
        const { div } = await mountFull({ starter: "x" });
        typeIntoTerminal("\x1b[A");
        okRun("");
        runButton(div).click();
        await flush();
        const body = JSON.parse((fetchMock.mock.calls[0][1] as { body: string }).body) as Record<string, unknown>;
        expect(body["stdin"]).toBe("");
    });

    test("carriage returns normalize to newlines", async () => {
        const { div } = await mountFull({ starter: "x" });
        typeIntoTerminal("a\rb");
        okRun("");
        runButton(div).click();
        await flush();
        const body = JSON.parse((fetchMock.mock.calls[0][1] as { body: string }).body) as Record<string, unknown>;
        expect(body["stdin"]).toBe("a\nb");
    });

    test("successful run prints output and re-enables the button", async () => {
        const { div } = await mountFull({ starter: "x" });
        okRun("hello\nworld\n");
        const btn = runButton(div);
        btn.click();
        await flush();
        await flush();
        expect(termOutput()).toContain("hello\r\nworld");
        expect(btn.getAttribute("disabled")).toBeNull();
    });

    test("button stays disabled until the run finishes", async () => {
        const { div } = await mountFull({ starter: "x" });
        let resolveIt!: (v: unknown) => void;
        fetchMock.mockReset();
        fetchMock.mockReturnValueOnce(
            new Promise((r) => {
                resolveIt = r;
            }),
        );
        const btn = runButton(div);
        btn.click();
        expect(btn.getAttribute("disabled")).toBe("true");
        resolveIt({ ok: true, json: async () => ({ ok: true, output: "", status: "OK" }) });
        await flush();
        await flush();
        expect(btn.getAttribute("disabled")).toBeNull();
    });

    test("non-zero exit prints output plus the status banner", async () => {
        const { div } = await mountFull({ starter: "x" });
        okRun("partial", false, "RUNTIME_ERROR");
        runButton(div).click();
        await flush();
        await flush();
        expect(termOutput()).toContain("partial");
        expect(termOutput()).toContain("[RUNTIME_ERROR] program exited non-zero");
    });

    test("rate limiting prints a dedicated message", async () => {
        const { div } = await mountFull({ starter: "x" });
        fetchMock.mockResolvedValueOnce({ ok: false, status: 429 });
        runButton(div).click();
        await flush();
        await flush();
        expect(termOutput()).toContain("rate limited");
    });

    test("missing runner prints a dedicated message", async () => {
        const { div } = await mountFull({ starter: "x" });
        fetchMock.mockResolvedValueOnce({ ok: false, status: 404 });
        runButton(div).click();
        await flush();
        await flush();
        expect(termOutput()).toContain("runner not found");
    });

    test("other server errors print a generic message", async () => {
        const { div } = await mountFull({ starter: "x" });
        fetchMock.mockResolvedValueOnce({ ok: false, status: 500 });
        runButton(div).click();
        await flush();
        await flush();
        expect(termOutput()).toContain("server error");
    });

    test("network failure prints the unavailable hint", async () => {
        const { div } = await mountFull({ starter: "x" });
        fetchMock.mockRejectedValueOnce(new Error("down"));
        runButton(div).click();
        await flush();
        await flush();
        expect(termOutput()).toContain("runner unavailable");
    });

    test("run resumes audio with a click", async () => {
        const resume = vi.spyOn(audio, "resume");
        const play = vi.spyOn(audio, "play");
        const { div } = await mountFull({ starter: "x" });
        okRun("");
        runButton(div).click();
        await flush();
        await flush();
        expect(resume).toHaveBeenCalled();
        expect(play).toHaveBeenCalledWith("click");
    });

    test("stdin resets after each run", async () => {
        const { div } = await mountFull({ starter: "x" });
        typeIntoTerminal("once");
        okRun("");
        runButton(div).click();
        await flush();
        await flush();
        okRun("");
        runButton(div).click();
        await flush();
        await flush();
        const second = JSON.parse((fetchMock.mock.calls[1][1] as { body: string }).body) as Record<string, unknown>;
        expect(second["stdin"]).toBe("");
    });
});

describe("OjBase destroy", () => {
    test("destroy disposes editor, terminal and fit and clears the DOM", async () => {
        const { div, renderer } = await mountFull({ starter: "x" }, "qd");
        const handle = lastHandle();
        renderer.destroy();
        expect((handle["destroy"] as ReturnType<typeof vi.fn>)).toHaveBeenCalled();
        expect(FakeTerminal.instances[0].dispose).toHaveBeenCalled();
        expect(FakeFit.instances[0].dispose).toHaveBeenCalled();
        expect(div.innerHTML).toBe("");
    });

    test("destroy cancels the pending fit frame", async () => {
        const { renderer } = await mountFull({ starter: "x" });
        renderer.destroy();
        expect(cancelSpy).toHaveBeenCalledWith(42);
    });

    test("destroy detaches the resize listener", async () => {
        const { renderer } = await mountFull({ starter: "x" });
        renderer.destroy();
        FakeFit.instances[0].fit.mockClear();
        window.dispatchEvent(new Event("resize"));
        expect(FakeFit.instances[0].fit).not.toHaveBeenCalled();
    });

    test("destroy after the fit frame needs no cancel", async () => {
        const { renderer } = await mountFull({ starter: "x" });
        rafCb?.(0);
        expect(FakeFit.instances[0].fit).toHaveBeenCalled();
        renderer.destroy();
        expect(cancelSpy).not.toHaveBeenCalled();
        expect(() => renderer.destroy()).not.toThrow();
    });

    test("destroy before the editor resolves disposes the late handle", async () => {
        editorMock.create.mockReset();
        let resolveIt!: (h: unknown) => void;
        editorMock.create.mockReturnValueOnce(
            new Promise((r) => {
                resolveIt = r;
            }),
        );
        const div = document.createElement("div");
        const r = new OjFullRenderer(div, { starter: "x" }, () => {});
        r.mount();
        r.destroy();
        const destroy = vi.fn();
        resolveIt({ getValue: () => "", setLanguage: vi.fn(), destroy });
        await flush();
        expect(destroy).toHaveBeenCalled();
    });
});
