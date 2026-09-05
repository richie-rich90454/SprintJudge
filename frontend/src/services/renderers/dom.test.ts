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

describe("el security edges", () => {
    test("script strings become inert text, never elements", () => {
        const node = el("div", {}, ["<script>alert(1)</script>"]);
        expect(node.querySelector("script")).toBeNull();
        expect(node.textContent).toBe("<script>alert(1)</script>");
    });

    test("markup in option text stays inert across several payloads", () => {
        const payloads = [
            "<img src=x onerror=alert(1)>",
            "<svg onload=alert(1)>",
            "<a href=\"javascript:alert(1)\">click</a>",
        ];
        for (const p of payloads) {
            const node = el("button", {}, [p]);
            expect(node.querySelector("img")).toBeNull();
            expect(node.querySelector("svg")).toBeNull();
            expect(node.querySelector("a")).toBeNull();
            expect(node.textContent).toBe(p);
        }
    });

    test("innerHTML assignment parses but never executes scripts", () => {
        const alerts: unknown[] = [];
        const orig = window.alert;
        Object.defineProperty(window, "alert", { value: (...a: unknown[]) => alerts.push(a), writable: true, configurable: true });
        const node = el("div", { innerHTML: "<script>alert(1)</script><b>safe</b>" });
        expect(node.querySelector("b")?.textContent).toBe("safe");
        expect(alerts).toHaveLength(0);
        Object.defineProperty(window, "alert", { value: orig, writable: true, configurable: true });
    });

    test("input values hold markup as plain strings", () => {
        const input = el("input", { type: "text", value: "<script>alert(1)</script>" });
        expect(input.value).toBe("<script>alert(1)</script>");
        expect(input.querySelector("script")).toBeNull();
    });

    test("dataset preserves arbitrary string keys verbatim", () => {
        const node = el("div", { dataset: { payload: "<script>alert(1)</script>", quote: "\"'><&" } });
        expect(node.dataset.payload).toBe("<script>alert(1)</script>");
        expect(node.dataset.quote).toBe("\"'><&");
        expect(node.querySelector("script")).toBeNull();
    });

    test("clear wipes injected markup then stays empty on repeat", () => {
        const node = el("div", {}, ["<script>alert(1)</script>"]);
        clear(node);
        clear(node);
        expect(node.innerHTML).toBe("");
        expect(node.textContent).toBe("");
    });

    test("class dataset and children compose without interference", () => {
        const node = el("div", { class: "wrap", dataset: { kind: "row" } }, [el("span", {}, ["<b>not-bold</b>"])]);
        expect(node.className).toBe("wrap");
        expect(node.dataset.kind).toBe("row");
        expect(node.querySelector("b")).toBeNull();
        expect(node.textContent).toBe("<b>not-bold</b>");
    });

    test("button type and disabled props apply together", () => {
        const btn = el("button", { type: "submit", disabled: true }, ["Go"]);
        expect(btn.type).toBe("submit");
        expect(btn.disabled).toBe(true);
        btn.disabled = false;
        btn.click();
        expect(btn.textContent).toBe("Go");
    });

    test("select value assigned before children falls back then sticks after", () => {
        const node = el("select", { value: "b" }, [el("option", { value: "a" }, ["A"]), el("option", { value: "b" }, ["B"])]);
        expect(node.querySelectorAll("option")).toHaveLength(2);
        expect(node.value).toBe("a");
        node.value = "b";
        expect(node.value).toBe("b");
        expect(node.textContent).toContain("A");
    });

    test("mixed text and node children keep document order", () => {
        const mid = document.createElement("i");
        mid.textContent = "mid";
        const node = el("p", {}, ["first-", mid, "-last<script>x</script>"]);
        expect(node.textContent).toBe("first-mid-last<script>x</script>");
        expect(node.querySelector("script")).toBeNull();
        expect(node.querySelector("i")?.textContent).toBe("mid");
    });
});
