// Convert bank records whose format disagrees with their answer shape into a
// playable format. Additive-only: the original format is preserved as a tag and
// version is bumped; no record is deleted. Idempotent.
// Usage: node scripts/repair-bank-playability.mjs [path...]
import fs from "node:fs";
import path from "node:path";

const ROOTS = process.argv.slice(2).length > 0 ? process.argv.slice(2) : ["src/main/resources/bank"];
const MCQ = new Set(["CONCEPT_MCQ", "STIMULUS_MCQ", "SOURCE_BASED", "ERROR_ANALYSIS",
  "DATA_INTERPRETATION", "CONCEPTUAL_REASONING", "RHETORICAL_ANALYSIS", "SYNTHESIS", "DIAGRAM",
  "CODE_TRACING", "BUG_SPOTTING", "CODE_COMPLETION", "MCQ", "TWO_STAGE", "ORDERING", "DATA_INTERPREPATION"]);
const NUMERIC = new Set(["NUMERIC", "FORMULA_APPLICATION", "CALCULATION_WITH_UNITS",
  "NUMERIC_CALCULATION", "FILE_DATASET", "JUDGED_PROBLEM"]);
const FREE = new Set(["EXPLANATION", "EXPERIMENT_DESIGN", "PROOF", "FREE_RESPONSE",
  "IMAGE_INTERPRETATION", "LISTENING_RESPONSE", "COMPOSITION", "WRITTEN"]);

function collect(dir, out) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith(".")) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) collect(full, out);
    else if (entry.name.endsWith(".json")) out.push(full);
  }
}

let files = 0;
let changed = 0;
const byChange = new Map();
for (const root of ROOTS) {
  const list = [];
  if (fs.existsSync(root)) {
    if (fs.statSync(root).isDirectory()) collect(root, list);
    else list.push(root);
  }
  for (const file of list) {
    let data;
    try {
      data = JSON.parse(fs.readFileSync(file, "utf8"));
    } catch {
      continue;
    }
    const questions = Array.isArray(data) ? data : Array.isArray(data.questions) ? data.questions : null;
    if (!questions) continue;
    let dirty = false;
    for (const q of questions) {
      const a = q.answer ?? {};
      const opts = Array.isArray(q.options) ? q.options : [];
      const hasCorrectId = typeof a.correctId === "string" && opts.some((o) => o.id === a.correctId);
      const hasCorrectIds = Array.isArray(a.correctIds) && a.correctIds.length >= 2 &&
        a.correctIds.every((c) => opts.some((o) => o.id === c));
      const hasValue = typeof a.value === "number" || typeof a.answer === "number";
      const hasExemplar = typeof a.exemplar === "string" && a.exemplar.trim().length > 0;
      // Some quantitative records store the keyed result in `v` and a derived
      // rate in `t`. Promote v to the runtime's `value` without discarding t/v.
      if (!hasValue && typeof a.v === "number" && NUMERIC.has(q.format)) {
        a.value = a.v;
        changed += 1;
        dirty = true;
        const k = `${q.format} -> value from v`;
        byChange.set(k, (byChange.get(k) ?? 0) + 1);
      }
      // A multi-select with exactly one keyed option is a single-answer item.
      if (q.format === "MULTI_SELECT" && Array.isArray(a.correctIds) && a.correctIds.length === 1 &&
          opts.some((o) => o.id === a.correctIds[0])) {
        a.correctId = a.correctIds[0];
        delete a.correctIds;
        q.format = "CONCEPT_MCQ";
        changed += 1;
        dirty = true;
        const k = "MULTI_SELECT -> CONCEPT_MCQ (single key)";
        byChange.set(k, (byChange.get(k) ?? 0) + 1);
        continue;
      }
      let target = null;
      if (MCQ.has(q.format) && !hasCorrectId) {
        if (q.format === "MULTI_SELECT" && hasCorrectIds) continue;
        if (hasValue) target = "NUMERIC";
        else if (hasExemplar) target = "EXPLANATION";
        else if (opts.length >= 2 && a.correctId) target = "CONCEPT_MCQ";
      } else if (q.format === "MULTI_SELECT" && !hasCorrectIds) {
        if (hasCorrectId) target = "CONCEPT_MCQ";
        else if (hasValue) target = "NUMERIC";
      } else if (NUMERIC.has(q.format) && !hasValue) {
        if (hasCorrectId) target = q.stimulus ? "STIMULUS_MCQ" : "CONCEPT_MCQ";
        else if (hasExemplar) target = "EXPLANATION";
      } else if (FREE.has(q.format) && !hasExemplar) {
        if (hasCorrectId) target = q.stimulus ? "STIMULUS_MCQ" : "CONCEPT_MCQ";
        else if (hasValue) target = "NUMERIC";
      }
      if (!target || target === q.format) continue;
      const old = q.format;
      q.format = target;
      q.tags = Array.isArray(q.tags) ? q.tags : [];
      const tag = `converted-from-${old.toLowerCase()}`;
      if (!q.tags.includes(tag)) q.tags.push(tag);
      q.version = Number.isInteger(q.version) ? q.version + 1 : 2;
      changed += 1;
      dirty = true;
      const k = `${old} -> ${target}`;
      byChange.set(k, (byChange.get(k) ?? 0) + 1);
    }
    if (dirty) {
      files += 1;
      fs.writeFileSync(file, JSON.stringify(data, null, 2) + "\n");
    }
  }
}
console.log(`playability repair: ${changed} records in ${files} files`);
for (const [k, v] of [...byChange.entries()].sort((a, b) => b[1] - a[1])) console.log(`  ${k}: ${v}`);
