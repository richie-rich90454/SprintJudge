// UI consistency gate: typography, copy punctuation, tokens, and resets.
// Scope: frontend source only (not tests, not the legacy answer palette).
import fs from "node:fs";
import path from "node:path";

const ROOT = "frontend/src";
const ALLOW_HEX = new Set([
  path.join(ROOT, "ui", "kahoot.ts"),
  path.join(ROOT, "index.css"),
  // xterm requires concrete colors; these mirror the stage tokens.
  path.join(ROOT, "services", "renderers", "OjBase.ts"),
]);

const files = [];
(function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p);
    else if (/\.(ts|tsx|css)$/.test(e.name) && !/\.test\./.test(e.name)) files.push(p);
  }
})(ROOT);

const errors = [];
const add = (file, line, rule, text) =>
  errors.push(`${file}:${line} [${rule}] ${text.trim().slice(0, 140)}`);

function stripComments(line) {
  const trimmed = line.trim();
  if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) return "";
  return line.replace(/\/\*.*?\*\//g, "").replace(/\/\/.*$/, "");
}

for (const file of files) {
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  lines.forEach((raw, index) => {
    const line = stripComments(raw);
    if (!line.trim()) return;
    const n = index + 1;

    if (/!important/.test(line)) add(file, n, "important", line);
    if (/text-transform:\s*uppercase/.test(line) || /\buppercase\b/.test(line)) {
      add(file, n, "uppercase", line);
    }
    if (line.includes("\u00b7")) add(file, n, "middle-dot", line);
    if (line.includes("\u2014")) add(file, n, "em-dash", line);
    if (!ALLOW_HEX.has(file) && /#[0-9a-fA-F]{3,8}\b/.test(line)) {
      add(file, n, "raw-hex", line);
    }

    const font = /font-family:\s*([^;}]+)/.exec(line);
    if (font) {
      const value = font[1].toLowerCase();
      const allowed = value
        .split(",")
        .map((part) => part.trim())
        .every(
          (part) =>
            part.startsWith("var(--font") ||
            part === "inherit" ||
            part === "sans-serif" ||
            part === "monospace" ||
            part === '"noto sans"' ||
            part === '"noto sans mono"',
        );
      if (!allowed) add(file, n, "font-family", line);
    }
  });
}

if (errors.length) {
  console.error(`check-ui: ${errors.length} violation(s)`);
  for (const error of errors) console.error("  " + error);
  process.exit(1);
}
console.log(`check-ui: ${files.length} files clean`);
