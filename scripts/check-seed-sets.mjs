// Legacy quiz-export quality gate. The seed files are archived after the merge,
// but this script keeps them verifiable while the conversion is in flight.
// Usage: node scripts/check-seed-sets.mjs [path...]
import fs from "node:fs";
import path from "node:path";

const ROOTS = process.argv.slice(2).length > 0
  ? process.argv.slice(2)
  : ["seed/sets", "archive/seed-sets"].filter((p) => fs.existsSync(p));

const errors = [];
let files = 0;
let questions = 0;

function collect(dir, out) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith(".")) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) collect(full, out);
    else if (entry.name.endsWith(".json")) out.push(full);
  }
}

// Verified template defects fixed in Phase 4; any reappearance is a regression.
const BAD_LINES = [
  ["if (flag == 0)", "bug line is a valid comparison, not a bug"],
  ["const double = x => x * 2;", "arrow has an implicit return"],
  ["if (value == null) {", "nullish check is idiomatic, not a bug"],
  ["const first = arr.slice(0, arr.length);", "full-array copy is correct"],
];
const BAD_KEYS = [
  ["'pyypy'", "string multiplication key is wrong"],
];

for (const root of ROOTS) {
  if (!fs.existsSync(root)) continue;
  const list = [];
  collect(root, list);
  for (const file of list) {
    files += 1;
    let data;
    try {
      data = JSON.parse(fs.readFileSync(file, "utf8"));
    } catch {
      errors.push(`${file}: invalid JSON`);
      continue;
    }
    for (const quiz of data.quizzes ?? []) {
      for (const q of quiz.questions ?? []) {
        questions += 1;
        const id = q.id ?? "index";
        const cfg = q.config ?? {};
        if (!q.title || !q.description) errors.push(`${file} [${id}]: missing title/description`);
        if (q.type === "MCQ") {
          const opts = cfg.options ?? [];
          if (opts.length < 2) errors.push(`${file} [${id}]: need 2+ options`);
          if (!Number.isInteger(cfg.correctIndex) || cfg.correctIndex < 0 || cfg.correctIndex >= opts.length) {
            errors.push(`${file} [${id}]: bad correctIndex`);
          }
          const seen = new Set();
          for (const o of opts) {
            const t = String(o).trim().toLowerCase();
            if (seen.has(t)) errors.push(`${file} [${id}]: duplicate option text`);
            seen.add(t);
          }
          for (const [needle, why] of BAD_KEYS) {
            if (opts.some((o) => String(o).includes(needle))) errors.push(`${file} [${id}]: ${why}`);
          }
        } else if (q.type === "CLICK_BUG") {
          const lines = cfg.codeLines ?? [];
          if (!Number.isInteger(cfg.bugLine) || cfg.bugLine < 0 || cfg.bugLine >= lines.length) {
            errors.push(`${file} [${id}]: bad bugLine`);
          }
          if (lines.some((line) => line === "]")) errors.push(`${file} [${id}]: Java block closed with ']'`);
          for (const [needle, why] of BAD_LINES) {
            if (lines.some((line) => line.includes(needle))) errors.push(`${file} [${id}]: ${why}`);
          }
        } else if (q.type === "OJ_FULL") {
          if (!Array.isArray(cfg.testCases) || cfg.testCases.length === 0) {
            errors.push(`${file} [${id}]: no test cases`);
          }
          for (const tc of cfg.testCases ?? []) {
            if (typeof tc.input !== "string" || typeof tc.expectedOutput !== "string") {
              errors.push(`${file} [${id}]: malformed test case`);
            }
            const input = (tc.input ?? "").trim();
            if ((input === "17" || input === "83") && tc.expectedOutput === "not prime") {
              errors.push(`${file} [${id}]: prime miskeyed as not prime`);
            }
          }
        } else {
          errors.push(`${file} [${id}]: unknown question type ${q.type}`);
        }
      }
    }
  }
}

console.log(`seed-sets: ${questions} questions in ${files} files across ${ROOTS.join(", ") || "no roots"}`);
for (const e of errors) console.error(`seed-sets error: ${e}`);
if (errors.length) process.exit(1);
console.log("seed-sets: clean");
