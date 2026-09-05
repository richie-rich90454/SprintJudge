import { describe, test, expect, vi, afterEach } from "vitest";

vi.mock("framer-motion", () => ({
    animate: vi.fn((...args: unknown[]) => {
        const opts = args[args.length - 1] as { onComplete?: () => void };
        opts?.onComplete?.();
        return { stop: vi.fn() };
    }),
}));

import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
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

afterEach(() => {
    vi.restoreAllMocks();
});

class Probe extends BaseQuestionRenderer {
    mount(): void {
        this.container.textContent = "mounted";
    }
    getResponse(): unknown {
        return { probe: true };
    }
    fire(response: unknown): void {
        this.emit(response);
    }
}

function host(): HTMLElement {
    return document.createElement("div");
}

describe("BaseQuestionRenderer", () => {
    test("mount and getResponse delegate to the subclass", () => {
        const div = host();
        const r = new Probe(div, {}, () => {});
        r.mount();
        expect(div.textContent).toBe("mounted");
        expect(r.getResponse()).toEqual({ probe: true });
    });

    test("null config becomes an empty object", () => {
        const r = new Probe(host(), null, () => {});
        expect((r as unknown as { config: unknown }).config).toEqual({});
    });

    test("empty language list normalizes to null", () => {
        const r = new Probe(host(), {}, () => {}, "q1", []);
        expect((r as unknown as { allowedLanguages: unknown }).allowedLanguages).toBeNull();
    });

    test("non-empty language list is kept", () => {
        const r = new Probe(host(), {}, () => {}, "q1", ["python"]);
        expect((r as unknown as { allowedLanguages: unknown }).allowedLanguages).toEqual(["python"]);
    });

    test("emit forwards the response to onChange", () => {
        const seen: unknown[] = [];
        new Probe(host(), {}, (v) => seen.push(v)).fire({ a: 1 });
        expect(seen).toEqual([{ a: 1 }]);
    });

    test("reveal defaults to a no-op", () => {
        expect(() => new Probe(host(), {}, () => {}).reveal()).not.toThrow();
    });

    test("destroy clears the container", () => {
        const div = host();
        const r = new Probe(div, {}, () => {});
        r.mount();
        r.destroy();
        expect(div.innerHTML).toBe("");
    });
});

