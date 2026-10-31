// Blueprint-driven draft writer (offline, no network, no browsing).
// Reads one blueprint yaml/json inside blueprints/**, validates the
// first-principles attestation, and writes a schema-shaped draft shell
// into drafts/** for a human to complete and review. No question text
// is invented here; the draft holds structure plus author placeholders.
const fs = require("node:fs");
const path = require("node:path");
const { assertReadable, assertWritable, REPO_ROOT } = require("./firewall");

function main() {
  const [blueprintRel, draftRel] = process.argv.slice(2);
  if (!blueprintRel || !draftRel) {
    console.error("usage: node tools/gen/generate.js <blueprint> <draft>");
    process.exit(2);
  }
  const blueprintPath = assertReadable(blueprintRel);
  const draftPath = assertWritable(draftRel);
  const raw = fs.readFileSync(blueprintPath, "utf8");
  if (!raw.includes("attestedFromFirstPrinciples: true")) {
    throw new Error("blueprint missing attestedFromFirstPrinciples: true");
  }
  const idMatch = raw.match(/id:\s*(\S+)/);
  const draft = {
    fromBlueprint: idMatch ? idMatch[1] : path.basename(blueprintPath),
    status: "draft-shell",
    note: "human author completes stem, options, explanation, and provenance",
    createdAt: new Date().toISOString(),
  };
  fs.mkdirSync(path.dirname(draftPath), { recursive: true });
  fs.writeFileSync(draftPath, JSON.stringify(draft, null, 2));
  const logPath = path.join(REPO_ROOT, "drafts", ".reads.log");
  fs.mkdirSync(path.dirname(logPath), { recursive: true });
  fs.appendFileSync(logPath, blueprintRel + "\n");
  console.log("draft shell written to " + draftRel);
}

if (require.main === module) {
  main();
}
