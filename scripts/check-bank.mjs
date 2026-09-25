// Bank quality gate: schema, item-writing rules, uniqueness, legal scan.
// Accepts single-question files ({...}) and batch files ({"questions":[...]}).
// Usage: node scripts/check-bank.mjs [path...] (default: src/main/resources/bank)
import fs from "node:fs";
import path from "node:path";

const ROOTS = process.argv.slice(2).length > 0 ? process.argv.slice(2) : ["src/main/resources/bank"];
const SUBJECTS = new Set(["JAVA_PROGRAMMING", "COMPUTING_FOUNDATIONS", "CHEMISTRY", "CALCULUS_I",
  "CALCULUS_II", "PHYSICS_I", "PHYSICS_II", "PHYSICS_MECHANICS", "PHYSICS_EM", "EUROPEAN_HISTORY",
  "US_HISTORY", "WORLD_HISTORY", "MACROECONOMICS", "MICROECONOMICS", "STATISTICS", "US_GOVERNMENT",
  "COMPARATIVE_GOVERNMENT", "PSYCHOLOGY", "ENVIRONMENTAL_SCIENCE", "HUMAN_GEOGRAPHY", "ENGLISH_LANGUAGE",
  "ENGLISH_LITERATURE", "BIOLOGY", "SPANISH", "FRENCH", "GERMAN", "CHINESE", "ART_HISTORY",
  "MUSIC_THEORY", "PRECALCULUS", "RESEARCH_METHODS", "SEMINAR", "ALGEBRA_I", "ALGEBRA_II", "GEOMETRY",
  "INTEGRATED_1", "INTEGRATED_2", "INTEGRATED_3", "PYTHON", "WEB_DEVELOPMENT", "CYBERSECURITY", "DATA_SCIENCE"]);
const SCOPES = new Set(["current-scope", "legacy-scope", "core-scope"]);
const BLOOMS = new Set(["REMEMBER", "UNDERSTAND", "APPLY", "ANALYZE", "EVALUATE", "CREATE"]);
// ponytail: word-boundary brand scan only; lowercase prose never matches.
const BRAND = [/\bAdvanced Placement\b/, /\bPre-AP\b/, /AP Central/, /AP Vertical Teams/, /\bSpringBoard\b/, /\bPSAT\b/, /\bNMSQT\b/];
const AP_WORD = /\bAP\b/;

const errors = [];
const warnings = [];
const stems = new Map(); // normalized stem -> id
const ids = new Set();
const stats = new Map(); // subject -> {total, units:Set, formats:Set, scopes:Set}
let total = 0;

function collect(dir, out) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith(".")) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) collect(full, out);
    else if (entry.name.endsWith(".json")) out.push(full);
  }
}

function normStem(stem) {
  return stem.toLowerCase().replace(/[^a-z0-9\s]/g, " ").replace(/\s+/g, " ").trim();
}

function err(file, id, msg) {
  errors.push(`${file} [${id}]: ${msg}`);
}

