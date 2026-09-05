import { readFileSync, readdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
let JSDOM;
try {
    ({ JSDOM } = await import("../frontend/node_modules/jsdom/lib/api.js"));
} catch {
    console.error("check-mermaid needs frontend dev deps: run `npm install` in frontend/ first.");
    process.exit(2);
}

const dom = new JSDOM("<!doctype html><html><body></body></html>", {
    pretendToBeVisual: true,
    url: "http://localhost/",
});
for (const key of [
    "document",
    "Element",
    "HTMLElement",
    "SVGElement",
    "Node",
    "Text",
    "Comment",
    "DocumentFragment",
    "DOMParser",
    "XMLSerializer",
    "getComputedStyle",
    "requestAnimationFrame",
    "cancelAnimationFrame",
]) {
    try {
        if (key in dom.window) {
            const v = dom.window[key];
            globalThis[key] = typeof v === "function" ? v.bind(dom.window) : v;
        }
    } catch {
        /* read-only global (navigator, self): leave Node's own */
    }
}
globalThis.window = dom.window;
globalThis.self = dom.window;

const mermaid = (await import("mermaid")).default;
mermaid.initialize({ startOnLoad: false, securityLevel: "loose" });

const root = dirname(fileURLToPath(import.meta.url));
let ok = 0;
let bad = 0;
for (const name of readdirSync(root).filter((f) => f.endsWith(".md"))) {
    const lines = readFileSync(join(root, name), "utf8").split("\n");
    let buf = null;
    let start = 0;
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.trim() === "```mermaid") {
            buf = [];
            start = i + 1;
        } else if (buf !== null && line.trim() === "```") {
            const src = buf.join("\n");
            try {
                await mermaid.parse(src);
                ok++;
            } catch (e) {
                bad++;
                console.log(`FAIL ${name}:${start}: ${String(e && e.message ? e.message : e).split("\n")[0]}`);
            }
            buf = null;
        } else if (buf !== null) {
            buf.push(line);
        }
    }
}
console.log(`parsed ok=${ok} bad=${bad}`);
process.exit(bad ? 1 : 0);
