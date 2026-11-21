// Structural bank repair. Fixes only genuine schema/structural violations so
// check-bank's contract gate passes. Never pads text or invents pedagogical
// content: missing stems, explanations, keys, hints or option text are hard
// errors reported with the question id for the content team to fix by hand.
// Idempotent. Usage: node scripts/repair-bank.mjs
import fs from "node:fs";
import path from "node:path";

const ROOT = "src/main/resources/bank";
const REQUIRED = ["id", "subject", "unit", "topic", "scope", "cognitiveDemand", "format",
  "stem", "answer", "explanation", "hint", "difficulty", "provenance", "version"];
const CONTENT_FIELDS = new Set(["stem", "explanation", "hint", "unit", "topic"]);
const BLOOMS = ["REMEMBER", "UNDERSTAND", "APPLY", "ANALYZE", "EVALUATE", "CREATE"];
const FORMAT_ALIASES = {
  "DATA_INTERPREPATION": "DATA_INTERPRETATION",
  "WRITTEN": "COMPOSITION",
  "MCQ": "CONCEPT_MCQ",
  "NUMERIC_CALCULATION": "NUMERIC",
  "ANALYZE": "CONCEPTUAL_REASONING",
};
const NEEDS_OPTIONS = new Set(["STIMULUS_MCQ", "CONCEPT_MCQ", "MULTI_SELECT", "ORDERING",
  "BUG_SPOTTING", "CODE_COMPLETION", "CODE_TRACING", "DIAGRAM", "IMAGE_INTERPRETATION"]);
const LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

let files = 0, questions = 0, fixed = 0;
const tally = {};
const fatal = [];
const bump = (k) => { tally[k] = (tally[k] || 0) + 1; };
const flag = (q, msg) => fatal.push(`${q && q.id ? q.id : "<no-id>"}: ${msg}`);

function repair(q) {
  let changed = false;
  const mark = () => { changed = true; };

  if (typeof q.format === "string" && FORMAT_ALIASES[q.format]) {
    q.format = FORMAT_ALIASES[q.format];
    mark();
    bump("formatAlias");
  }

  // Missing required content is a hard error - never synthesize a placeholder
  // that would pass check-bank's minLength rules and ship as real pedagogy.
  for (const f of REQUIRED) {
    if (q[f] === undefined || q[f] === null) {
      bump("missing:" + f);
      flag(q, `missing required field ${f}`);
    } else if (CONTENT_FIELDS.has(f) && typeof q[f] === "string" && q[f].trim().length === 0) {
      bump("blank:" + f);
      flag(q, `blank required field ${f}`);
    }
  }
  if (typeof q.stem !== "string") { flag(q, "stem is not text"); }
  if (typeof q.difficulty !== "number" || q.difficulty < 0 || q.difficulty > 1) {
    bump("difficultyRange");
    flag(q, "difficulty out of range");
  }
  const cd = q.cognitiveDemand && typeof q.cognitiveDemand === "object" ? q.cognitiveDemand : {};
  if (cd.bloom !== undefined && !BLOOMS.includes(cd.bloom)) { bump("cognitive"); flag(q, "bad bloom level"); }
  if (cd.dok !== undefined && ![1, 2, 3, 4].includes(cd.dok)) { bump("cognitive"); flag(q, "bad DOK"); }

  const p = q.provenance;
  if (p && typeof p === "object") {
    for (const f of ["author", "reviewer", "attestation"]) {
      if (typeof p[f] !== "string" || !p[f]) { bump("prov." + f); flag(q, `missing provenance.${f}`); }
    }
    if (typeof p.aiAssisted !== "boolean") { bump("prov.ai"); flag(q, "missing provenance.aiAssisted"); }
  }

  // Options: structural fixes only (non-object options, unique ids).
  const opts = Array.isArray(q.options) ? q.options : null;
  if (opts) {
    const used = new Set();
    opts.forEach((o, i) => {
      if (!o || typeof o !== "object") {
        flag(q, `option ${i} is not an object`);
        return;
      }
      const opt = o;
      if (typeof opt.id !== "string" || !opt.id || used.has(opt.id)) {
        let id = LETTERS[i] || "OPT" + i;
        let n = 1;
        while (used.has(id)) id = (LETTERS[i] || "OPT") + "_" + (++n);
        opt.id = id; mark(); bump("optId");
      }
      used.add(opt.id);
      for (const f of ["text", "misconception"]) {
        if (typeof opt[f] !== "string") { bump("opt." + f); flag(q, `option ${i} ${f} is not text`); }
      }
    });

    // Answer refs must point to existing option ids.
    const a = q.answer && typeof q.answer === "object" ? q.answer : {};
    if (typeof a.correctId === "string" && !used.has(a.correctId)) { delete a.correctId; mark(); bump("answerRef"); }
    if (Array.isArray(a.correctIds)) {
      const kept = a.correctIds.filter((c) => used.has(c));
      if (kept.length !== a.correctIds.length) { mark(); bump("answerRef"); }
      if (kept.length) a.correctIds = kept; else delete a.correctIds;
    }
    // Free-response questions sometimes carried dummy option arrays. Never
    // invent a correct answer: drop the dummy options and keep the exemplar.
    const hasFreeAnswer = typeof a.exemplar === "string" || typeof a.value === "number"
      || a.correctOrder !== undefined;
    if (hasFreeAnswer && opts.length && opts[0].text === "") {
      delete q.options; mark(); bump("noAnswerDropDummyOptions");
    }
    if (hasFreeAnswer && opts.length && a.correctId === opts[0].id && opts[0].text === "") {
      delete a.correctId; delete q.options; mark(); bump("noAnswerUnfabricate");
    }
    q.answer = a;
  }

  // Multi-option formats need at least two options; never fabricate their text.
  if (NEEDS_OPTIONS.has(q.format) && (!opts || opts.length < 2)) {
    bump("needOptions");
    flag(q, `${q.format} needs at least two options`);
  }
  return changed;
}

function visit(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name.startsWith(".")) continue;
    const f = path.join(dir, e.name);
    if (e.isDirectory()) visit(f);
    else if (e.name.endsWith(".json")) {
      let data;
      try { data = JSON.parse(fs.readFileSync(f, "utf8")); } catch { continue; }
      const list = Array.isArray(data) ? data : (data && Array.isArray(data.questions)) ? data.questions : [data];
      let changed = false;
      for (const q of list) {
        if (!q || typeof q !== "object") continue;
        questions++;
        if (repair(q)) { changed = true; fixed++; }
      }
      if (changed) { files++; fs.writeFileSync(f, JSON.stringify(data, null, 2) + "\n"); }
    }
  }
}
visit(ROOT);
console.log(JSON.stringify({ filesChanged: files, questions, questionsFixed: fixed, tally }, null, 2));
if (fatal.length) {
  console.error(`repair-bank: ${fatal.length} content issues need a human author (first 40):`);
  for (const line of fatal.slice(0, 40)) console.error("  " + line);
  if (fatal.length > 40) console.error(`  ... and ${fatal.length - 40} more`);
  process.exit(1);
}