function checkQuestion(file, q, index) {
  const id = q.id ?? `index-${index}`;
  const tag = `${file}#${id}`;
  if (typeof q.id !== "string" || !/^[a-z0-9]+-[0-9]+\.[0-9]+-[0-9]{4}$/.test(q.id)) err(file, id, "bad id shape");
  if (ids.has(q.id)) err(file, id, "duplicate id");
  else ids.add(q.id);
  if (!SUBJECTS.has(q.subject)) err(file, id, "unknown subject");
  if (typeof q.unit !== "string" || q.unit.length === 0) err(file, id, "missing unit");
  if (typeof q.topic !== "string" || q.topic.length === 0) err(file, id, "missing topic");
  if (!SCOPES.has(q.scope)) err(file, id, "bad scope");
  const cd = q.cognitiveDemand ?? {};
  if (!BLOOMS.has(cd.bloom) || ![1, 2, 3, 4].includes(cd.dok)) err(file, id, "bad cognitiveDemand");
  if (typeof q.format !== "string" || q.format.length === 0) err(file, id, "missing format");
  if (typeof q.stem !== "string" || q.stem.trim().length < 20) err(file, id, "stem too short or missing");
  if (typeof q.explanation !== "string" || q.explanation.trim().length < 20) err(file, id, "explanation too short");
  // Quality floor: an explanation must teach, not restate the answer.
  if (typeof q.explanation === "string" && q.explanation.trim().length < 60) {
    err(file, id, "explanation under 60 chars: must teach the why, not restate the answer");
  }
  if (typeof q.hint !== "string" || q.hint.trim().length === 0) err(file, id, "missing hint");
  const NEEDS_OPTIONS = new Set(["STIMULUS_MCQ", "CONCEPT_MCQ", "MULTI_SELECT", "ORDERING", "BUG_SPOTTING", "CODE_COMPLETION", "CODE_TRACING", "DIAGRAM", "IMAGE_INTERPRETATION"]);
  const CODE_FORMATS = new Set(["CODE_TRACING", "BUG_SPOTTING", "CODE_COMPLETION", "JUDGED_PROBLEM", "FILE_DATASET"]);
  const options = Array.isArray(q.options) ? q.options : [];
  // Quality floor: a code item without code in it is not a code item.
  if (CODE_FORMATS.has(q.format) && options.length > 0) {
    const looksLikeCode = /[;{}()]|=>|\bint\b|\bvoid\b|\bString\b|\bfor\b|\bif\b|\bnew\b|\breturn\b/.test(q.stem);
    if (!looksLikeCode) err(file, id, `${q.format} stem carries no code`);
  }
  if (NEEDS_OPTIONS.has(q.format) && options.length < 2) err(file, id, "need 2+ options");
  else if (options.length > 0) {
    const seen = new Set();
    for (const opt of q.options) {
      if (typeof opt.id !== "string" || typeof opt.text !== "string" || typeof opt.misconception !== "string") {
        err(file, id, "option missing id/text/misconception");
        break;
      }
      if (seen.has(opt.id)) err(file, id, "duplicate option id");
      seen.add(opt.id);
    }
    const correct = new Set();
    if (q.answer && typeof q.answer.correctId === "string") correct.add(q.answer.correctId);
    if (q.answer && Array.isArray(q.answer.correctIds)) for (const c of q.answer.correctIds) correct.add(c);
    for (const opt of options) {
      if (!correct.has(opt.id) && opt.misconception.trim().length === 0 && correct.size > 0) {
        err(file, id, `distractor ${opt.id} needs a named misconception`);
      }
      // Quality floor: "wrong" or "confuses order" is not a named misconception.
      if (!correct.has(opt.id) && correct.size > 0 && opt.misconception.trim().length < 12) {
        err(file, id, `distractor ${opt.id} misconception under 12 chars: name the specific wrong belief`);
      }
    }
    if (correct.size > 0) {
      for (const c of correct) if (!seen.has(c)) err(file, id, `answer ${c} not among options`);
    }
    // Quality floor: options must be grammatically parallel in length, so no
    // giveaway short/long option. Median-relative, lenient enough for numerals.
    if (options.length >= 3) {
      const lengths = options.map((o) => o.text.trim().length).sort((a, b) => a - b);
      const mid = lengths[Math.floor(lengths.length / 2)];
      if (mid > 0) {
        for (const o of options) {
          const ratio = o.text.trim().length / mid;
          if (ratio < 0.3 || ratio > 3.5) {
            err(file, id, `option ${o.id} length is ${ratio.toFixed(1)}x the median: options must be parallel`);
          }
        }
      }
    }
  }
  if (typeof q.difficulty !== "number" || q.difficulty < 0 || q.difficulty > 1) err(file, id, "difficulty must be 0..1");
  const p = q.provenance ?? {};
  if (typeof p.author !== "string" || p.author.length === 0) err(file, id, "provenance.author required");
  if (typeof p.reviewer !== "string" || p.reviewer.length === 0) err(file, id, "provenance.reviewer required");
  if (p.author === p.reviewer) err(file, id, "reviewer must differ from author");
  if (typeof p.attestation !== "string" || p.attestation.length === 0) err(file, id, "provenance.attestation required");
  if (typeof p.aiAssisted !== "boolean") err(file, id, "provenance.aiAssisted must be boolean");
  if (p.aiAssisted && (typeof p.humanReviewer !== "string" || p.humanReviewer.length === 0)) {
    err(file, id, "ai-assisted questions need provenance.humanReviewer");
  }
  if (q.stimulus && (typeof q.stimulus.license !== "string" || q.stimulus.license.length === 0)) {
    err(file, id, "stimulus needs a license");
  }
  const text = `${q.stem} ${q.explanation} ${(q.options ?? []).map((o) => o.text).join(" ")}`;
  for (const re of BRAND) if (re.test(text)) err(file, id, "branded program name in content");
  if (AP_WORD.test(text)) err(file, id, "standalone branded initialism in content");
  if (/(lorem ipsum|todo|fixme|placeholder|question \d+ about)/i.test(text)) err(file, id, "placeholder text");
  const key = normStem(q.stem);
  if (stems.has(key)) err(file, id, `duplicate stem of ${stems.get(key)}`);
  else stems.set(key, tag);
  const words = key.split(" ");
  if (words.length > 14) {
    const head = words.slice(0, 12).join(" ");
    const heads = (globalThis.__heads ??= new Map());
    if (heads.has(head)) warnings.push(`${tag}: near-duplicate opening of ${heads.get(head)}`);
    else heads.set(head, tag);
  }
  const s = stats.get(q.subject) ?? { total: 0, units: new Set(), formats: new Set(), scopes: new Set() };
  s.total += 1;
  s.units.add(q.unit);
  s.formats.add(q.format);
  s.scopes.add(q.scope);
  stats.set(q.subject, s);
  total += 1;
}

const files = [];
for (const root of ROOTS) {
  const abs = path.resolve(root);
  if (!fs.existsSync(abs)) continue;
  if (fs.statSync(abs).isDirectory()) collect(abs, files);
  else files.push(abs);
}
for (const file of files) {
  let data;
  try {
    data = JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    errors.push(`${file}: invalid JSON`);
    continue;
  }
  const list = Array.isArray(data) ? data : Array.isArray(data.questions) ? data.questions : [data];
  if (list.length === 0) errors.push(`${file}: no questions`);
  list.forEach((q, i) => checkQuestion(file, q, i));
}

console.log(`bank: ${total} questions in ${files.length} files`);
for (const [subject, s] of [...stats.entries()].sort()) {
  console.log(`bank: ${subject} total=${s.total} units=${s.units.size} formats=${[...s.formats].join(",")} scopes=${[...s.scopes].join(",")}`);
}
for (const w of warnings) console.log(`bank warn: ${w}`);
if (errors.length > 0) {
  for (const e of errors) console.error(`bank error: ${e}`);
  process.exit(1);
}
console.log("bank: clean");
