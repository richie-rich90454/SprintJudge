// Seeds a few playable quiz sets per subject from the bank so every catalog
// subject appears in the public library, not just the coding subjects.
// Usage: node scripts/seed-playable-banks.mjs [baseUrl] [setsPerSubject]
// Requires SPRINTJUDGE_ADMIN_PASSWORD (or SPRINTJUDGE_ADMIN_USERNAME/PASSWORD).
import fs from "node:fs";

const BASE = process.argv[2] ?? "http://127.0.0.1:8080";
const SETS = Number(process.argv[3] ?? 2);
const USER = process.env.SPRINTJUDGE_ADMIN_USERNAME ?? "admin";
const PASS = process.env.SPRINTJUDGE_ADMIN_PASSWORD ?? (process.env.CI === "true" ? "ci-admin-password" : "");
if (!PASS) {
    console.error("seed: set SPRINTJUDGE_ADMIN_PASSWORD before running outside CI");
    process.exit(1);
}

const schema = JSON.parse(fs.readFileSync("contracts/question-schema.json", "utf8"));
const subjects = schema.properties.subject.enum;
const cookies = new Map();

function absorb(response) {
    for (const raw of response.headers.getSetCookie()) {
        const [pair] = raw.split(";");
        const at = pair.indexOf("=");
        if (at > 0) cookies.set(pair.slice(0, at), pair.slice(at + 1));
    }
}

function cookieHeader() {
    return Array.from(cookies, ([k, v]) => `${k}=${v}`).join("; ");
}

async function call(path, options = {}) {
    const headers = { Cookie: cookieHeader(), ...(options.headers ?? {}) };
    const response = await fetch(BASE + path, { ...options, headers, redirect: "manual" });
    absorb(response);
    return response;
}

const loginPage = await call("/admin/login");
const xsrf = cookies.get("XSRF-TOKEN") ?? "";
const login = await call("/admin/login", {
    method: "POST",
    headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "X-XSRF-TOKEN": xsrf,
    },
    body: new URLSearchParams({ username: USER, password: PASS }).toString(),
});
if (login.status !== 302) {
    console.error(`seed: login failed with ${login.status}`);
    process.exit(1);
}

// Spring clears the CSRF cookie on login; the browser's redirect GET issues a
// fresh one. Do the same here before any mutating call.
await call("/admin/dashboard");
const token = cookies.get("XSRF-TOKEN") ?? "";
let built = 0;
let failed = 0;
for (const subject of subjects) {
    for (let i = 1; i <= SETS; i++) {
        const response = await call("/api/admin/bank/build", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": token,
            },
            body: JSON.stringify({ subject, count: 12, title: `${subject} practice ${i}` }),
        });
        if (response.ok) {
            built++;
        } else {
            failed++;
            console.error(`seed: ${subject} set ${i} failed with ${response.status}`);
        }
    }
}
console.log(`seed: ${built} quiz sets built across ${subjects.length} subjects, ${failed} failed`);
if (failed > 0) process.exitCode = 1;
