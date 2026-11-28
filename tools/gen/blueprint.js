// Pure blueprint parsing/validation. No file access here: the CLI layer owns
// every read so the firewall stays the single entry point.
const REQUIRED = [
  "id", "subject", "unit", "topic", "concept", "bloom", "dok",
  "format", "author", "authoredAt", "attestedFromFirstPrinciples",
];
const OPTIONAL = new Set([
  "scope", "misconceptionToTarget", "stimulusType", "stimulusLicense",
  "learningObjective", "practice", "tags",
]);
const BLOOMS = new Set(["REMEMBER", "UNDERSTAND", "APPLY", "ANALYZE", "EVALUATE", "CREATE"]);
const SCOPES = new Set(["current-scope", "legacy-scope", "core-scope"]);
const FORMATS = new Set([
  "STIMULUS_MCQ", "CONCEPT_MCQ", "MULTI_SELECT", "NUMERIC", "ORDERING",
  "BUG_SPOTTING", "CODE_COMPLETION", "JUDGED_PROBLEM", "FREE_RESPONSE",
  "TWO_STAGE", "DATA_INTERPRETATION", "SOURCE_BASED", "DIAGRAM",
  "CALCULATION_WITH_UNITS", "PROOF", "EXPERIMENT_DESIGN", "ERROR_ANALYSIS",
  "EXPLANATION", "CODE_TRACING", "FILE_DATASET", "IMAGE_INTERPRETATION",
  "LISTENING_RESPONSE", "COMPOSITION", "SYNTHESIS", "RHETORICAL_ANALYSIS",
  "FORMULA_APPLICATION", "CONCEPTUAL_REASONING",
]);

function parse(raw) {
  const out = {};
  for (const line of raw.split(/\r?\n/)) {
    const text = line.trim();
    if (!text || text.startsWith("#")) continue;
    const at = text.indexOf(":");
    if (at < 1) throw new Error("bad blueprint line: " + line);
    const key = text.slice(0, at).trim();
    let value = text.slice(at + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (Object.hasOwn(out, key)) throw new Error("duplicate blueprint key: " + key);
    out[key] = value;
  }
  return out;
}

function validate(bp) {
  for (const key of REQUIRED) {
    if (bp[key] === undefined || bp[key] === null || bp[key] === "") {
      throw new Error("blueprint missing required field: " + key);
    }
  }
  for (const key of Object.keys(bp)) {
    if (!REQUIRED.includes(key) && !OPTIONAL.has(key)) throw new Error("unknown blueprint field: " + key);
  }
  if (bp.attestedFromFirstPrinciples !== true && bp.attestedFromFirstPrinciples !== "true") {
    throw new Error("blueprint missing attestedFromFirstPrinciples: true");
  }
  const id = String(bp.id);
  if (!/^bp-[a-z0-9]+-[0-9]+\.[0-9]+-[0-9]{4}$/.test(id)) throw new Error("bad blueprint id: " + id);
  if (!/^[A-Z][A-Z0-9_]*$/.test(String(bp.subject))) throw new Error("bad subject: " + bp.subject);
  const unitMatch = /^(?:unit|domain)\s+(\d+)$/i.exec(String(bp.unit));
  const idUnit = /^bp-[a-z0-9]+-(\d+)\.\d+-\d{4}$/.exec(id);
  if (!unitMatch) throw new Error('unit must be of the form "Unit <n>": ' + bp.unit);
  if (idUnit && idUnit[1] !== unitMatch[1]) throw new Error("unit label disagrees with blueprint id");
  if (!BLOOMS.has(String(bp.bloom))) throw new Error("unknown bloom: " + bp.bloom);
  const dok = Number(bp.dok);
  if (!Number.isInteger(dok) || dok < 1 || dok > 4) throw new Error("dok must be 1..4");
  if (!FORMATS.has(String(bp.format))) throw new Error("unknown format: " + bp.format);
  if (bp.scope !== undefined && !SCOPES.has(String(bp.scope))) throw new Error("unknown scope: " + bp.scope);
  if (Number.isNaN(Date.parse(String(bp.authoredAt)))) throw new Error("bad authoredAt date: " + bp.authoredAt);
  return bp;
}

function buildDraftShell(bp) {
  validate(bp);
  const questionId = String(bp.id).replace(/^bp-/, "");
  return {
    fromBlueprint: bp.id,
    status: "draft-shell",
    note: "human author completes stem, options, explanation, and provenance",
    createdAt: new Date().toISOString(),
    id: questionId,
    subject: bp.subject,
    unit: bp.unit,
    topic: bp.topic,
    scope: bp.scope ?? "current-scope",
    cognitiveDemand: { bloom: bp.bloom, dok: Number(bp.dok) },
    format: bp.format,
    ...(bp.learningObjective ? { learningObjective: bp.learningObjective } : {}),
    ...(bp.misconceptionToTarget ? { misconceptionToTarget: bp.misconceptionToTarget } : {}),
    ...(bp.stimulusType ? { suggestedStimulusType: bp.stimulusType } : {}),
    stem: "",
    options: [],
    answer: {},
    explanation: "",
    hint: "",
    difficulty: 0.5,
    provenance: {
      author: bp.author,
      reviewer: "",
      reviewedAt: "",
      aiAssisted: false,
      stimulusLicense: bp.stimulusLicense ?? null,
      attestation: "human-authored from first principles; pending independent review",
    },
    version: 1,
    todo: ["stem", "options/answer", "explanation", "hint", "provenance.reviewer", "provenance.reviewedAt"],
  };
}

module.exports = { parse, validate, buildDraftShell };
