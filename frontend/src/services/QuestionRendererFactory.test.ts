import { describe, test, expect, vi, afterEach } from "vitest";

vi.mock("./CodeEditor", () => ({ createCodeEditor: vi.fn(async () => ({})) }));
vi.mock("@xterm/xterm", () => ({
    Terminal: class {
        constructor(..._args: unknown[]) {}
        loadAddon(..._args: unknown[]) {}
        open(..._args: unknown[]) {}
        write(..._args: unknown[]) {}
        onData(..._args: unknown[]) {}
        dispose(..._args: unknown[]) {}
    },
}));
vi.mock("@xterm/addon-fit", () => ({
    FitAddon: class {
        fit(..._args: unknown[]) {}
        dispose(..._args: unknown[]) {}
    },
}));
vi.mock("tone", () => ({
    Gain: class {
        constructor(..._args: unknown[]) {}
        toDestination() {
            return this;
        }
        connect(..._args: unknown[]) {
            return this;
        }
    },
    PolySynth: class {
        constructor(..._args: unknown[]) {}
        connect(..._args: unknown[]) {
            return this;
        }
    },
    MonoSynth: class {
        constructor(..._args: unknown[]) {}
        connect(..._args: unknown[]) {
            return this;
        }
    },
    NoiseSynth: class {
        constructor(..._args: unknown[]) {}
        connect(..._args: unknown[]) {
            return this;
        }
    },
    Synth: class {
        constructor(..._args: unknown[]) {}
        connect(..._args: unknown[]) {
            return this;
        }
    },
    Filter: class {
        constructor(..._args: unknown[]) {}
        connect(dest: unknown) {
            return dest;
        }
    },
    BitCrusher: class {
        constructor(..._args: unknown[]) {}
        connect(..._args: unknown[]) {
            return this;
        }
    },
    Loop: class {
        constructor(..._args: unknown[]) {}
        start(..._args: unknown[]) {}
        stop(..._args: unknown[]) {}
        dispose(..._args: unknown[]) {}
    },
    getTransport: () => ({ bpm: { value: 0 }, start: () => {} }),
    getDestination: () => ({ mute: false }),
    start: async () => {},
    now: () => 0,
    Frequency: () => ({ toNote: () => "C4" }),
}));
vi.mock("framer-motion", () => ({
    animate: vi.fn(() => ({ stop: vi.fn() })),
}));

import { QuestionRendererFactory } from "./QuestionRendererFactory";
import { McqRenderer } from "./renderers/McqRenderer";
import { TrueFalseRenderer } from "./renderers/TrueFalseRenderer";
import { MultipleSelectRenderer } from "./renderers/MultipleSelectRenderer";
import { NumericRenderer } from "./renderers/NumericRenderer";
import { OutputPredRenderer } from "./renderers/OutputPredRenderer";
import { FillBlankRenderer } from "./renderers/FillBlankRenderer";
import { DragSortRenderer } from "./renderers/DragSortRenderer";
import { ClickBugRenderer } from "./renderers/ClickBugRenderer";
import { CodeCompletionRenderer } from "./renderers/CodeCompletionRenderer";
import { ComplexityRenderer } from "./renderers/ComplexityRenderer";
import { OjFullRenderer } from "./renderers/OjFullRenderer";
import { OjPatchRenderer } from "./renderers/OjPatchRenderer";
import { QuestionType } from "../types";

afterEach(() => {
    vi.restoreAllMocks();
});

function make(type: QuestionType) {
    return QuestionRendererFactory.create(type, document.createElement("div"), {}, () => {});
}

describe("QuestionRendererFactory", () => {
    test("MCQ resolves to McqRenderer", () => {
        expect(make("MCQ")).toBeInstanceOf(McqRenderer);
    });

    test("TRUE_FALSE resolves to TrueFalseRenderer", () => {
        expect(make("TRUE_FALSE")).toBeInstanceOf(TrueFalseRenderer);
    });

    test("MULTIPLE_SELECT resolves to MultipleSelectRenderer", () => {
        expect(make("MULTIPLE_SELECT")).toBeInstanceOf(MultipleSelectRenderer);
    });

    test("NUMERIC resolves to NumericRenderer", () => {
        expect(make("NUMERIC")).toBeInstanceOf(NumericRenderer);
    });

    test("OUTPUT_PRED resolves to OutputPredRenderer", () => {
        expect(make("OUTPUT_PRED")).toBeInstanceOf(OutputPredRenderer);
    });

    test("FILL_BLANK resolves to FillBlankRenderer", () => {
        expect(make("FILL_BLANK")).toBeInstanceOf(FillBlankRenderer);
    });

    test("DRAG_SORT resolves to DragSortRenderer", () => {
        expect(make("DRAG_SORT")).toBeInstanceOf(DragSortRenderer);
    });

    test("CLICK_BUG resolves to ClickBugRenderer", () => {
        expect(make("CLICK_BUG")).toBeInstanceOf(ClickBugRenderer);
    });

    test("CODE_COMPLETION resolves to CodeCompletionRenderer", () => {
        expect(make("CODE_COMPLETION")).toBeInstanceOf(CodeCompletionRenderer);
    });

    test("COMPLEXITY resolves to ComplexityRenderer", () => {
        expect(make("COMPLEXITY")).toBeInstanceOf(ComplexityRenderer);
    });

    test("OJ_FULL resolves to OjFullRenderer", () => {
        expect(make("OJ_FULL")).toBeInstanceOf(OjFullRenderer);
    });

    test("OJ_PATCH resolves to OjPatchRenderer", () => {
        expect(make("OJ_PATCH")).toBeInstanceOf(OjPatchRenderer);
    });

    test("created MCQ renderer answers with its default response", () => {
        const r = QuestionRendererFactory.create("MCQ", document.createElement("div"), { options: ["a"] }, () => {}, "q1", ["python"]);
        expect(r.getResponse()).toEqual({ selectedIndex: -1 });
    });

    test("created OJ renderer answers with empty source", () => {
        const r = QuestionRendererFactory.create("OJ_FULL", document.createElement("div"), {}, () => {}, "q2", null);
        expect(r.getResponse()).toEqual({ source: "", language: "python" });
    });

    test("supported lists all twelve types", () => {
        expect(QuestionRendererFactory.supported()).toHaveLength(12);
        expect(QuestionRendererFactory.supported()).toContain("OJ_PATCH");
    });

    test("unknown type throws a descriptive error", () => {
        expect(() => make("NOPE" as QuestionType)).toThrow("No renderer for type NOPE");
    });
});
