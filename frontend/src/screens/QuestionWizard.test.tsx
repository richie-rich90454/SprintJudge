import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QuestionWizard } from "./QuestionWizard";
import { useAdminStore } from "../stores/useAdminStore";

const wizard = vi.hoisted(() => ({ rendered: [] as any[] }));
vi.mock("../components/QuestionRendererHost", () => ({
    QuestionRendererHost: (props: any) => {
        wizard.rendered.push(props);
        return <div data-testid="renderer" />;
    },
}));

describe("QuestionWizard step focus", () => {
    beforeEach(() => {
        useAdminStore.setState({
            wizardStep: "statement",
            wizardType: "MCQ",
            draft: { title: "A question", questionType: "MCQ", config: {} },
        });
    });

    afterEach(() => {
        useAdminStore.setState({ wizardStep: "type", wizardOpen: false });
    });

    test("exposes the active step and moves focus when the advancing button unmounts", () => {
        render(<QuestionWizard />);

        const statement = screen.getByRole("button", { name: "Statement" });
        expect(statement).toHaveAttribute("aria-current", "step");
        expect(screen.getByRole("button", { name: "Type" })).not.toHaveAttribute("aria-current");

        fireEvent.click(screen.getByRole("button", { name: /Next: configure/i }));

        const config = screen.getByRole("button", { name: "Config" });
        expect(config).toHaveAttribute("aria-current", "step");
        expect(config).toHaveFocus();
        expect(statement).not.toHaveAttribute("aria-current");
    });
});

function setWizard(
    questionType: string,
    config: Record<string, unknown> = {},
    wizardStep: "type" | "statement" | "config" | "preview" = "config",
    extra: Record<string, unknown> = {},
) {
    useAdminStore.setState({
        wizardStep,
        wizardType: questionType as any,
        wizardOpen: true,
        draft: {
            title: "A question",
            description: "Description",
            questionType,
            config,
            timeLimitSec: 30,
            pointsBase: 100,
            ...extra,
        } as any,
        saveQuestion: vi.fn().mockResolvedValue(undefined),
        closeWizard: vi.fn(),
    } as any);
}

const draftConfig = () => useAdminStore.getState().draft.config as Record<string, any>;

describe("QuestionWizard type selection", () => {
    afterEach(() => setWizard("MCQ", {}, "type"));

    test("advances to the statement step and resets config for a core type", () => {
        setWizard("MCQ", { stale: true }, "type");
        render(<QuestionWizard />);
        fireEvent.click(screen.getByRole("button", { name: "NUMERIC" }));
        expect(useAdminStore.getState().wizardStep).toBe("statement");
        expect(useAdminStore.getState().draft.questionType).toBe("NUMERIC");
        expect(draftConfig()).not.toHaveProperty("stale");
    });

    test("installs the default config for every extra type", () => {
        const cases: Record<string, string[]> = {
            "HOT SPOT": ["imageUrl", "regions", "correctRegionIds"],
            MATCHING: ["left", "right", "pairs"],
            "TOLERANCE NUMERIC": ["answer", "tolerance", "toleranceMode", "unit"],
            "MULTI PART": ["parts"],
        };
        for (const [label, keys] of Object.entries(cases)) {
            setWizard("MCQ", {}, "type");
            const { unmount } = render(<QuestionWizard />);
            fireEvent.click(screen.getByRole("button", { name: label }));
            expect(useAdminStore.getState().wizardStep).toBe("statement");
            for (const key of keys) expect(draftConfig()).toHaveProperty(key);
            unmount();
        }
    });
});

