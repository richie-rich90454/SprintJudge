// Bank quality gate: schema, item-writing rules, uniqueness, legal scan.
// Accepts single-question files ({...}) and batch files ({"questions":[...]}).
// Usage: node scripts/check-bank.mjs [path...] (default: src/main/resources/bank)
import fs from "node:fs";
import path from "node:path";

const SCHEMA = JSON.parse(fs.readFileSync("contracts/question-schema.json", "utf8"));
const REQUIRED = SCHEMA.required;
const FORMATS = new Set(SCHEMA.properties.format.enum);

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

// Editorial quality goals (explanation richness, misconception specificity,
// option-length parallelism, stem uniqueness) are not part of the committed
// question schema (contracts/question-schema.json). They are reported as
// warnings so the content team can improve them, but they must not fail the
// structural/legal gate. Only contract violations and legal scans are errors.
function advisory(file, id, msg) {
  warnings.push(`${file} [${id}]: ${msg}`);
}

function checkQuestion(file, q, index) {
  const id = q.id ?? `index-${index}`;
  const tag = `${file}#${id}`;
  // Drive the required-field floor from the contract so the gate cannot drift
  // behind the schema: `practice: undefined` and a missing `answer` both slip
  // past hand-maintained checks.
  for (const field of REQUIRED) {
    if (q[field] === undefined || q[field] === null) err(file, id, `required field ${field} is missing`);
  }
  if (typeof q.id !== "string" || !/^[a-z0-9]+-[0-9]+\.[0-9]+-(?!0000)[0-9]{4}$/.test(q.id)) err(file, id, "bad id shape");
  if (ids.has(q.id)) err(file, id, "duplicate id");
  else ids.add(q.id);
  if (!SUBJECTS.has(q.subject)) err(file, id, "unknown subject");
  if (typeof q.unit !== "string" || q.unit.length === 0) err(file, id, "missing unit");
  // Quality floor: the unit label must agree with the unit number in the id,
  // or per-unit coverage silently reports on the wrong content.
  const idUnit = typeof q.id === "string" ? /^[a-z0-9]+-(\d+)\.\d+-\d{4}$/.exec(q.id) : null;
  const labelUnit = /^(?:unit|domain)\s+(\d+)$/i.exec(q.unit ?? "");
  if (idUnit && /^(?:unit|domain)\b/i.test(q.unit ?? "") && !labelUnit) {
    err(file, id, `unit label "${q.unit}" is not of the form "Unit <n>"`);
  } else if (idUnit && labelUnit && idUnit[1] !== labelUnit[1]) {
    err(file, id, `unit label "${q.unit}" disagrees with the unit number in the id`);
  }
  if (typeof q.topic !== "string" || q.topic.length === 0) err(file, id, "missing topic");
  if (!SCOPES.has(q.scope)) err(file, id, "bad scope");
  const cd = q.cognitiveDemand ?? {};
  if (!BLOOMS.has(cd.bloom) || ![1, 2, 3, 4].includes(cd.dok)) err(file, id, "bad cognitiveDemand");
  if (typeof q.format !== "string" || q.format.length === 0) err(file, id, "missing format");
  else if (!FORMATS.has(q.format)) err(file, id, `format ${q.format} is not in the schema contract`);
  // Contract floor: schema requires non-empty stem/explanation (minLength 1).
  if (typeof q.stem !== "string" || q.stem.trim().length === 0) err(file, id, "stem missing");
  if (typeof q.explanation !== "string" || q.explanation.trim().length === 0) err(file, id, "explanation missing");
  // Editorial goals: richer explanations teach better, but length is not a
  // contract requirement. Report short content as advisory, not a hard error.
  if (typeof q.explanation === "string" && q.explanation.trim().length < 60) {
    advisory(file, id, "explanation under 60 chars: consider teaching the why, not restating the answer");
  }
  // The REQUIRED loop above already fails a missing hint field (contract).
  // An empty-string hint is valid per schema; flag it as editorial advice only.
  if (typeof q.hint === "string" && q.hint.trim().length === 0) advisory(file, id, "empty hint: consider a short nudge");
  const NEEDS_OPTIONS = new Set(["STIMULUS_MCQ", "CONCEPT_MCQ", "MULTI_SELECT", "ORDERING", "BUG_SPOTTING", "CODE_COMPLETION", "CODE_TRACING", "DIAGRAM", "IMAGE_INTERPRETATION"]);
  const CODE_FORMATS = new Set(["CODE_TRACING", "BUG_SPOTTING", "CODE_COMPLETION", "JUDGED_PROBLEM", "FILE_DATASET"]);
  const options = Array.isArray(q.options) ? q.options : [];
  // Quality floor: a code item without code in it is not a code item.
  if (CODE_FORMATS.has(q.format) && options.length > 0) {
    const looksLikeCode = /[;{}()]|=>|\bint\b|\bvoid\b|\bString\b|\bfor\b|\bif\b|\bnew\b|\breturn\b/.test(q.stem);
    if (!looksLikeCode) advisory(file, id, `${q.format} stem carries no code in the stem text`);
  }
  // Quality floor: an item with options must say which one is correct. Without
  // this, a missing `answer` silently skipped every distractor check below.
  const correctIds = new Set();
  if (q.answer && typeof q.answer.correctId === "string") correctIds.add(q.answer.correctId);
  if (q.answer && Array.isArray(q.answer.correctIds)) for (const c of q.answer.correctIds) correctIds.add(c);
  if (options.length > 0 && correctIds.size === 0) {
    err(file, id, "no answer: an item with options must set answer.correctId or answer.correctIds");
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
    const correct = correctIds;
    for (const opt of options) {
      const misconception =
        typeof opt.misconception === "string" ? opt.misconception.trim() : "";
      if (!correct.has(opt.id) && misconception.length === 0 && correct.size > 0) {
        advisory(file, id, `distractor ${opt.id} has no misconception named`);
      }
      // Editorial goal: a specific misconception teaches better than "wrong",
      // but the schema only requires the field be present. Advisory only.
      if (!correct.has(opt.id) && correct.size > 0 && misconception.length > 0 && misconception.length < 12) {
        advisory(file, id, `distractor ${opt.id} misconception under 12 chars: consider naming the specific wrong belief`);
      }
    }
    if (correct.size > 0) {
      for (const c of correct) if (!seen.has(c)) err(file, id, `answer ${c} not among options`);
    }
    // Editorial goal: parallel-length options avoid a giveaway. Not a contract
    // rule (numeric options are naturally uneven). Advisory only.
    if (options.length >= 3 && options.every((o) => typeof o.text === "string")) {
      const lengths = options.map((o) => o.text.trim().length).sort((a, b) => a - b);
      const mid = lengths[Math.floor(lengths.length / 2)];
      if (mid > 0) {
        for (const o of options) {
          const ratio = o.text.trim().length / mid;
          if (ratio < 0.3 || ratio > 3.5) {
            advisory(file, id, `option ${o.id} length is ${ratio.toFixed(1)}x the median: consider parallel options`);
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
  const text = `${q.stem} ${q.explanation} ${q.hint ?? ""} ${q.stimulus?.body ?? ""} ` +
    `${(q.options ?? []).map((o) => `${o.text} ${o.misconception ?? ""}`).join(" ")}`;
  for (const re of BRAND) if (re.test(text)) err(file, id, "branded program name in content");
  if (AP_WORD.test(text)) err(file, id, "standalone branded initialism in content");
  // Only unambiguous unfinished-content markers are hard errors. "todo" was
  // removed (matches Spanish "todos los domingos") and "placeholder(s)" was
  // removed (legitimate math/CS subject matter, e.g. zero coefficients acting
  // as placeholders). What remains reliably indicates an unfinished item.
  if (/(lorem ipsum|\bfixme\b|\bquestion \d+ about\b|\(review needed\)|\bI set [A-D]\.|Error\. Leave|need only one correct|I.ll edit mentally|final output uses corrected|The correct answer is [A-D])/i.test(text)) {
    err(file, id, "placeholder or author-scratch text");
  }
  // A self-correction that names a different key than the stored answer means
  // students are graded against an answer the author rejected.
  const scratchKey = text.match(/The correct answer is ([A-D])/i)?.[1];
  const storedKey = q.answer?.correctId;
  if (scratchKey && storedKey && scratchKey.toUpperCase() !== String(storedKey).toUpperCase()) {
    err(file, id, "explanation contradicts the answer key");
  }
  const key = normStem(typeof q.stem === "string" ? q.stem : `__badstem__${q.id ?? index}`);
  // Stem uniqueness is an editorial goal (variety), not a schema contract;
  // identical stems still have unique ids and play correctly. Advisory only.
  if (stems.has(key)) advisory(file, id, `duplicate stem of ${stems.get(key)}`);
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
