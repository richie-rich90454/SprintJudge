// Marks a bank directory as shipped only when every subject it contains meets
// the per-course floor and every unit it contains meets the per-unit rules.
// This keeps .shipped markers consistent with both check-volume.mjs and
// check-unit-volume.mjs, which bind floors on directories that carry one.
// Idempotent. Usage: node scripts/mark-shipped.mjs [floor]
import fs from "node:fs";
import path from "node:path";

const FLOOR = Number(process.argv[2] || 4000);
const MIN_PER_UNIT = 50;
const MIN_FORMATS = 4;
const SCOPES = new Set(["current-scope", "legacy-scope", "core-scope"]);
const BANK = "src/main/resources/bank";
const SCHEMA = "contracts/question-schema.json";
const catalog = JSON.parse(fs.readFileSync(SCHEMA, "utf8")).properties.subject.enum;
const known = new Set(catalog);

const unitStats = new Map(); // key -> {total, formats:Set, scopes:Set, diffs:[], dirs:Set}
const dirSubjects = new Map(); // dir -> Set<subject>
const dirSubjectCounts = new Map(); // dir -> Map<subject, count in THIS dir>
const dirUnits = new Map(); // dir -> Set<unitKey>

function collect(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) collect(full, out);
    else if (e.name.endsWith(".json")) out.push(full);
  }
}

const files = [];
collect(BANK, files);
for (const file of files) {
  const dir = path.dirname(file);
  let data;
  try { data = JSON.parse(fs.readFileSync(file, "utf8")); } catch { continue; }
  const list = Array.isArray(data) ? data : Array.isArray(data.questions) ? data.questions : [data];
  for (const q of list) {
    if (typeof q?.subject !== "string" || !known.has(q.subject)) continue;
    if (!dirSubjects.has(dir)) dirSubjects.set(dir, new Set());
    dirSubjects.get(dir).add(q.subject);
    if (!dirSubjectCounts.has(dir)) dirSubjectCounts.set(dir, new Map());
    const perDir = dirSubjectCounts.get(dir);
    perDir.set(q.subject, (perDir.get(q.subject) || 0) + 1);
    if (typeof q?.unit !== "string") continue;
    const key = `${q.subject}|${q.unit}`;
    if (!unitStats.has(key)) unitStats.set(key, { total: 0, formats: new Set(), scopes: new Set(), diffs: [], dirs: new Set() });
    const u = unitStats.get(key);
    u.total++;
    u.formats.add(String(q.format));
    u.scopes.add(String(q.scope));
    if (typeof q.difficulty === "number") u.diffs.push(q.difficulty);
    u.dirs.add(dir);
    if (!dirUnits.has(dir)) dirUnits.set(dir, new Set());
    dirUnits.get(dir).add(key);
  }
}

function unitOk(key) {
  const u = unitStats.get(key);
  if (!u || u.total < MIN_PER_UNIT || u.formats.size < MIN_FORMATS) return false;
  if (![...u.scopes].some((s) => SCOPES.has(s))) return false;
  if (u.diffs.length) {
    const min = Math.min(...u.diffs);
    const max = Math.max(...u.diffs);
    if (min > 0.3 || max < 0.8) return false;
  }
  return true;
}

let written = 0, removed = 0;
const dirs = new Set([...dirSubjects.keys()]);
for (const dir of dirs) {
  const marker = path.join(dir, ".shipped");
  const subjects = [...dirSubjects.get(dir)];
  const units = [...(dirUnits.get(dir) || [])];
  // Bind the floor to what THIS directory holds: check-volume marks a subject
  // shipped if any directory carrying it has a marker, so a stray single
  // question must never vouch for a subject's global volume.
  const perDir = dirSubjectCounts.get(dir) || new Map();
  const ok = subjects.every((s) => (perDir.get(s) || 0) >= FLOOR) && units.every(unitOk);
  if (ok) {
    const body = `subjects: ${subjects.join(",")}\nunits: ${units.length}\nfloor: ${FLOOR}\nshipped: ${new Date().toISOString().slice(0, 10)}\n`;
    if (!fs.existsSync(marker) || fs.readFileSync(marker, "utf8") !== body) {
      fs.writeFileSync(marker, body);
      written++;
      console.log(`shipped: ${path.relative(BANK, dir)} (${subjects.join(",")})`);
    }
  } else if (fs.existsSync(marker)) {
    fs.unlinkSync(marker);
    removed++;
    console.log(`unshipped: ${path.relative(BANK, dir)}`);
  }
}
console.log(JSON.stringify({ written, removed }));
