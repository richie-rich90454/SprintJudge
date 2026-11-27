import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, test, vi } from "vitest";
import type { ExtraQuestionType } from "./authoringTypes";
import { ExtraTypeConfigForm, ExtraTypePreview } from "./ExtraTypeConfigForm";

function Stateful({
    type,
    initial,
    onChange = vi.fn(),
}: {
    type: ExtraQuestionType;
    initial: Record<string, unknown>;
    onChange?: (patch: Record<string, unknown>) => void;
}) {
    const [config, setConfig] = useState(initial);
    const handle = (patch: Record<string, unknown>) => {
        onChange(patch);
        setConfig((prev) => ({ ...prev, ...patch }));
    };
    return (
        <>
            <ExtraTypeConfigForm type={type} config={config} onChange={handle} />
            <ExtraTypePreview type={type} config={config} />
            <pre data-testid="cfg">{JSON.stringify(config)}</pre>
        </>
    );
}

const cfg = () => screen.getByTestId("cfg").textContent ?? "";

describe("ExtraTypeConfigForm - HOT_SPOT", () => {
    const initial = {
        imageUrl: "",
        width: 100,
        height: 100,
        regions: [{ id: "a", label: "A", x: 50, y: 50, r: 8 }],
        correctRegionIds: ["a"],
    };

    test("edits image, dimensions, region fields and correctness, and adds a region", () => {
        const onChange = vi.fn();
        render(<Stateful type="HOT_SPOT" initial={initial} onChange={onChange} />);

        fireEvent.change(screen.getByLabelText("Image URL"), {
            target: { value: "https://x/img.png" },
        });
        expect(onChange).toHaveBeenCalledWith({ imageUrl: "https://x/img.png" });

        const numbers = screen.getAllByRole("spinbutton");
        expect(numbers).toHaveLength(5);
        fireEvent.change(numbers[0], { target: { value: "200" } });
        fireEvent.change(numbers[1], { target: { value: "150" } });
        fireEvent.change(numbers[2], { target: { value: "40" } });
        fireEvent.change(numbers[3], { target: { value: "60" } });
        fireEvent.change(numbers[4], { target: { value: "12" } });

        fireEvent.change(screen.getByLabelText("Label"), { target: { value: "Region A" } });
        const box = screen.getByRole("checkbox");
        expect(box).toBeChecked();
        fireEvent.click(box);
        expect(cfg()).toContain('"correctRegionIds":[]');

        fireEvent.click(screen.getByRole("button", { name: "Add region" }));
        expect(screen.getAllByRole("spinbutton")).toHaveLength(8);
        expect(cfg()).toContain('"label":""');
    });

    test("preview shows an http image, correct border, and re-checking a region", () => {
        render(
            <Stateful type="HOT_SPOT" initial={{ ...initial, imageUrl: "https://x/img.png" }} />,
        );
        expect(screen.getByRole("img", { name: "Hot-spot target" })).toBeInTheDocument();
        const region = document.querySelector("div.rounded-full") as HTMLElement;
        expect(region.getAttribute("style")).toContain("var(--oq-accent)");

        fireEvent.click(screen.getByRole("checkbox"));
        expect(region.getAttribute("style")).toContain("var(--oq-border)");
        fireEvent.click(screen.getByRole("checkbox"));
        expect(region.getAttribute("style")).toContain("var(--oq-accent)");
    });

    test("preview ignores a non-http image URL", () => {
        render(
            <Stateful type="HOT_SPOT" initial={{ ...initial, imageUrl: "javascript:alert(1)" }} />,
        );
        expect(screen.queryByRole("img")).not.toBeInTheDocument();
    });
});