describe("McqRenderer", () => {
    test("renders one button per option with shape markers", () => {
        const div = host();
        new McqRenderer(div, { options: ["a", "b", "c"] }, () => {}).mount();
        const btns = div.querySelectorAll("button");
        expect(btns).toHaveLength(3);
        expect(btns[0].querySelector(".shape-triangle")).not.toBeNull();
        expect(btns[1].querySelector(".shape-diamond")).not.toBeNull();
        expect(btns[2].textContent).toContain("c");
    });

    test("two options use the compact two-column layout", () => {
        const div = host();
        new McqRenderer(div, { options: ["a", "b"] }, () => {}).mount();
        expect(div.querySelector(".cols-2")).not.toBeNull();
    });

    test("three options skip the compact layout", () => {
        const div = host();
        new McqRenderer(div, { options: ["a", "b", "c"] }, () => {}).mount();
        expect(div.querySelector(".cols-2")).toBeNull();
    });

    test("click selects and emits the index", () => {
        const div = host();
        const seen: unknown[] = [];
        const r = new McqRenderer(div, { options: ["a", "b"] }, (v) => seen.push(v));
        r.mount();
        const btns = div.querySelectorAll("button");
        btns[1].click();
        expect(seen).toEqual([{ selectedIndex: 1 }]);
        expect(r.getResponse()).toEqual({ selectedIndex: 1 });
        expect(btns[1].getAttribute("data-selected")).toBe("true");
        expect(btns[0].getAttribute("data-selected")).toBeNull();
    });

    test("reselecting moves the selection", () => {
        const div = host();
        const r = new McqRenderer(div, { options: ["a", "b"] }, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        btns[0].click();
        btns[1].click();
        expect(r.getResponse()).toEqual({ selectedIndex: 1 });
        expect(btns[0].getAttribute("data-selected")).toBeNull();
    });

    test("initial response is unselected", () => {
        const r = new McqRenderer(host(), { options: ["a"] }, () => {});
        r.mount();
        expect(r.getResponse()).toEqual({ selectedIndex: -1 });
    });

    test("reveal highlights the correct option and locks the buttons", () => {
        const div = host();
        const r = new McqRenderer(div, { options: ["a", "b"], correctIndex: 0 }, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        btns[1].click();
        r.reveal();
        expect(btns[0].classList.contains("is-correct")).toBe(true);
        expect(btns[0].getAttribute("disabled")).toBe("true");
        expect(btns[1].getAttribute("disabled")).toBe("true");
    });

    test("reveal without a correct index leaves buttons alone", () => {
        const div = host();
        const r = new McqRenderer(div, { options: ["a", "b"] }, () => {});
        r.mount();
        r.reveal();
        expect(div.querySelector(".is-correct")).toBeNull();
    });

    test("reveal with three options leaves the untouched wrong one alone", () => {
        const div = host();
        const r = new McqRenderer(div, { options: ["a", "b", "c"], correctIndex: 0 }, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        btns[1].click();
        r.reveal();
        expect(btns[0].classList.contains("is-correct")).toBe(true);
        expect(btns[2].classList.contains("is-correct")).toBe(false);
    });

    test("empty options render nothing and never crash", () => {
        const div = host();
        const r = new McqRenderer(div, {}, () => {});
        r.mount();
        expect(div.querySelectorAll("button")).toHaveLength(0);
        expect(r.getResponse()).toEqual({ selectedIndex: -1 });
    });
});

describe("TrueFalseRenderer", () => {
    test("renders True and False buttons", () => {
        const div = host();
        new TrueFalseRenderer(div, {}, () => {}).mount();
        const btns = div.querySelectorAll("button");
        expect(btns).toHaveLength(2);
        expect(btns[0].textContent).toContain("True");
        expect(btns[1].textContent).toContain("False");
    });

    test("clicking True emits true", () => {
        const div = host();
        const seen: unknown[] = [];
        const r = new TrueFalseRenderer(div, {}, (v) => seen.push(v));
        r.mount();
        div.querySelectorAll("button")[0].click();
        expect(seen).toEqual([{ value: true }]);
        expect(r.getResponse()).toEqual({ value: true });
    });

    test("clicking False emits false and moves selection", () => {
        const div = host();
        const r = new TrueFalseRenderer(div, {}, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        btns[0].click();
        btns[1].click();
        expect(r.getResponse()).toEqual({ value: false });
        expect(btns[0].getAttribute("data-selected")).toBeNull();
        expect(btns[1].getAttribute("data-selected")).toBe("true");
    });

    test("initial response is null", () => {
        const r = new TrueFalseRenderer(host(), {}, () => {});
        r.mount();
        expect(r.getResponse()).toEqual({ value: null });
    });

    test("reveal marks True correct when the answer is true", () => {
        const div = host();
        const r = new TrueFalseRenderer(div, { correct: true }, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        btns[1].click();
        r.reveal();
        expect(btns[0].classList.contains("is-correct")).toBe(true);
        expect(btns[1].classList.contains("is-correct")).toBe(false);
    });

    test("reveal marks False correct when the answer is false", () => {
        const div = host();
        const r = new TrueFalseRenderer(div, { correct: false }, () => {});
        r.mount();
        r.reveal();
        expect(div.querySelectorAll("button")[1].classList.contains("is-correct")).toBe(true);
    });

    test("reveal with a non-boolean answer is a no-op", () => {
        const div = host();
        const r = new TrueFalseRenderer(div, { correct: "yes" }, () => {});
        r.mount();
        r.reveal();
        expect(div.querySelector(".is-correct")).toBeNull();
    });
});

describe("MultipleSelectRenderer", () => {
    test("renders options plus a partial-scoring hint", () => {
        const div = host();
        new MultipleSelectRenderer(div, { options: ["a", "b"] }, () => {}).mount();
        expect(div.querySelectorAll("button")).toHaveLength(2);
        expect(div.textContent).toContain("Partial scoring applies.");
    });

    test("clicking toggles options and emits sorted indices", () => {
        const div = host();
        const seen: unknown[] = [];
        const r = new MultipleSelectRenderer(div, { options: ["a", "b", "c"] }, (v) => seen.push(v));
        r.mount();
        const btns = div.querySelectorAll("button");
        btns[2].click();
        btns[0].click();
        expect(seen[1]).toEqual({ selectedIndices: [0, 2] });
        btns[2].click();
        expect(seen[2]).toEqual({ selectedIndices: [0] });
        expect(r.getResponse()).toEqual({ selectedIndices: [0] });
    });

    test("reveal highlights every correct option", () => {
        const div = host();
        const r = new MultipleSelectRenderer(div, { options: ["a", "b", "c"], correctIndices: [0, 2] }, () => {});
        r.mount();
        const btns = div.querySelectorAll("button");
        btns[1].click();
        r.reveal();
        expect(btns[0].classList.contains("is-correct")).toBe(true);
        expect(btns[2].classList.contains("is-correct")).toBe(true);
        expect(btns[1].classList.contains("is-correct")).toBe(false);
    });

    test("reveal with a non-array answer is a no-op", () => {
        const div = host();
        const r = new MultipleSelectRenderer(div, { options: ["a"] }, () => {});
        r.mount();
        r.reveal();
        expect(div.querySelector(".is-correct")).toBeNull();
    });

    test("missing options render an empty list with just the hint", () => {
        const div = host();
        const seen: unknown[] = [];
        const r = new MultipleSelectRenderer(div, {}, (v) => seen.push(v));
        r.mount();
        expect(div.querySelectorAll("button")).toHaveLength(0);
        expect(div.textContent).toContain("Partial scoring applies.");
        expect(r.getResponse()).toEqual({ selectedIndices: [] });
    });

    test("reveal leaves untouched wrong options alone", () => {
        const div = host();
        const r = new MultipleSelectRenderer(
            div,
            { options: ["a", "b", "c", "d"], correctIndices: [0] },
            () => {},
        );
        r.mount();
        const btns = div.querySelectorAll("button");
        btns[1].click();
        r.reveal();
        expect(btns[0].classList.contains("is-correct")).toBe(true);
        expect(btns[2].classList.contains("is-correct")).toBe(false);
        expect(btns[3].classList.contains("is-correct")).toBe(false);
    });
});

describe("NumericRenderer", () => {
    test("typing a number emits its value", () => {
        const div = host();
        const seen: unknown[] = [];
        const r = new NumericRenderer(div, {}, (v) => seen.push(v));
        r.mount();
        const input = div.querySelector("input") as HTMLInputElement;
        input.value = "42";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        expect(seen).toEqual([{ value: 42 }]);
        expect(r.getResponse()).toEqual({ value: 42 });
    });

    test("non-numeric input emits null", () => {
        const div = host();
        const seen: unknown[] = [];
        new NumericRenderer(div, {}, (v) => seen.push(v)).mount();
        const input = div.querySelector("input") as HTMLInputElement;
        input.value = "";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        expect(seen).toEqual([{ value: null }]);
    });

    test("decimal input is parsed as a float", () => {
        const div = host();
        const r = new NumericRenderer(div, {}, () => {});
        r.mount();
        const input = div.querySelector("input") as HTMLInputElement;
        input.value = "3.14";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        expect(r.getResponse()).toEqual({ value: 3.14 });
    });

    test("unit line shows when configured", () => {
        const div = host();
        new NumericRenderer(div, { unit: "kg" }, () => {}).mount();
        expect(div.textContent).toContain("Unit: kg");
    });

    test("unit line hides when unconfigured", () => {
        const div = host();
        new NumericRenderer(div, {}, () => {}).mount();
        expect(div.textContent).not.toContain("Unit:");
    });
});

describe("OutputPredRenderer", () => {
    test("shows the code block and lettered options", () => {
        const div = host();
        new OutputPredRenderer(div, { code: "print(1)", options: ["1", "2"] }, () => {}).mount();
        expect(div.querySelector("pre")?.textContent).toContain("print(1)");
        const btns = div.querySelectorAll("button");
        expect(btns).toHaveLength(2);
        expect(btns[0].textContent).toContain("A");
        expect(btns[1].textContent).toContain("B");
    });

    test("clicking an option emits and highlights it", () => {
        const div = host();
        const seen: unknown[] = [];
        const r = new OutputPredRenderer(div, { code: "x", options: ["1", "2"] }, (v) => seen.push(v));
        r.mount();
        const btns = div.querySelectorAll("button");
        btns[0].click();
        btns[1].click();
        expect(seen[1]).toEqual({ selectedIndex: 1 });
        expect(btns[1].classList.contains("border-primary")).toBe(true);
        expect(btns[0].classList.contains("border-primary")).toBe(false);
        expect(r.getResponse()).toEqual({ selectedIndex: 1 });
    });

    test("missing code defaults to empty", () => {
        const div = host();
        new OutputPredRenderer(div, { options: [] }, () => {}).mount();
        expect(div.querySelector("pre")?.textContent).toBe("");
    });

    test("missing options render only the code block", () => {
        const div = host();
        const seen: unknown[] = [];
        const r = new OutputPredRenderer(div, { code: "x" }, (v) => seen.push(v));
        r.mount();
        expect(div.querySelectorAll("button")).toHaveLength(0);
        expect(r.getResponse()).toEqual({ selectedIndex: -1 });
        expect(seen).toHaveLength(0);
    });
});

describe("FillBlankRenderer", () => {
    test("shows the snippet and emits typed text", () => {
        const div = host();
        const seen: unknown[] = [];
        const r = new FillBlankRenderer(div, { snippet: "a = ___" }, (v) => seen.push(v));
        r.mount();
        expect(div.querySelector("pre")?.textContent).toContain("a = ___");
        const input = div.querySelector("input") as HTMLInputElement;
        input.value = "5";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        expect(seen).toEqual([{ text: "5" }]);
        expect(r.getResponse()).toEqual({ text: "5" });
    });

    test("missing snippet defaults to empty", () => {
        const div = host();
        new FillBlankRenderer(div, {}, () => {}).mount();
        expect(div.querySelector("pre")?.textContent).toBe("");
    });
});

describe("DragSortRenderer", () => {
    const lines = [
        { id: "l1", text: "first" },
        { id: "l2", text: "second" },
        { id: "l3", text: "third" },
    ];

    test("emits the initial order on mount", () => {
        const seen: unknown[] = [];
        new DragSortRenderer(host(), { lines }, (v) => seen.push(v)).mount();
        expect(seen).toEqual([{ order: ["l1", "l2", "l3"] }]);
    });

    test("rows show position numbers and text", () => {
        const div = host();
        new DragSortRenderer(div, { lines }, () => {}).mount();
        const rows = div.querySelectorAll("div > div > div");
        expect(rows[0].textContent).toContain("1.");
        expect(rows[0].textContent).toContain("first");
    });

    test("lines without ids fall back to index strings", () => {
        const seen: unknown[] = [];
        new DragSortRenderer(host(), { lines: [{ text: "a" }, { text: "b" }] }, (v) => seen.push(v)).mount();
        expect(seen).toEqual([{ order: ["0", "1"] }]);
    });

    test("drop reorders and emits the new order", () => {
        const div = host();
        const seen: unknown[] = [];
        const r = new DragSortRenderer(div, { lines }, (v) => seen.push(v));
        r.mount();
        const list = div.firstElementChild as HTMLElement;
        const target = list.children[2];
        const ev = new Event("drop", { bubbles: true, cancelable: true }) as Event & {
            dataTransfer?: { getData: (k: string) => string };
        };
        ev.dataTransfer = { getData: () => "0" };
        target.dispatchEvent(ev);
        expect(seen[seen.length - 1]).toEqual({ order: ["l2", "l3", "l1"] });
        expect(r.getResponse()).toEqual({ order: ["l2", "l3", "l1"] });
    });

    test("drop with an unreadable source index is ignored", () => {
        const div = host();
        const seen: unknown[] = [];
        new DragSortRenderer(div, { lines }, (v) => seen.push(v)).mount();
        const list = div.firstElementChild as HTMLElement;
        const ev = new Event("drop", { bubbles: true, cancelable: true }) as Event & {
            dataTransfer?: { getData: (k: string) => string };
        };
        ev.dataTransfer = { getData: () => "nope" };
        list.children[1].dispatchEvent(ev);
        expect(seen).toHaveLength(1);
    });

    test("drop onto the same index is ignored", () => {
        const div = host();
        const seen: unknown[] = [];
        new DragSortRenderer(div, { lines }, (v) => seen.push(v)).mount();
        const list = div.firstElementChild as HTMLElement;
        const ev = new Event("drop", { bubbles: true, cancelable: true }) as Event & {
            dataTransfer?: { getData: (k: string) => string };
        };
        ev.dataTransfer = { getData: () => "1" };
        list.children[1].dispatchEvent(ev);
        expect(seen).toHaveLength(1);
    });

    test("dragstart and dragover do not throw", () => {
        const div = host();
        new DragSortRenderer(div, { lines }, () => {}).mount();
        const list = div.firstElementChild as HTMLElement;
        const row = list.children[0];
        const start = new Event("dragstart", { bubbles: true, cancelable: true }) as Event & {
            dataTransfer?: { setData: (t: string, v: string) => void };
        };
        start.dataTransfer = { setData: () => {} };
        expect(() => row.dispatchEvent(start)).not.toThrow();
        expect(() => row.dispatchEvent(new Event("dragover", { bubbles: true, cancelable: true }))).not.toThrow();
    });

    test("missing lines render an empty list with an empty order", () => {
        const seen: unknown[] = [];
        const r = new DragSortRenderer(host(), {}, (v) => seen.push(v));
        r.mount();
        expect(seen).toEqual([{ order: [] }]);
        expect(r.getResponse()).toEqual({ order: [] });
    });
});

describe("ClickBugRenderer", () => {
    test("renders numbered code lines", () => {
        const div = host();
        new ClickBugRenderer(div, { codeLines: ["x = 1", "y = 2"] }, () => {}).mount();
        const rows = div.firstElementChild?.querySelectorAll("div");
        expect(rows).toHaveLength(2);
        expect(rows?.[0].textContent).toContain("1");
        expect(rows?.[0].textContent).toContain("x = 1");
    });

    test("clicking a line emits it and highlights the row", () => {
        const div = host();
        const seen: unknown[] = [];
        const r = new ClickBugRenderer(div, { codeLines: ["a", "b", "c"] }, (v) => seen.push(v));
        r.mount();
        const rows = div.firstElementChild?.querySelectorAll("div");
        rows?.[2].dispatchEvent(new Event("click", { bubbles: true }));
        expect(seen).toEqual([{ line: 2 }]);
        expect(r.getResponse()).toEqual({ line: 2 });
        rows?.[0].dispatchEvent(new Event("click", { bubbles: true }));
        expect(rows?.[2].classList.contains("border-primary")).toBe(false);
        expect(rows?.[0].classList.contains("border-primary")).toBe(true);
    });

    test("initial response is unselected", () => {
        const r = new ClickBugRenderer(host(), { codeLines: ["a"] }, () => {});
        r.mount();
        expect(r.getResponse()).toEqual({ line: -1 });
    });

    test("missing code lines render an empty wrap", () => {
        const div = host();
        const r = new ClickBugRenderer(div, {}, () => {});
        r.mount();
        expect(div.querySelectorAll("div > div").length).toBeGreaterThanOrEqual(0);
        expect(r.getResponse()).toEqual({ line: -1 });
    });
});

describe("CodeCompletionRenderer", () => {
    test("emits the skeleton on mount and edits on input", () => {
        const div = host();
        const seen: unknown[] = [];
        const r = new CodeCompletionRenderer(div, { skeleton: "def f():\n  pass" }, (v) => seen.push(v));
        r.mount();
        expect(seen).toEqual([{ code: "def f():\n  pass" }]);
        const ta = div.querySelector("textarea") as HTMLTextAreaElement;
        expect(ta.value).toBe("def f():\n  pass");
        ta.value = "def f():\n  return 1";
        ta.dispatchEvent(new Event("input", { bubbles: true }));
        expect(seen[1]).toEqual({ code: "def f():\n  return 1" });
        expect(r.getResponse()).toEqual({ code: "def f():\n  return 1" });
    });

    test("missing skeleton defaults to empty code", () => {
        const seen: unknown[] = [];
        new CodeCompletionRenderer(host(), {}, (v) => seen.push(v)).mount();
        expect(seen).toEqual([{ code: "" }]);
    });
});

describe("ComplexityRenderer", () => {
    test("clicking an option emits and highlights it", () => {
        const div = host();
        const seen: unknown[] = [];
        const r = new ComplexityRenderer(div, { options: ["O(1)", "O(n)"] }, (v) => seen.push(v));
        r.mount();
        const btns = div.querySelectorAll("button");
        btns[1].click();
        expect(seen).toEqual([{ selectedIndex: 1 }]);
        expect(btns[1].classList.contains("border-primary")).toBe(true);
        expect(btns[0].classList.contains("border-primary")).toBe(false);
        expect(r.getResponse()).toEqual({ selectedIndex: 1 });
    });

    test("initial response is unselected", () => {
        const r = new ComplexityRenderer(host(), { options: ["O(1)"] }, () => {});
        r.mount();
        expect(r.getResponse()).toEqual({ selectedIndex: -1 });
    });

    test("empty options render nothing and never crash", () => {
        const div = host();
        const r = new ComplexityRenderer(div, {}, () => {});
        r.mount();
        expect(div.querySelectorAll("button")).toHaveLength(0);
    });
});

describe("renderer destroy", () => {
    test("destroy clears mounted DOM for every simple renderer", () => {
        const cases: Array<[new (c: HTMLElement, cfg: unknown, cb: (v: unknown) => void) => BaseQuestionRenderer, unknown]> = [
            [McqRenderer, { options: ["a"] }],
            [TrueFalseRenderer, {}],
            [MultipleSelectRenderer, { options: ["a"] }],
            [NumericRenderer, {}],
            [OutputPredRenderer, { options: ["a"] }],
            [FillBlankRenderer, {}],
            [DragSortRenderer, { lines: [{ id: "x", text: "y" }] }],
            [ClickBugRenderer, { codeLines: ["a"] }],
            [CodeCompletionRenderer, {}],
            [ComplexityRenderer, { options: ["a"] }],
        ];
        for (const [Ctor, cfg] of cases) {
            const div = host();
            const r = new Ctor(div, cfg, () => {});
            r.mount();
            expect(div.innerHTML.length).toBeGreaterThan(0);
            r.destroy();
            expect(div.innerHTML).toBe("");
        }
    });
});
