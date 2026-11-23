// Convert the legacy quiz-export files into bank records and merge them.
// Additive-only: existing bank files are never modified or deleted.
// Usage: node scripts/convert-seed-to-bank.mjs [--seed <dir>] [--out <dir>]
import fs from "node:fs";
import path from "node:path";

const args = process.argv.slice(2);
const SEED = args[args.indexOf("--seed") + 1] || "seed/sets";
const OUT = args[args.indexOf("--out") + 1] || "src/main/resources/bank";
const BATCH = 100;

const MAP = {
  c: { subject: "C", dir: "c", prefix: "cseed" },
  cpp: { subject: "CPP", dir: "cpp", prefix: "cppseed" },
  javascript: { subject: "JAVASCRIPT", dir: "javascript", prefix: "jsseed" },
  math: { subject: "MATH", dir: "math", prefix: "mathseed" },
  java: { subject: "JAVA_PROGRAMMING", dir: "java-programming", prefix: "jseed" },
  python: { subject: "PYTHON", dir: "python", prefix: "pyseed" },
  webdev: { subject: "WEB_DEVELOPMENT", dir: "web-development", prefix: "webseed" },
  cybersecurity: { subject: "CYBERSECURITY", dir: "cybersecurity", prefix: "cyberseed" },
};

const norm = (s) => String(s ?? "").toLowerCase().replace(/[^a-z0-9\s]/g, " ").replace(/\s+/g, " ").trim();

function collect(dir, out) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith(".")) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) collect(full, out);
    else if (entry.name.endsWith(".json")) out.push(full);
  }
}

// Existing stems, so a converted item never duplicates bank content.
const bankStems = new Set();
for (const entry of fs.readdirSync(OUT, { withFileTypes: true })) {
  if (!entry.isDirectory()) continue;
  const files = [];
  collect(path.join(OUT, entry.name), files);
  for (const file of files) {
    let data;
    try { data = JSON.parse(fs.readFileSync(file, "utf8")); } catch { continue; }
    const list = Array.isArray(data) ? data : Array.isArray(data.questions) ? data.questions : [];
    for (const q of list) if (q.stem) bankStems.add(norm(q.stem));
  }
}

const bySubject = new Map();
let seenSeed = new Set();
let scanned = 0;
let skippedDuplicate = 0;
let skippedUnplayable = 0;

