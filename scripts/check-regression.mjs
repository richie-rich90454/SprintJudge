// One-command no-regression gate. Runs every gate by default, including
// builds, backend verify, E2E and the production health probe. Pass --fast to
// run only the static checks.
// Usage: node scripts/check-regression.mjs [--fast]
import { spawnSync } from "node:child_process";

const fast = process.argv.includes("--fast");
const steps = [
  ["bank schema + content", "node scripts/check-bank.mjs"],
  ["volume floors", "node scripts/check-volume.mjs"],
  ["unit floors", "node scripts/check-unit-volume.mjs"],
  ["seed sets", "node scripts/check-seed-sets.mjs"],
  ["legal gates", "make check-legal"],
  ["UI rules", "make check-ui"],
  ["frontend typecheck + tests", "make test-frontend"],
];
if (!fast) {
  steps.push(["frontend build", "make build-frontend"]);
  steps.push(["backend verify", "make verify"]);
  // seed-banks POSTs to a running backend on :8080; it is not standalone.
  // Run it manually with `make dev-backend` up when validating new subjects.
  steps.push(["E2E", "make test-e2e"]);
  steps.push(["production probe", "make package && make verify-prod"]);
}

let failed = 0;
for (const [name, command] of steps) {
  console.log(`\n== ${name} ==`);
  const result = spawnSync(command, { shell: true, stdio: "inherit" });
  if (result.status !== 0) {
    console.error(`REGRESSION FAILED: ${name}`);
    failed += 1;
  }
}
if (failed) {
  console.error(`\n${failed} regression gate(s) failed`);
  process.exit(1);
}
console.log("\nregression: clean");
