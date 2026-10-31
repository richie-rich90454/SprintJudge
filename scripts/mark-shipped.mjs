// Marks a subject directory as shipped once it meets the per-course floor, so
// check-volume treats that subject's floor as binding.
// Usage: node scripts/mark-shipped.mjs [floor]
import fs from "node:fs";
import path from "node:path";

const FLOOR = Number(process.argv[2] || 4000);
const BANK = "src/main/resources/bank";
const SCHEMA = "contracts/question-schema.json";
const catalog = JSON.parse(fs.readFileSync(SCHEMA, "utf8")).properties.subject.enum;

const counts = new Map(catalog.map((s) => [s, 0]));
for (const dir of fs.readdirSync(BANK, { withFileTypes: true })) {
  if (!dir.isDirectory()) continue;
  const d = path.join(BANK, dir.name);
  let total = 0;
  for (const f of fs.readdirSync(d)) {
    if (!f.endsWith(".json")) continue;
    let data;
    try { data = JSON.parse(fs.readFileSync(path.join(d, f), "utf8")); } catch { continue; }
    const list = Array.isArray(data) ? data : data.questions || [data];
    for (const q of list) if (typeof q?.subject === "string") counts.set(q.subject, (counts.get(q.subject) || 0) + 1);
  }
  for (const [s, n] of counts) {
    if (n >= FLOOR && !fs.existsSync(path.join(d, ".shipped"))) {
      fs.writeFileSync(path.join(d, ".shipped"), `subject: ${s}\nquestions: ${n}\nfloor: ${FLOOR}\nshipped: ${new Date().toISOString().slice(0, 10)}\n`);
      console.log(`shipped: ${s} (${n})`);
    }
  }
}
for (const [s, n] of counts) if (n < FLOOR) console.log(`not shipped: ${s} (${n}/${FLOOR})`);