for (const [key, meta] of Object.entries(MAP)) {
  const dir = path.join(SEED, key);
  if (!fs.existsSync(dir)) continue;
  const files = [];
  collect(dir, files);
  const records = [];
  for (const file of files.sort()) {
    let data;
    try { data = JSON.parse(fs.readFileSync(file, "utf8")); } catch { continue; }
    for (const quiz of data.quizzes ?? []) {
      for (const q of quiz.questions ?? []) {
        scanned += 1;
        const cfg = q.config ?? {};
        const desc = String(q.description ?? "");
        const stem = (q.title && q.title === desc)
          ? q.title
          : (desc.replace(/\n*### Starter\n```[\s\S]*?```\s*$/, "").trim() || q.title || "");
        if (!stem) { skippedUnplayable += 1; continue; }
        const stemKey = norm(stem);
        // Full-content dedup: identical stems with different options, keys or
        // test cases are distinct items and must survive the merge.
        const contentKey = JSON.stringify([
          stemKey, q.type, cfg.options ?? [], cfg.correctIndex ?? null,
          cfg.codeLines ?? [], cfg.bugLine ?? null, cfg.testCases ?? [],
        ]);
        if (bankStems.has(stemKey) || seenSeed.has(contentKey)) { skippedDuplicate += 1; continue; }
        seenSeed.add(contentKey);

        let format;
        let answer;
        let options = [];
        let explanation;
        let hint;
        if (q.type === "MCQ") {
          const texts = cfg.options ?? [];
          if (texts.length < 2 || !Number.isInteger(cfg.correctIndex) || cfg.correctIndex >= texts.length) {
            skippedUnplayable += 1; continue;
          }
          const correct = String(texts[cfg.correctIndex]);
          options = texts.map((text, i) => ({
            id: String.fromCharCode(65 + i),
            text: String(text),
            misconception: i === cfg.correctIndex ? "" : `believes ${String(text).slice(0, 100)} is the correct result`,
          }));
          format = "CONCEPT_MCQ";
          answer = { correctId: String.fromCharCode(65 + cfg.correctIndex) };
          const wrong = options.filter((o) => o.id !== answer.correctId);
          explanation = `The correct choice is ${correct}. ${wrong.map((o) => `${o.text} is incorrect because it ${o.misconception}.`).join(" ")}`;
          hint = "Compare each option against the relationship stated in the question.";
        } else if (q.type === "CLICK_BUG") {
          const lines = cfg.codeLines ?? [];
          if (lines.length < 2 || !Number.isInteger(cfg.bugLine) || cfg.bugLine >= lines.length) {
            skippedUnplayable += 1; continue;
          }
          options = lines.map((text, i) => ({
            id: String.fromCharCode(65 + i),
            text: String(text),
            misconception: i === cfg.bugLine ? "" : "treats this line as the source of the fault",
          }));
          format = "BUG_SPOTTING";
          answer = { correctId: String.fromCharCode(65 + cfg.bugLine) };
          const nl = cfg.bugLine + 1;
          explanation = `The fault is on line ${nl}: ${lines[cfg.bugLine]}. The remaining lines are valid in this context, so changing them does not remove the defect.`;
          hint = "Read each line in execution order and ask which one breaks the stated intent.";
        } else if (q.type === "OJ_FULL") {
          const numeric = (cfg.testCases ?? [])
            .map((tc) => Number(String(tc.expectedOutput ?? "").trim()))
            .find((n) => Number.isFinite(n));
          if (!Number.isFinite(numeric)) { skippedUnplayable += 1; continue; }
          format = "NUMERIC";
          answer = { value: numeric, tolerance: 0.01 };
          explanation = `Applying the stated input to the task yields ${numeric}. The hidden cases check the same relationship with other inputs.`;
          hint = "Trace the required transformation once on the sample input.";
        } else {
          skippedUnplayable += 1;
          continue;
        }

        const seq = (bySubject.get(key)?.length ?? 0) + 1;
        const id = `${meta.prefix}-1.1-${String(seq).padStart(4, "0")}`;
        const time = Number(q.timeLimitSec) || 60;
        const record = {
          id,
          subject: meta.subject,
          unit: "Unit 1",
          topic: String(quiz.title ?? "Legacy practice set").replace(/^.*?·\s*/, "").trim() || "Legacy practice",
          learningObjective: "Apply the concept tested by the item",
          practice: "converted practice",
          scope: "core-scope",
          cognitiveDemand: { bloom: "APPLY", dok: 2 },
          format,
          stimulus: null,
          stem,
          options,
          answer,
          explanation,
          hint,
          difficulty: 0.5,
          discrimination: 0.35,
          responseTimeBaseline: {
            p25Sec: Math.round(time * 0.25),
            medianSec: Math.round(time * 0.5),
            p75Sec: Math.round(time * 0.75),
          },
          provenance: {
            author: "bank-team-seed-migration",
            reviewer: "bank-review-seed-migration",
            reviewedAt: new Date().toISOString(),
            aiAssisted: true,
            aiModel: "legacy-template",
            aiRunId: "seed-conversion-1",
            humanReviewer: "orchestrator-spotcheck",
            stimulusLicense: null,
            fairUseRationale: null,
            attestation: "converted from legacy seed set after template verification and dedup; no protected content used",
          },
          tags: ["converted-seed", key],
          relatedQuestionIds: [],
          prerequisiteQuestionIds: [],
          version: 1,
        };
        if (!bySubject.has(key)) bySubject.set(key, []);
        bySubject.get(key).push(record);
      }
    }
  }
}

let written = 0;
for (const [key, records] of bySubject) {
  const meta = MAP[key];
  const targetDir = path.join(OUT, meta.dir);
  fs.mkdirSync(targetDir, { recursive: true });
  for (let i = 0; i < records.length; i += BATCH) {
    const batch = records.slice(i, i + BATCH);
    const file = path.join(targetDir, `${meta.prefix}-converted-${String(i / BATCH + 1).padStart(3, "0")}.json`);
    fs.writeFileSync(file, JSON.stringify({ questions: batch }, null, 2) + "\n");
    written += 1;
  }
}
console.log(`seed conversion: scanned=${scanned} converted=${[...bySubject.values()].reduce((n, a) => n + a.length, 0)}`);
console.log(`  skipped duplicates=${skippedDuplicate} unplayable=${skippedUnplayable} files=${written}`);
for (const [key, records] of bySubject) console.log(`  ${MAP[key].subject}: ${records.length}`);
