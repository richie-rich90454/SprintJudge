// Per-unit volume and coverage gate: every unit of every subject ships enough
// questions, with the formats and scopes the course framework calls for, and a
// difficulty spread that reaches across the whole range.
// Usage: node scripts/check-unit-volume.mjs [--strict] [path...]
import fs from "node:fs";
import path from "node:path";

const args = process.argv.slice(2);
const strict = args.includes("--strict");
const ROOTS = args.filter((a) => !a.startsWith("--"));
const BANK = "src/main/resources/bank";
const SCHEMA = "contracts/question-schema.json";

const MIN_PER_UNIT = 50;
const MIN_FORMATS = 4;
const SCOPES = new Set(["current-scope", "legacy-scope", "core-scope"]);

if (!fs.existsSync(SCHEMA)) {
  console.error("unit-volume: cannot read " + SCHEMA);
  process.exit(1);
}
const catalog = JSON.parse(fs.readFileSync(SCHEMA, "utf8")).properties.subject.enum;

const units = new Map();
const shipped = new Set();

function collect(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) collect(full, out);
    else if (e.name.endsWith(".json")) out.push(full);
  }
}

const files = [];
for (const root of ROOTS.length ? ROOTS : [BANK]) {
  const abs = path.resolve(root);
  if (!fs.existsSync(abs)) continue;
  if (fs.statSync(abs).isDirectory()) collect(abs, files);
  else files.push(abs);
}

let total = 0;
for (const file of files) {
  const dir = path.dirname(file);
  const isShipped = fs.existsSync(path.join(dir, ".shipped"));
  let data;
  try { data = JSON.parse(fs.readFileSync(file, "utf8")); }
  catch { console.error(`unit-volume: ${file}: invalid JSON`); process.exit(1); }
  const list = Array.isArray(data) ? data : Array.isArray(data.questions) ? data.questions : [data];
  for (const q of list) {
    if (typeof q?.subject !== "string" || typeof q?.unit !== "string") continue;
    const key = `${q.subject}|${q.unit}`;
    if (!units.has(key)) units.set(key, { total: 0, formats: new Set(), scopes: new Set(), diffs: [], subject: q.subject, unit: q.unit });
    const u = units.get(key);
    u.total++;
    u.formats.add(String(q.format));
    u.scopes.add(String(q.scope));
    if (typeof q.difficulty === "number") u.diffs.push(q.difficulty);
    if (isShipped) shipped.add(key);
    total++;
  }
}

let failed = false;
const short = [];
for (const [key, u] of [...units].sort()) {
  const problems = [];
  if (u.total < MIN_PER_UNIT) problems.push(`only ${u.total} questions`);
  if (u.formats.size < MIN_FORMATS) problems.push(`only ${u.formats.size} formats: ${[...u.formats].join(",")}`);
  const scopes = [...u.scopes];
  if (!scopes.some((s) => SCOPES.has(s))) problems.push(`bad scope: ${scopes.join(",")}`);
  if (u.diffs.length) {
    const min = Math.min(...u.diffs), max = Math.max(...u.diffs);
    if (min > 0.3) problems.push(`easiest item is ${min.toFixed(2)}, expected some near 0.2`);
    if (max < 0.8) problems.push(`hardest item is ${max.toFixed(2)}, expected some near 0.9`);
  }
  if (problems.length) {
    short.push(`${u.subject} ${u.unit}: ${problems.join("; ")}`);
    if (shipped.has(key) || strict) failed = true;
  }
}

const subjects = new Set([...units.values()].map((u) => u.subject));
const missing = catalog.filter((s) => !subjects.has(s));
for (const s of missing) {
  short.push(`${s}: no questions at all`);
  failed = true;
}

for (const line of short) console.log(`unit-volume: ${line}`);
console.log(`unit-volume: ${total} questions in ${units.size} units across ${subjects.size} subjects, ${short.length} below the floor`);
if (failed) {
  console.error("unit-volume: floor not met");
  process.exit(1);
}
console.log("unit-volume: floors met");
