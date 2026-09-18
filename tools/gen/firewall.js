// Firewall for the bank generation pipeline.
// The generator may read only human-authored blueprints and may write
// only machine drafts. Anything else is refused at runtime.
const fs = require("node:fs");
const path = require("node:path");

const REPO_ROOT = path.resolve(__dirname, "..", "..");
const ALLOWED_READ_ROOT = path.join(REPO_ROOT, "blueprints");
const ALLOWED_WRITE_ROOT = path.join(REPO_ROOT, "drafts");
const FORBIDDEN_READ = ["src/main/resources/bank", "docs", "seed", "data"];

function assertReadable(file) {
  const abs = path.resolve(REPO_ROOT, file);
  const inside = abs === ALLOWED_READ_ROOT || abs.startsWith(ALLOWED_READ_ROOT + path.sep);
  if (!inside) {
    throw new Error("firewall: refusing to read outside blueprints/**: " + file);
  }
  for (const denied of FORBIDDEN_READ) {
    if (abs.startsWith(path.join(REPO_ROOT, denied) + path.sep)) {
      throw new Error("firewall: refusing protected path: " + file);
    }
  }
  return abs;
}

function assertWritable(file) {
  const abs = path.resolve(REPO_ROOT, file);
  const inside = abs === ALLOWED_WRITE_ROOT || abs.startsWith(ALLOWED_WRITE_ROOT + path.sep);
  if (!inside) {
    throw new Error("firewall: refusing to write outside drafts/**: " + file);
  }
  return abs;
}

module.exports = { REPO_ROOT, assertReadable, assertWritable };
