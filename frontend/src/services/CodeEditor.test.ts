import { beforeEach, describe, expect, test } from "vitest";
import { __typeText, behavior, created } from "../../test/mocks/monaco";
import { createCodeEditor } from "./CodeEditor";

function host(): HTMLElement {
    const div = document.createElement("div");
    document.body.appendChild(div);
    return div;
}

describe("createCodeEditor (monaco path)", () => {
    beforeEach(() => {
        behavior.failCreate = false;
        behavior.partialMount = false;
        created.length = 0;
        document.body.innerHTML = "";
    });

    test("mounts with value and language", async () => {
        const seen: string[] = [];
        const handle = await createCodeEditor(host(), {
            value: "print(1)",
            language: "python",
            onChange: (v) => seen.push(v),
        });
        expect(handle.getValue()).toBe("print(1)");
        expect(created.length).toBe(1);
        expect(created[0]!.options["language"]).toBe("python");
        expect(seen).toEqual([]);
        handle.destroy();
        expect(created[0]!.disposed).toBe(true);
    });

    test("change listener forwards edits", async () => {
        const seen: string[] = [];
        const handle = await createCodeEditor(host(), {
            value: "a",
            language: "python",
            onChange: (v) => seen.push(v),
        });
        __typeText(created[0]!, "b");
        expect(seen).toEqual(["b"]);
        expect(handle.getValue()).toBe("b");
        handle.destroy();
    });

    test("setLanguage switches highlighting", async () => {
        const handle = await createCodeEditor(host(), {
            value: "x",
            language: "python",
            onChange: () => {},
        });
        handle.setLanguage("java");
        expect(created[0]!.options["language"]).toBe("java");
        handle.destroy();
    });

    test("destroy disposes listener", async () => {
        const seen: string[] = [];
        const handle = await createCodeEditor(host(), {
            value: "x",
            language: "python",
            onChange: (v) => seen.push(v),
        });
        handle.destroy();
        __typeText(created[0]!, "late");
        expect(seen).toEqual([]);
    });

    test("setLanguage with no model is safe", async () => {
        behavior.nullModel = true;
        try {
            const handle = await createCodeEditor(host(), {
                value: "x",
                language: "python",
                onChange: () => {},
            });
            handle.setLanguage("java");
            handle.destroy();
        } finally {
            behavior.nullModel = false;
        }
    });
});

describe("createCodeEditor (textarea fallback)", () => {
    beforeEach(() => {
        behavior.failCreate = true;
        behavior.partialMount = false;
        created.length = 0;
        document.body.innerHTML = "";
    });

    test("fallback renders editable textarea", async () => {
        const seen: string[] = [];
        const h = host();
        const handle = await createCodeEditor(h, {
            value: "starter",
            language: "python",
            onChange: (v) => seen.push(v),
        });
        const ta = h.querySelector("textarea");
        expect(ta).not.toBeNull();
        expect(handle.getValue()).toBe("starter");
        ta!.value = "edited";
        ta!.dispatchEvent(new Event("input", { bubbles: true }));
        expect(seen).toEqual(["edited"]);
        expect(handle.getValue()).toBe("edited");
        handle.destroy();
        expect(h.querySelector("textarea")).toBeNull();
    });

    test("fallback honors custom height", async () => {
        const h = host();
        const handle = await createCodeEditor(h, {
            value: "",
            language: "python",
            height: 400,
            onChange: () => {},
        });
        expect(h.querySelector("textarea")!.style.minHeight).toBe("400px");
        handle.destroy();
    });

    test("setLanguage is a safe noop on fallback", async () => {
        const h = host();
        const handle = await createCodeEditor(h, {
            value: "",
            language: "python",
            onChange: () => {},
        });
        handle.setLanguage("java");
        handle.destroy();
    });
});

describe("createCodeEditor (partial mount cleanup)", () => {
    beforeEach(() => {
        behavior.failCreate = false;
        behavior.partialMount = true;
        created.length = 0;
        document.body.innerHTML = "";
    });

    test("broken monaco DOM is cleared before fallback", async () => {
        const h = host();
        await createCodeEditor(h, {
            value: "x",
            language: "python",
            onChange: () => {},
        });
        expect(h.querySelectorAll("div").length).toBe(0);
        expect(h.querySelector("textarea")).not.toBeNull();
    });
});

describe("editor worker environment", () => {
    test("worker factory constructs", () => {
        const env = (
            self as unknown as {
                MonacoEnvironment?: { getWorker: () => unknown };
            }
        ).MonacoEnvironment;
        expect(env).toBeDefined();
        expect(env!.getWorker()).toBeTruthy();
    });
});
