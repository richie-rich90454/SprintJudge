import { describe, test, expect, vi, afterEach } from "vitest";
import { el, clear } from "./dom";

afterEach(() => {
    vi.restoreAllMocks();
});

describe("el", () => {
    test("creates an element with class and text child", () => {
        const node = el("div", { class: "box" }, ["hi"]);
        expect(node.tagName).toBe("DIV");
        expect(node.className).toBe("box");
        expect(node.textContent).toBe("hi");
    });

    test("assigns dataset entries", () => {
        const node = el("div", { dataset: { drag: "2", kind: "row" } });
        expect(node.dataset.drag).toBe("2");
        expect(node.dataset.kind).toBe("row");
    });

    test("assigns extra props onto the node", () => {
        const input = el("input", { type: "number", placeholder: "Enter a number" });
        expect(input.type).toBe("number");
        expect(input.placeholder).toBe("Enter a number");
    });

    test("appends node children alongside strings", () => {
        const child = document.createElement("span");
        child.textContent = "inner";
        const node = el("div", {}, [child, "tail"]);
        expect(node.querySelector("span")?.textContent).toBe("inner");
        expect(node.textContent).toContain("tail");
    });

    test("works with no props or children", () => {
        const node = el("p");
        expect(node.tagName).toBe("P");
        expect(node.childNodes).toHaveLength(0);
    });

    test("select value and textContent props apply", () => {
        const node = el("button", { textContent: "▶ Run" });
        expect(node.textContent).toBe("▶ Run");
    });
});

describe("clear", () => {
    test("empties the node", () => {
        const node = document.createElement("div");
        node.innerHTML = "<b>x</b>text";
        clear(node);
        expect(node.innerHTML).toBe("");
    });
});
