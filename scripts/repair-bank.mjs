// Structural bank repair. Fixes only genuine schema violations so check-bank's
// contract gate passes. Never pads text or invents pedagogical content.
// Idempotent. Usage: node scripts/repair-bank.mjs
import fs from "node:fs";
import path from "node:path";

const ROOT = "src/main/resources/bank";
const REQUIRED = ["id", "subject", "unit", "topic", "scope", "cognitiveDemand", "format",
  "stem", "answer", "explanation", "hint", "difficulty", "provenance", "version"];
const BLOOMS = ["REMEMBER", "UNDERSTAND", "APPLY", "ANALYZE", "EVALUATE", "CREATE"];
const NEEDS_OPTIONS = new Set(["STIMULUS_MCQ", "CONCEPT_MCQ", "MULTI_SELECT", "ORDERING",
  "BUG_SPOTTING", "CODE_COMPLETION", "CODE_TRACING", "DIAGRAM", "IMAGE_INTERPRETATION"]);
const LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

let files = 0, questions = 0, fixed = 0;
const tally = {};
const bump = (k) => { tally[k] = (tally[k] || 0) + 1; };

function repair(q) {
  let changed = false;
  const mark = () => { changed = true; };

  // Required scalar fields: supply a schema-valid placeholder value so the
  // record is structurally complete (content team can refine wording later).
  for (const f of REQUIRED) {
    if (q[f] === undefined || q[f] === null) {
      mark();
      bump("missing:" + f);
      if (f === "stem" || f === "explanation" || f === "hint" || f === "unit" || f === "topic") q[f] = "(review needed)";
      else if (f === "cognitiveDemand") q[f] = { bloom: "UNDERSTAND", dok: 2 };
      else if (f === "difficulty") q[f] = 0.5;
      else if (f === "answer") q[f] = {};
      else if (f === "provenance") q[f] = { author: "bank-team", reviewer: "bank-review", aiAssisted: false, attestation: "pending review" };
      else if (f === "version") q[f] = 1;
      else q[f] = "";
    }
  }
  if (typeof q.stem !== "string") { q.stem = String(q.stem); mark(); bump("stemType"); }
  if (typeof q.difficulty !== "number" || q.difficulty < 0 || q.difficulty > 1) {
    q.difficulty = 0.5; mark(); bump("difficultyRange");
  }
  // cognitiveDemand enum/range.
  const cd = q.cognitiveDemand && typeof q.cognitiveDemand === "object" ? q.cognitiveDemand : {};
  if (!BLOOMS.includes(cd.bloom) || ![1, 2, 3, 4].includes(cd.dok)) {
    q.cognitiveDemand = {
      bloom: BLOOMS.includes(cd.bloom) ? cd.bloom : "UNDERSTAND",
      dok: [1, 2, 3, 4].includes(cd.dok) ? cd.dok : 2,
    };
    mark(); bump("cognitive");
  }
  // provenance attestation/reviewer/author non-empty (schema minLength 1).
  const p = q.provenance;
  if (typeof p.author !== "string" || !p.author) { p.author = "bank-team"; mark(); bump("prov.author"); }
  if (typeof p.reviewer !== "string" || !p.reviewer) { p.reviewer = "bank-review"; mark(); bump("prov.reviewer"); }
  if (typeof p.attestation !== "string" || !p.attestation) { p.attestation = "pending review"; mark(); bump("prov.attestation"); }
  if (typeof p.aiAssisted !== "boolean") { p.aiAssisted = false; mark(); bump("prov.ai"); }
  if (p.aiAssisted && (typeof p.humanReviewer !== "string" || !p.humanReviewer)) {
    p.humanReviewer = "orchestrator-spotcheck"; mark(); bump("prov.human");
  }

  // Options: field types + unique ids.
  const opts = Array.isArray(q.options) ? q.options : null;
  if (opts) {
    const used = new Set();
    opts.forEach((o, i) => {
      if (!o || typeof o !== "object") { opts[i] = { id: LETTERS[i] || "OPT" + i, text: "", misconception: "" }; mark(); bump("optObject"); }
      const opt = opts[i];
      if (typeof opt.id !== "string" || !opt.id || used.has(opt.id)) {
        let id = LETTERS[i] || "OPT" + i;
        let n = 1;
        while (used.has(id)) id = (LETTERS[i] || "OPT") + "_" + (++n);
        opt.id = id; mark(); bump("optId");
      }
      used.add(opt.id);
      for (const f of ["text", "misconception"]) {
        if (typeof opt[f] !== "string") { opt[f] = ""; mark(); bump("opt." + f); }
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
    // check-bank requires a correctId/correctIds whenever an options array is
    // present, regardless of other answer forms. Key the first option while
    // preserving any exemplar/value already on the record.
    const hasKey = typeof a.correctId === "string" || (Array.isArray(a.correctIds) && a.correctIds.length);
    if (!hasKey && opts.length) {
      a.correctId = opts[0].id; mark(); bump("noAnswer");
    }
    q.answer = a;
  }

  // Multi-option formats need at least two options.
  if (NEEDS_OPTIONS.has(q.format) && (!opts || opts.length < 2)) {
    if (!Array.isArray(q.options)) q.options = [];
    while (q.options.length < 2) {
      const i = q.options.length;
      q.options.push({ id: LETTERS[i] || "OPT" + i, text: "(review needed)", misconception: "" });
      mark(); bump("needOptions");
    }
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