describe("QuestionWizard config editors", () => {
    afterEach(() => setWizard("MCQ", {}, "type"));

    test("edits MCQ options and the correct index", () => {
        setWizard("MCQ", { options: ["A", "B"], correctIndex: 0 });
        render(<QuestionWizard />);
        fireEvent.change(screen.getByPlaceholderText("Option A"), { target: { value: "Alpha" } });
        fireEvent.change(screen.getByRole("spinbutton"), { target: { value: "1" } });
        expect(draftConfig().options).toEqual(["Alpha", "B"]);
        expect(draftConfig().correctIndex).toBe(1);
    });

    test("offers a code snippet for OUTPUT_PRED only", () => {
        setWizard("OUTPUT_PRED", { options: ["A"], code: "int x;" });
        render(<QuestionWizard />);
        fireEvent.change(screen.getByPlaceholderText("Code snippet"), { target: { value: "int y;" } });
        expect(draftConfig().code).toBe("int y;");
    });

    test("edits TRUE_FALSE via the select", () => {
        setWizard("TRUE_FALSE", { correct: true });
        render(<QuestionWizard />);
        fireEvent.change(screen.getByRole("combobox"), { target: { value: "false" } });
        expect(draftConfig().correct).toBe(false);
    });

    test("parses MULTIPLE_SELECT indices, dropping invalid entries", () => {
        setWizard("MULTIPLE_SELECT", { options: ["A"], correctIndices: [0] });
        render(<QuestionWizard />);
        fireEvent.change(screen.getByDisplayValue("0"), { target: { value: "0, abc,2" } });
        expect(draftConfig().correctIndices).toEqual([0, 2]);
    });

    test("edits NUMERIC answer and tolerance", () => {
        setWizard("NUMERIC", {});
        render(<QuestionWizard />);
        const inputs = screen.getAllByRole("spinbutton");
        fireEvent.change(inputs[0], { target: { value: "42" } });
        fireEvent.change(inputs[1], { target: { value: "0.5" } });
        expect(draftConfig().answer).toBe(42);
        expect(draftConfig().tolerance).toBe(0.5);
    });

    test("edits FILL_BLANK snippet and answer", () => {
        setWizard("FILL_BLANK", {});
        render(<QuestionWizard />);
        fireEvent.change(screen.getByPlaceholderText("Snippet (use ___ for the blank)"), {
            target: { value: "The capital is ___" },
        });
        fireEvent.change(screen.getByPlaceholderText("Correct answer"), { target: { value: "Paris" } });
        expect(draftConfig().snippet).toBe("The capital is ___");
        expect(draftConfig().answer).toBe("Paris");
    });

    test("adds DRAG_SORT lines and edits the correct order", () => {
        setWizard("DRAG_SORT", {});
        render(<QuestionWizard />);
        expect(screen.getByText("No lines yet - add the first line below.")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Add line" }));
        expect(draftConfig().lines).toHaveLength(1);
        fireEvent.change(screen.getByPlaceholderText("Line 1"), { target: { value: "First" } });
        expect(draftConfig().lines[0].text).toBe("First");
        fireEvent.change(screen.getByPlaceholderText("e.g. 2,0,1"), { target: { value: "0,1" } });
        expect(draftConfig().correctOrder).toBe("0,1");
    });

    test("edits CLICK_BUG code lines from an empty draft", () => {
        setWizard("CLICK_BUG", {});
        render(<QuestionWizard />);
        expect(screen.getByText("No code lines yet - add one buggy line per row below.")).toBeInTheDocument();
        fireEvent.change(screen.getByPlaceholderText("One buggy line per row"), {
            target: { value: "line1\nline2" },
        });
        expect(draftConfig().codeLines).toEqual(["line1", "line2"]);
        fireEvent.change(screen.getByRole("spinbutton"), { target: { value: "1" } });
        expect(draftConfig().bugLine).toBe(1);
    });

    test("edits CODE_COMPLETION skeleton and expected code", () => {
        setWizard("CODE_COMPLETION", {});
        render(<QuestionWizard />);
        fireEvent.change(screen.getByPlaceholderText("Skeleton / starter code (editable region)"), {
            target: { value: "TODO" },
        });
        fireEvent.change(screen.getByPlaceholderText("Expected completed code (used to grade)"), {
            target: { value: "done" },
        });
        expect(draftConfig().skeleton).toBe("TODO");
        expect(draftConfig().expected).toBe("done");
    });

    test("edits OJ_FULL and OJ_PATCH test cases and buggy/starter code", () => {
        setWizard("OJ_FULL", { testCases: [{ input: "1", expectedOutput: "2", isHidden: false }] });
        const { unmount } = render(<QuestionWizard />);
        expect(screen.getByPlaceholderText("Starter code")).toBeInTheDocument();
        fireEvent.change(screen.getByPlaceholderText("input"), { target: { value: "3" } });
        expect(draftConfig().testCases[0].input).toBe("3");
        fireEvent.change(screen.getByPlaceholderText("expected"), { target: { value: "4" } });
        expect(draftConfig().testCases[0].expectedOutput).toBe("4");
        fireEvent.click(screen.getByRole("button", { name: "Add test case" }));
        expect(draftConfig().testCases).toHaveLength(2);
        unmount();

        setWizard("OJ_PATCH", {});
        render(<QuestionWizard />);
        expect(screen.getByPlaceholderText("Buggy function")).toBeInTheDocument();
        fireEvent.change(screen.getByPlaceholderText("Buggy function"), { target: { value: "bug" } });
        expect(draftConfig().buggyFunction).toBe("bug");
    });

    test("edits MULTIPLE_SELECT option text", () => {
        setWizard("MULTIPLE_SELECT", { options: ["A", "B"], correctIndices: [0] });
        render(<QuestionWizard />);
        fireEvent.change(screen.getByPlaceholderText("Option A"), { target: { value: "Alpha" } });
        expect(draftConfig().options).toEqual(["Alpha", "B"]);
    });

    test("edits the OJ_FULL starter code", () => {
        setWizard("OJ_FULL", { starter: "old" });
        render(<QuestionWizard />);
        fireEvent.change(screen.getByPlaceholderText("Starter code"), {
            target: { value: "new" },
        });
        expect(draftConfig().starter).toBe("new");
    });

    test("editors tolerate an empty config object", () => {
        for (const type of ["OUTPUT_PRED", "TRUE_FALSE", "MULTIPLE_SELECT"]) {
            setWizard(type, {}, "config");
            const { unmount } = render(<QuestionWizard />);
            expect(screen.queryByText("No config editor for this type.")).not.toBeInTheDocument();
            unmount();
        }
        act(() =>
            useAdminStore.setState({
                wizardStep: "config",
                draft: { config: undefined } as any,
            } as any),
        );
        render(<QuestionWizard />);
        expect(screen.getByText("No config editor for this type.")).toBeInTheDocument();
    });

    test("falls back when no editor exists for the type", () => {
        setWizard("UNKNOWN", {});
        render(<QuestionWizard />);
        expect(screen.getByText("No config editor for this type.")).toBeInTheDocument();
    });
});

describe("QuestionWizard extra type editors", () => {
    afterEach(() => setWizard("MCQ", {}, "type"));

    test("HOT_SPOT edits regions, dimensions and correctness", () => {
        setWizard("HOT_SPOT", {
            imageUrl: "https://example.com/a.png",
            width: 100,
            height: 100,
            regions: [{ id: "a", label: "A", x: 50, y: 50, r: 8 }],
            correctRegionIds: ["a"],
        });
        render(<QuestionWizard />);
        fireEvent.change(screen.getByLabelText("Image URL"), { target: { value: "https://example.com/b.png" } });
        expect(draftConfig().imageUrl).toBe("https://example.com/b.png");
        fireEvent.change(screen.getByLabelText("Label"), { target: { value: "B" } });
        expect(draftConfig().regions[0].label).toBe("B");
        fireEvent.click(screen.getByRole("button", { name: "Add region" }));
        expect(draftConfig().regions).toHaveLength(2);
        fireEvent.click(screen.getAllByRole("checkbox")[0]);
        expect(draftConfig().correctRegionIds).toEqual([]);
    });

    test("MATCHING edits prompt lines and pairs", () => {
        setWizard("MATCHING", { left: ["", ""], right: ["", ""], pairs: [{ left: 0, right: 0 }] });
        render(<QuestionWizard />);
        fireEvent.change(screen.getByLabelText(/Left prompts/), { target: { value: "One\nTwo" } });
        expect(draftConfig().left).toEqual(["One", "Two"]);
        fireEvent.change(screen.getByLabelText(/Right prompts/), { target: { value: "A\nB" } });
        expect(draftConfig().right).toEqual(["A", "B"]);
        fireEvent.click(screen.getByRole("button", { name: "Add pair" }));
        expect(draftConfig().pairs).toHaveLength(2);
    });

    test("TOLERANCE_NUMERIC edits answer, tolerance, mode and unit", () => {
        setWizard("TOLERANCE_NUMERIC", {});
        render(<QuestionWizard />);
        fireEvent.change(screen.getByLabelText("Mode"), { target: { value: "relative" } });
        expect(draftConfig().toleranceMode).toBe("relative");
        fireEvent.change(screen.getByLabelText("Unit"), { target: { value: "kg" } });
        expect(draftConfig().unit).toBe("kg");
    });

    test("MULTI_PART edits, removes and adds parts", () => {
        setWizard("MULTI_PART", {
            parts: [{ id: "p1", prompt: "P", kind: "TEXT", options: [], answer: "A", points: 1 }],
        });
        render(<QuestionWizard />);
        fireEvent.change(screen.getByPlaceholderText("Part 1 prompt"), { target: { value: "New" } });
        expect(draftConfig().parts[0].prompt).toBe("New");
        fireEvent.change(screen.getByLabelText("Kind"), { target: { value: "MCQ" } });
        expect(draftConfig().parts[0].kind).toBe("MCQ");
        fireEvent.change(screen.getByLabelText("Options (comma separated)"), {
            target: { value: "a, b" },
        });
        expect(draftConfig().parts[0].options).toEqual(["a", "b"]);
        fireEvent.click(screen.getByRole("button", { name: "Remove" }));
        expect(draftConfig().parts).toHaveLength(0);
        fireEvent.click(screen.getByRole("button", { name: "Add part" }));
        expect(draftConfig().parts[0]).toMatchObject({ kind: "TEXT", points: 1 });
    });
});

describe("QuestionWizard statement, preview and save", () => {
    beforeEach(() => {
        wizard.rendered = [];
        setWizard("MCQ", {}, "statement");
    });
    afterEach(() => setWizard("MCQ", {}, "type"));

    test("edits the statement, timing, points and rich stem media", () => {
        render(<QuestionWizard />);
        fireEvent.change(screen.getByPlaceholderText("Title"), { target: { value: "Edited" } });
        fireEvent.change(screen.getByPlaceholderText("Description (Markdown)"), {
            target: { value: "New description" },
        });
        fireEvent.change(screen.getByLabelText("Time limit (s)"), { target: { value: "60" } });
        fireEvent.change(screen.getByLabelText("Base points"), { target: { value: "250" } });
        fireEvent.change(screen.getByLabelText("Image URL"), { target: { value: "javascript:alert(1)" } });
        expect(useAdminStore.getState().draft.title).toBe("Edited");
        expect(useAdminStore.getState().draft.description).toBe("New description");
        expect(useAdminStore.getState().draft.timeLimitSec).toBe(60);
        expect(useAdminStore.getState().draft.pointsBase).toBe(250);
        expect(screen.getByText("Image URL blocked by the preview allowlist.")).toBeInTheDocument();
    });

    test("renders a normal preview with the built question and navigates back to config", () => {
        wizard.rendered = [];
        useAdminStore.setState({
            wizardStep: "preview",
            draft: { ...useAdminStore.getState().draft, title: "Edited" } as any,
        });
        render(<QuestionWizard />);
        expect(screen.getByTestId("renderer")).toBeInTheDocument();
        expect(wizard.rendered[0].question).toMatchObject({ id: "preview", title: "Edited", type: "MCQ" });
        fireEvent.click(screen.getByRole("button", { name: "Back" }));
        expect(useAdminStore.getState().wizardStep).toBe("config");
    });

    test("renders the extra-type preview and rich-media author preview", () => {
        setWizard("MATCHING", {
            left: ["L"],
            right: ["R"],
            pairs: [{ left: 0, right: 0 }],
            rich: { latex: "x^2" },
        }, "preview");
        render(<QuestionWizard />);
        expect(wizard.rendered).toHaveLength(0);
        expect(screen.getByText("L → R")).toBeInTheDocument();
        expect(screen.getAllByText("x^2").length).toBeGreaterThan(0);
    });

    test("blocks saving without a title and returns to the statement step", () => {
        setWizard("MCQ", {}, "preview", { title: "  " });
        render(<QuestionWizard />);
        fireEvent.click(screen.getByRole("button", { name: "Save question" }));
        expect(screen.getAllByRole("alert")[0]).toHaveTextContent("Title is required");
        expect(useAdminStore.getState().wizardStep).toBe("statement");
        expect(useAdminStore.getState().saveQuestion).not.toHaveBeenCalled();
    });

    test("saves successfully and reports generic and detailed failures", async () => {
        setWizard("MCQ", {}, "preview");
        const save = useAdminStore.getState().saveQuestion as ReturnType<typeof vi.fn>;
        render(<QuestionWizard />);
        fireEvent.click(screen.getByRole("button", { name: "Save question" }));
        await waitFor(() => expect(save).toHaveBeenCalledOnce());
        expect(screen.queryAllByRole("alert")).toHaveLength(0);

        (save as any).mockRejectedValueOnce(new Error("boom"));
        fireEvent.click(screen.getByRole("button", { name: "Save question" }));
        expect(await screen.findByText(/Save failed/)).toBeInTheDocument();

        (save as any).mockRejectedValueOnce({ response: { data: { error: "Invalid config" } } });
        fireEvent.click(screen.getByRole("button", { name: "Save question" }));
        expect(await screen.findByText("Save failed: Invalid config")).toBeInTheDocument();
    });

    test("shows the saving state while the request is outstanding", async () => {
        let resolve!: () => void;
        const save = vi.fn(() => new Promise<void>((r) => (resolve = r)));
        useAdminStore.setState({ saveQuestion: save, wizardStep: "preview" } as any);
        render(<QuestionWizard />);
        fireEvent.click(screen.getByRole("button", { name: "Save question" }));
        expect(screen.getByRole("button", { name: "Saving…" })).toBeDisabled();
        resolve();
        await waitFor(() => expect(screen.getByRole("button", { name: "Save question" })).toBeEnabled());
    });

    test("step buttons and Preview navigate between steps", () => {
        setWizard("MCQ", { options: ["A"] }, "config");
        render(<QuestionWizard />);
        fireEvent.click(screen.getByRole("button", { name: "Statement" }));
        expect(useAdminStore.getState().wizardStep).toBe("statement");
        act(() => setWizard("MCQ", { options: ["A"] }, "config"));
        const previewButtons = Array.from(document.querySelectorAll("button")).filter(
            (b) => b.textContent === "Preview",
        );
        expect(previewButtons.length).toBeGreaterThanOrEqual(2);
        fireEvent.click(previewButtons[previewButtons.length - 1]);
        expect(useAdminStore.getState().wizardStep).toBe("preview");
    });

    test("preview renderer response callback is harmless and missing fields fall back", () => {
        wizard.rendered = [];
        useAdminStore.setState({
            wizardStep: "preview",
            draft: {} as any,
            saveQuestion: vi.fn().mockResolvedValue(undefined),
            closeWizard: vi.fn(),
        } as any);
        render(<QuestionWizard />);
        expect(wizard.rendered[0].question.title).toBe("Preview question");
        expect(() => wizard.rendered[0].onResponse({})).not.toThrow();
        fireEvent.click(screen.getByRole("button", { name: "Save question" }));
        expect(screen.getAllByRole("alert")[0]).toHaveTextContent("Title is required");
    });

    test("closing the dialog calls closeWizard", () => {
        const close = vi.fn();
        useAdminStore.setState({ closeWizard: close } as any);
        render(<QuestionWizard />);
        fireEvent.click(screen.getByRole("button", { name: "Close dialog" }));
        expect(close).toHaveBeenCalledOnce();
    });
});
