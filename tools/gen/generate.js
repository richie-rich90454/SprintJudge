// Blueprint-driven draft writer (offline, no network, no browsing).
// Reads attested human blueprints under blueprints/**, validates them, and
// writes schema-shaped draft shells into seed/drafts/** for a human to
// complete and review. No question text is invented here.
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { assertReadable, assertWritable, REPO_ROOT } = require("./firewall");
const { parse, validate, buildDraftShell } = require("./blueprint");

function listBlueprints(dir) {
  const abs = assertReadable(dir);
  const out = [];
  for (const entry of fs.readdirSync(abs, { withFileTypes: true })) {
    if (entry.name.startsWith(".")) continue;
    const rel = path.relative(REPO_ROOT, path.join(abs, entry.name)).replace(/\\/g, "/");
    if (entry.isDirectory()) out.push(...listBlueprints(rel));
    else if (/\.(yaml|yml|json)$/i.test(entry.name)) out.push(rel);
  }
  return out;
}

function loadBlueprint(rel) {
  const abs = assertReadable(rel);
  const raw = fs.readFileSync(abs, "utf8");
  const bp = /\.json$/i.test(rel) ? JSON.parse(raw) : parse(raw);
  return validate(bp);
}

function logRead(rel) {
  const logPath = path.join(REPO_ROOT, "seed", "drafts", ".reads.log");
  fs.mkdirSync(path.dirname(logPath), { recursive: true });
  fs.appendFileSync(logPath, rel + "\n");
}

function generate(blueprints, draftDir) {
  // Validate every input first: one bad attestation must not leave a partial batch.
  const jobs = blueprints.map((rel) => {
    const bp = loadBlueprint(rel);
    const draftRel = (draftDir ?? "seed/drafts") + "/" + String(bp.id).replace(/^bp-/, "") + ".draft.json";
    return { rel, draftPath: assertWritable(draftRel), shell: buildDraftShell(bp) };
  });
  for (const job of jobs) {
    fs.mkdirSync(path.dirname(job.draftPath), { recursive: true });
    fs.writeFileSync(job.draftPath, JSON.stringify(job.shell, null, 2) + "\n");
    logRead(job.rel);
    console.log("draft shell written to " + path.relative(REPO_ROOT, job.draftPath).replace(/\\/g, "/"));
  }
  return jobs.length;
}

function selfCheck() {
  const raw = [
    "id: bp-jp-1.1-0001", "subject: JAVA_PROGRAMMING", "unit: Unit 1",
    "topic: Primitive Types", "concept: declare and initialize int variables before use",
    "bloom: APPLY", "dok: 1", "format: CONCEPT_MCQ", "author: j.doe",
    "authoredAt: 2026-09-18T00:00:00Z", "attestedFromFirstPrinciples: true",
  ].join("\n");
  const shell = buildDraftShell(parse(raw));
  assert.equal(shell.id, "jp-1.1-0001");
  assert.equal(shell.status, "draft-shell");
  assert.equal(shell.provenance.author, "j.doe");
  assert.equal(shell.stem, "");
  assert.throws(() => validate({ ...parse(raw), attestedFromFirstPrinciples: "false" }));
  assert.throws(() => validate({ ...parse(raw), bloom: "UNKNOWN" }));
  assert.throws(() => validate({ ...parse(raw), unit: "Unit 2" }));
  assert.throws(() => assertReadable("src/main/resources/bank/x.json"));
  assert.throws(() => assertWritable("blueprints/jp/bp-jp-1.1-0001.yaml"));
  console.log("generator self-check: ok");
}

function main() {
  const [first, second] = process.argv.slice(2);
  if (first === "--self-check") return selfCheck();
  if (first === "--all") {
    const drafts = second ?? "seed/drafts";
    assertWritable(drafts);
    const count = generate(listBlueprints("blueprints"), drafts);
    if (!count) throw new Error("no blueprints found");
    return;
  }
  if (!first || !second) {
    console.error("usage: node tools/gen/generate.js <blueprint> <draft>");
    console.error("       node tools/gen/generate.js --all [draft-dir]");
    console.error("       node tools/gen/generate.js --self-check");
    process.exit(2);
  }
  const bp = loadBlueprint(first);
  const target = assertWritable(second);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, JSON.stringify(buildDraftShell(bp), null, 2) + "\n");
  logRead(first);
  console.log("draft shell written to " + second);
}

if (require.main === module) {
  try {
    main();
  } catch (error) {
    console.error("generator: " + error.message);
    process.exit(1);
  }
}

module.exports = { generate, loadBlueprint, selfCheck };