describe("ExtraTypeConfigForm - MATCHING", () => {
    const initial = {
        left: ["", ""],
        right: ["", ""],
        pairs: [{ left: 0, right: 0 }],
    };

    test("edits prompt lists, pair selects and adds a pair", () => {
        const onChange = vi.fn();
        render(<Stateful type="MATCHING" initial={initial} onChange={onChange} />);

        expect(screen.getAllByText("Left 1").length).toBeGreaterThan(0);
        expect(screen.getAllByText("Right 2").length).toBeGreaterThan(0);

        fireEvent.change(screen.getByLabelText("Left prompts (one per line)"), {
            target: { value: "alpha\nbeta\n" },
        });
        fireEvent.change(screen.getByLabelText("Right prompts (one per line)"), {
            target: { value: "one\ntwo\n" },
        });
        expect(onChange).toHaveBeenCalledWith({ left: ["alpha", "beta"] });
        expect(onChange).toHaveBeenCalledWith({ right: ["one", "two"] });

        const selects = screen.getAllByRole("combobox");
        fireEvent.change(selects[0], { target: { value: "1" } });
        fireEvent.change(selects[1], { target: { value: "1" } });
        expect(cfg()).toContain('"pairs":[{"left":1,"right":1}]');

        fireEvent.click(screen.getByRole("button", { name: "Add pair" }));
        expect(screen.getAllByRole("combobox")).toHaveLength(4);
    });

    test("preview renders generated labels, pairs and the no-pairs message", () => {
        const { unmount } = render(
            <Stateful
                type="MATCHING"
                initial={{ left: [""], right: [""], pairs: [{ left: 0, right: 0 }] }}
            />,
        );
        expect(screen.getAllByText("Left 1").length).toBeGreaterThan(0);
        expect(screen.getAllByText("Right 1").length).toBeGreaterThan(0);

        unmount();
        render(
            <Stateful
                type="MATCHING"
                initial={{ left: ["alpha"], right: ["one"], pairs: [{ left: 0, right: 0 }] }}
            />,
        );
        expect(screen.getByText("alpha → one")).toBeInTheDocument();

        const empty = render(
            <Stateful type="MATCHING" initial={{ left: [], right: [], pairs: [] }} />,
        );
        expect(empty.getByText("No pairs set")).toBeInTheDocument();
    });
});

describe("ExtraTypeConfigForm - TOLERANCE_NUMERIC", () => {
    test("edits answer, tolerance, mode and unit", () => {
        const onChange = vi.fn();
        render(
            <Stateful
                type="TOLERANCE_NUMERIC"
                initial={{ answer: 0, tolerance: 0, toleranceMode: "absolute", unit: "" }}
                onChange={onChange}
            />,
        );
        const numbers = screen.getAllByRole("spinbutton");
        fireEvent.change(numbers[0], { target: { value: "5" } });
        fireEvent.change(numbers[1], { target: { value: "2" } });
        fireEvent.change(screen.getByRole("combobox"), { target: { value: "relative" } });
        fireEvent.change(screen.getByLabelText("Unit"), { target: { value: "cm" } });
        expect(onChange).toHaveBeenCalledWith({ answer: 5 });
        expect(onChange).toHaveBeenCalledWith({ tolerance: 2 });
        expect(onChange).toHaveBeenCalledWith({ toleranceMode: "relative" });
        expect(onChange).toHaveBeenCalledWith({ unit: "cm" });
        expect(screen.getByText("Expected: 5 ± 2% cm")).toBeInTheDocument();
    });

    test("preview uses absolute mode without a percent suffix", () => {
        render(
            <Stateful
                type="TOLERANCE_NUMERIC"
                initial={{ answer: 7, tolerance: 1, toleranceMode: "absolute", unit: "" }}
            />,
        );
        expect(screen.getByText(/Expected: 7 ± 1/)).toBeInTheDocument();
    });
});

describe("ExtraTypeConfigForm - MULTI_PART", () => {
    const part = { id: "p1", prompt: "", kind: "TEXT", options: [], answer: "", points: 1 };

    test("edits a part, shows MCQ options, removes and adds parts", () => {
        const onChange = vi.fn();
        render(<Stateful type="MULTI_PART" initial={{ parts: [part] }} onChange={onChange} />);

        fireEvent.change(screen.getByPlaceholderText("Part 1 prompt"), {
            target: { value: "First" },
        });
        fireEvent.change(screen.getByLabelText("Kind"), { target: { value: "MCQ" } });
        const numbers = screen.getAllByRole("spinbutton");
        fireEvent.change(numbers[0], { target: { value: "3" } });
        fireEvent.change(screen.getByLabelText("Expected answer"), { target: { value: "yes" } });
        expect(onChange).toHaveBeenCalledWith({
            parts: [expect.objectContaining({ prompt: "First" })],
        });
        expect(onChange).toHaveBeenCalledWith({
            parts: [expect.objectContaining({ kind: "MCQ" })],
        });
        expect(onChange).toHaveBeenCalledWith({ parts: [expect.objectContaining({ points: 3 })] });
        expect(onChange).toHaveBeenCalledWith({
            parts: [expect.objectContaining({ answer: "yes" })],
        });

        fireEvent.change(screen.getByLabelText("Options (comma separated)"), {
            target: { value: "yes, no" },
        });
        expect(cfg()).toContain('"options":["yes","no"]');

        fireEvent.click(screen.getByRole("button", { name: "Remove" }));
        expect(cfg()).toContain('"parts":[]');
        fireEvent.click(screen.getByRole("button", { name: "Add part" }));
        expect(cfg()).toContain('"id":"p1"');
    });

    test("preview uses unset fallbacks for blank parts", () => {
        render(<Stateful type="MULTI_PART" initial={{ parts: [part] }} />);
        expect(screen.getByText(/Part 1 - TEXT, 1 pt\(s\), answer: \(unset\)/)).toBeInTheDocument();
    });
});

