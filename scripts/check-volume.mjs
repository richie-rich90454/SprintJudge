// Volume floor check: every catalog subject needs 3000+ real question records.
// Counts records by each record's own `subject` field, never by file or folder
// name, so directory slugs cannot drift from the schema. The catalog comes from
// contracts/question-schema.json, so a new subject cannot be skipped by forgetting
// to create its folder. Subjects with zero questions are reported as short.
// Exits non-zero when a subject holding a `.shipped` marker is below the floor,
// or when any subject is short and `--strict` is passed.
// Usage: node scripts/check-volume.mjs [--strict] [path...]
import fs from "node:fs";
import path from "node:path";

const args = process.argv.slice(2);
const strict = args.includes("--strict") || process.env.VOLUME_STRICT === "1";
const ROOTS = args.filter((a) => !a.startsWith("--"));
const BANK = "src/main/resources/bank";
const SCHEMA = "contracts/question-schema.json";
const FLOOR = 3000;

if (!fs.existsSync(SCHEMA)) {
  console.error(`volume: cannot read ${SCHEMA}`);
  process.exit(1);
}
const catalog = JSON.parse(fs.readFileSync(SCHEMA, "utf8")).properties.subject.enum;
const counts = new Map(catalog.map((s) => [s, 0]));
const shippedSubjects = new Set();

function collect(dir, out) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) collect(full, out);
    else if (entry.name.endsWith(".json")) out.push(full);
  }
}

let total = 0;
const unknown = new Set();
for (const root of ROOTS.length > 0 ? ROOTS : [BANK]) {
  const abs = path.resolve(root);
  if (!fs.existsSync(abs)) continue;
  const files = [];
  if (fs.statSync(abs).isDirectory()) collect(abs, files);
  else files.push(abs);
  const shippedHere = path.dirname(files[0] ?? abs) === abs && fs.existsSync(path.join(abs, ".shipped"));
  for (const file of files) {
    let data;
    try {
      data = JSON.parse(fs.readFileSync(file, "utf8"));
    } catch {
      console.error(`volume: ${file}: invalid JSON`);
      process.exit(1);
    }
    const list = Array.isArray(data) ? data : Array.isArray(data.questions) ? data.questions : [data];
    for (const q of list) {
      if (typeof q?.subject !== "string") continue;
      total += 1;
      if (counts.has(q.subject)) {
        counts.set(q.subject, counts.get(q.subject) + 1);
        if (shippedHere) shippedSubjects.add(q.subject);
      } else {
        unknown.add(`${q.subject} in ${file}`);
      }
    }
  }
}

let failed = unknown.size > 0;
for (const [subject, count] of counts) {
  const deficit = FLOOR - count;
  if (deficit <= 0) {
    console.log(`volume: ${subject} ${count}/${FLOOR} ok`);
    continue;
  }
  console.log(`volume: ${subject} ${count}/${FLOOR} short ${deficit}`);
  if (shippedSubjects.has(subject) || strict) {
    console.error(`volume: ${subject} has ${count}, floor is ${FLOOR}`);
    failed = true;
  }
}
for (const u of unknown) console.error(`volume: subject not in schema catalog: ${u}`);

const short = [...counts.values()].filter((c) => c < FLOOR).length;
console.log(`volume: ${total} questions across ${counts.size} catalog subjects, ${short} below the ${FLOOR} floor`);
if (failed) {
  console.error("volume: floor not met");
  process.exit(1);
}
console.log("volume: floors met");
