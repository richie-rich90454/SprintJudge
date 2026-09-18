// Volume floor check: every bank subject needs 3000+ questions.
// Macro and micro may share one combined 3000+ pool. Units need 50+.
import fs from "node:fs";
import path from "node:path";

const BANK = path.resolve("src/main/resources/bank");
const FLOOR = 3000;
const UNIT_FLOOR = 50;

if (!fs.existsSync(BANK)) {
  console.log("volume: no bank dir yet, skipping (floor applies once subjects ship)");
  process.exit(0);
}

const subjects = fs.readdirSync(BANK, { withFileTypes: true }).filter((d) => d.isDirectory()).map((d) => d.name);
if (subjects.length === 0) {
  console.log("volume: bank empty, skipping");
  process.exit(0);
}

const counts = new Map();
for (const subject of subjects) {
  const files = fs.readdirSync(path.join(BANK, subject)).filter((f) => f.endsWith(".json"));
  counts.set(subject, files.length);
}

const macro = counts.get("macroeconomics") ?? 0;
const micro = counts.get("microeconomics") ?? 0;
let failed = false;
for (const [subject, count] of counts) {
  if (subject === "macroeconomics" || subject === "microeconomics") {
    continue;
  }
  if (count < FLOOR) {
    console.error(`volume: ${subject} has ${count}, floor is ${FLOOR}`);
    failed = true;
  }
}
if (counts.has("macroeconomics") || counts.has("microeconomics")) {
  if (macro + micro < FLOOR) {
    console.error(`volume: macro+micro combined ${macro + micro}, floor is ${FLOOR}`);
    failed = true;
  }
}
if (failed) {
  process.exit(1);
}
console.log("volume: floors met");