describe("ExtraTypeConfigForm empty-config fallbacks", () => {
    test("HOT_SPOT defaults dimensions and tolerates empty lists", () => {
        render(<Stateful type="HOT_SPOT" initial={{ width: "abc", height: "abc" }} />);
        const numbers = screen.getAllByRole("spinbutton");
        expect(numbers[0]).toHaveValue(100);
        expect(numbers[1]).toHaveValue(100);
        expect(screen.queryAllByRole("checkbox")).toHaveLength(0);
        expect(document.querySelector("img")).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Add region" })).toBeInTheDocument();
    });

    test("HOT_SPOT updates a region other than the first", () => {
        render(
            <Stateful
                type="HOT_SPOT"
                initial={{
                    regions: [
                        { id: "a", label: "A", x: 10, y: 10, r: 5 },
                        { id: "b", label: "B", x: 20, y: 20, r: 6 },
                    ],
                    correctRegionIds: [],
                }}
            />,
        );
        fireEvent.change(screen.getByDisplayValue("B"), { target: { value: "Second" } });
        expect(cfg()).toContain('"label":"Second"');
        expect(cfg()).toContain('"label":"A"');
    });

    test("MATCHING defaults empty lists and renders raw out-of-range pair indices", () => {
        const empty = render(<Stateful type="MATCHING" initial={{}} />);
        expect(empty.getByText("No pairs set")).toBeInTheDocument();
        expect(empty.getAllByRole("textbox")[0]).toHaveValue("");
        empty.unmount();

        render(
            <Stateful
                type="MATCHING"
                initial={{ left: [], right: [], pairs: [{ left: 3, right: 4 }] }}
            />,
        );
        expect(screen.getByText("3 → 4")).toBeInTheDocument();
    });

    test("MATCHING updates a pair other than the first", () => {
        render(
            <Stateful
                type="MATCHING"
                initial={{
                    left: ["a", "b"],
                    right: ["c", "d"],
                    pairs: [
                        { left: 0, right: 0 },
                        { left: 1, right: 1 },
                    ],
                }}
            />,
        );
        const selects = screen.getAllByRole("combobox");
        fireEvent.change(selects[3], { target: { value: "0" } });
        fireEvent.change(selects[2], { target: { value: "0" } });
        expect(cfg()).toContain('"pairs":[{"left":0,"right":0},{"left":0,"right":0}]');
    });

    test("TOLERANCE_NUMERIC defaults mode and unit", () => {
        render(<Stateful type="TOLERANCE_NUMERIC" initial={{}} />);
        expect(screen.getByRole("combobox")).toHaveValue("absolute");
        expect(screen.getByLabelText("Unit")).toHaveValue("");
        expect(screen.getByText(/Expected: 0 ± 0/)).toBeInTheDocument();
    });

    test("MULTI_PART defaults parts and shows options only for MCQ", () => {
        const empty = render(<Stateful type="MULTI_PART" initial={{}} />);
        expect(empty.queryByPlaceholderText("Part 1 prompt")).not.toBeInTheDocument();
        expect(empty.getByRole("button", { name: "Add part" })).toBeInTheDocument();
        expect(empty.container.querySelector("ol")).toBeEmptyDOMElement();
        empty.unmount();

        render(
            <Stateful
                type="MULTI_PART"
                initial={{
                    parts: [
                        { id: "p1", prompt: "First", kind: "TEXT", answer: "a", points: 1 },
                        { id: "p2", prompt: "Second", kind: "TEXT", answer: "b", points: 2 },
                    ],
                }}
            />,
        );
        expect(screen.queryByLabelText("Options (comma separated)")).not.toBeInTheDocument();
        fireEvent.change(screen.getByDisplayValue("Second"), { target: { value: "Updated" } });
        expect(cfg()).toContain('"prompt":"Updated"');
        fireEvent.change(screen.getAllByRole("combobox")[1], { target: { value: "MCQ" } });
        expect(screen.getByLabelText("Options (comma separated)")).toHaveValue("");
    });
});
