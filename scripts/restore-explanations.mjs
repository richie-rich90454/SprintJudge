// One-off repair: restore questions whose explanation key was dropped by a
// bad field mapping, using the committed version as the source. Only fills in
// questions that currently have no explanation; all other edits are preserved.
// Usage: node scripts/restore-explanations.mjs <subject-dir> [...]
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const dirs = process.argv.slice(2);
let restored = 0;
let files = 0;
for (const dir of dirs) {
  const root = path.join("src/main/resources/bank", dir);
  if (!fs.existsSync(root)) continue;
  const walk = (d, out) => {
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) walk(p, out);
      else if (e.name.endsWith(".json")) out.push(p);
    }
  };
  const list = [];
  walk(root, list);
  for (const file of list) {
    const current = JSON.parse(fs.readFileSync(file, "utf8"));
    const questions = Array.isArray(current) ? current : current.questions;
    if (!questions) continue;
    if (!questions.some((q) => q.explanation === undefined)) continue;
    let old;
    try {
      old = JSON.parse(execFileSync("git", ["show", `HEAD:${file.replace(/\\/g, "/")}`], { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 }));
    } catch {
      continue;
    }
    const oldQuestions = Array.isArray(old) ? old : old.questions;
    const byId = new Map((oldQuestions ?? []).map((q) => [q.id, q]));
    let dirty = false;
    for (const q of questions) {
      if (q.explanation !== undefined) continue;
      const original = byId.get(q.id);
      if (original && typeof original.explanation === "string") {
        q.explanation = original.explanation;
        restored += 1;
        dirty = true;
      }
    }
    if (dirty) {
      files += 1;
      fs.writeFileSync(file, JSON.stringify(current, null, 2) + "\n");
    }
  }
}
console.log(`restore-explanations: ${restored} questions in ${files} files`);
