import { test, expect, type Page, type Request } from "@playwright/test";
import { readFileSync } from "node:fs";

// The PWA service worker claims the page and reloads on controllerchange,
// cancelling in-flight form POSTs before Spring Security sees them.
test.use({ serviceWorkers: "block" });

/**
 * Exploratory end-to-end sweep of the admin journey against the CI-equivalent
 * backend (prod profile, seeded 82-course bank, ci-admin-password).
 *
 * Form login is served by Spring Security, so the whole journey stays on the
 * backend origin (the jar serves the same SPA the Vite build produces).
 *
 * Gaps in the current implementation are asserted at the API layer from inside
 * the browser rather than papered over:
 *  - the bank detail UI is answer-free; options/explanation/misconception are
 *    readable only in the admin-only config of a bank-built quiz;
 *  - bank build ignores a `format` field, so format is verified by pairing the
 *    filtered headers with the generated questions;
 *  - assignments expose a public metadata URL but no student HTML launch page
 *    and no answer-submission endpoint; the student answer is submitted through
 *    the real public practice game, and the assignment view is verified through
 *    roster/start/grade progress;
 *  - the dashboard's Import control hardcodes replace=true (wiping the bank),
 *    so the round-trip uses the same admin endpoint with replace=false.
 */
const B = process.env.SPRINTJUDGE_BACKEND_URL ?? "http://127.0.0.1:8080";
const PASSWORD = process.env.SPRINTJUDGE_ADMIN_PASSWORD ?? "ci-admin-password";
const LABELS: Record<string, string> = {
    ACCOUNTING: "Accounting",
    ANALYTICAL_CHEMISTRY: "Analytical Chemistry",
    BIOLOGY: "Biology",
    JAVA_PROGRAMMING: "Java Programming",
    US_HISTORY: "United States History",
};

interface BankHeader {
    id: string;
    subject: string;
    unit: string;
    topic: string;
    scope: string;
    format: string;
    stem: string;
}

interface QuestionConfig {
    unit?: string;
    explanation?: string;
    options?: string[];
    optionMeta?: Array<{ id: string; misconception: string }>;
}

interface QuizQuestion {
    id: string;
    title: string;
    description: string;
    questionType: string;
    timeLimitSec?: number;
    pointsBase?: number;
    // The admin questions endpoint returns the stored config verbatim as a
    // JSON string; the export endpoint parses it into an object.
    config: string;
    orderIndex: number;
}

interface ExportedQuestion {
    id: string;
    title: string;
    description: string;
    type: string;
    timeLimitSec?: number;
    pointsBase?: number;
    config: QuestionConfig;
}

function parsedConfig(raw: string | QuestionConfig): QuestionConfig {
    return typeof raw === "string" ? (JSON.parse(raw) as QuestionConfig) : raw;
}

interface ApiResult {
    status: number;
    body: unknown;
}

/** Same-origin fetch from the authenticated page, echoing the raw XSRF cookie. */
async function call(page: Page, path: string, method = "GET", body?: unknown): Promise<ApiResult> {
    return page.evaluate(
        async ({ path, method, body }) => {
            const token =
                document.cookie
                    .split("; ")
                    .find((cookie) => cookie.startsWith("XSRF-TOKEN="))
                    ?.split("=")[1] ?? "";
            const response = await fetch(path, {
                method,
                credentials: "include",
                headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": token },
                body: body === undefined ? undefined : JSON.stringify(body),
            });
            const text = await response.text();
            let parsed: unknown = text;
            try {
                parsed = JSON.parse(text);
            } catch {
                // Non-JSON bodies (e.g. an empty 204) stay as text.
            }
            return { status: response.status, body: parsed };
        },
        { path, method, body },
    );
}

function ok(result: ApiResult, what: string): any {
    expect(
        result.status,
        `${what}: HTTP ${result.status} ${JSON.stringify(result.body).slice(0, 300)}`,
    ).toBeLessThan(300);
    return result.body;
}

async function login(page: Page): Promise<void> {
    await page.goto(`${B}/admin/login`);
    await page.locator('input[name="username"]').fill("admin");
    await page.locator('input[name="password"]').fill(PASSWORD);
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 15_000 });
    await expect(page.getByText("Start a CSA class")).toBeVisible();
}

const firstLine = (stem: string) => stem.split("\n")[0].trim();

test.describe("exploratory admin journey", () => {
    test("bank -> build -> assignment -> student answer -> analytics -> export/import -> settings", async ({
        page,
        browser,
    }) => {
        test.setTimeout(180_000);
        const stamp = Date.now();
        let subject = "";
        let unit = "";
        let format = "";

        await test.step("login and browse the 82-course catalog", async () => {
            await login(page);
            await page.goto(`${B}/admin/bank`);
            await expect(
                page.getByRole("heading", { name: "Browse the question bank" }),
            ).toBeVisible();

            const volume = ok(await call(page, "/api/admin/bank/volume"), "bank volume") as Record<
                string,
                number
            >;
            expect(Object.keys(volume)).toHaveLength(82);
            expect(Object.values(volume).every((count) => count >= 4_000)).toBeTruthy();

            // Find a unit whose whole catalog is one playable format. That lets
            // the format search filter and the (format-agnostic) build endpoint
            // be cross-checked against the same question set.
            for (const code of Object.keys(LABELS)) {
                const coverage = ok(
                    await call(page, `/api/admin/bank/coverage/${code}`),
                    `coverage ${code}`,
                ) as Record<string, { total: number; formats: string[] }>;
                const pure = Object.keys(coverage).find(
                    (u) =>
                        coverage[u].formats.length === 1 &&
                        coverage[u].formats[0] === "CONCEPT_MCQ" &&
                        coverage[u].total >= 3,
                );
                if (pure) {
                    subject = code;
                    unit = pure;
                    format = coverage[pure].formats[0];
                    break;
                }
            }
            expect(subject && unit && format).toBeTruthy();

            await page.locator("#bank-subject").selectOption(LABELS[subject]);
            await page.locator("#bank-scope").selectOption("everything");
            await expect(
                page.getByText(new RegExp(`${subject}: ${volume[subject]} questions across`)),
            ).toBeVisible({ timeout: 10_000 });

            await page.locator("#bank-unit").fill(unit);
            const headers = ok(
                await call(
                    page,
                    `/api/admin/bank/search?subject=${subject}&unit=${encodeURIComponent(unit)}&scope=everything`,
                ),
                "bank search",
            ) as BankHeader[];
            expect(headers.length).toBeGreaterThan(1);
            await expect(page.getByText(new RegExp(`^${headers.length} questions?$`))).toBeVisible({
                timeout: 10_000,
            });
            await expect(page.locator("ul li button").first()).toContainText(unit);

            const formatted = ok(
                await call(
                    page,
                    `/api/admin/bank/search?subject=${subject}&unit=${encodeURIComponent(unit)}&scope=everything&format=${format}`,
                ),
                "format-filtered bank search",
            ) as BankHeader[];
            expect(formatted.length).toBeGreaterThan(0);
            expect(formatted.length).toBe(headers.length);
            expect(formatted.every((h) => h.format === format)).toBeTruthy();

            // Header-only detail: the bank UI deliberately hides answers.
            await page.locator("ul li button").first().click();
            const detail = page.locator('[aria-live="polite"]').last();
            await expect(detail).toContainText("Question details");
            await expect(detail).toContainText(headers[0].stem);
            await expect(detail).toContainText(headers[0].id);
            await expect(detail).not.toContainText("misconception");
        });

        let quizId = "";
        await test.step("build a filtered quiz and inspect stem, options, explanation, misconception", async () => {
            const built = ok(
                await call(page, "/api/admin/bank/build", "POST", {
                    subject,
                    unit,
                    format, // not honoured by the server yet; verified via headers below
                    count: 3,
                    seed: 20261127,
                    title: `E2E journey ${stamp}`,
                }),
                "bank build",
            ) as { quizId: string; questions: number };
            quizId = built.quizId;
            expect(built.questions).toBe(3);

            const questions = ok(
                await call(page, `/api/admin/quizzes/${quizId}/questions`),
                "built quiz questions",
            ) as QuizQuestion[];
            expect(questions).toHaveLength(3);
            const headers = ok(
                await call(
                    page,
                    `/api/admin/bank/search?subject=${subject}&unit=${encodeURIComponent(unit)}&format=${format}`,
                ),
                "filtered headers for built quiz",
            ) as BankHeader[];
            for (const q of questions) {
                expect(q.questionType).toBe("MCQ");
                const config = parsedConfig(q.config);
                expect(config.unit).toBe(unit);
                expect(q.description.length).toBeGreaterThan(10);
                expect(config.explanation?.length).toBeGreaterThan(0);
                expect(config.options?.length).toBeGreaterThan(1);
                expect(config.optionMeta?.some((m) => m.misconception.length > 0)).toBeTruthy();
                expect(
                    headers.some((h) => firstLine(h.stem) === firstLine(q.description)),
                ).toBeTruthy();
            }
        });

        let assignmentId = "";
        await test.step("create an async assignment, launch its public link, submit a student answer, grade it", async () => {
            const assignmentTitle = `E2E assignment ${stamp}`;
            const studentId = `e2e-stu-${stamp.toString().slice(-7)}`;
            await page.goto(`${B}/admin/assignments`);
            await page.getByRole("tab", { name: "New assignment" }).click();
            await page.locator("#assignment-quiz").fill(quizId);
            await page.locator("#assignment-title").fill(assignmentTitle);
            await page.locator("#assignment-description").fill("round-trip verification");
            await page.getByRole("button", { name: "Create assignment" }).click();

            // The POST is async: the row only appears after create succeeds and
            // the list refetch lands. Wait for it before reading the API.
            const titleButton = page.getByRole("button", { name: assignmentTitle });
            await expect(titleButton).toBeVisible({ timeout: 15000 });

            const assignments = ok(
                await call(page, "/api/admin/assignments"),
                "assignments list",
            ) as Array<{ id: string; title: string }>;
            const assignment = assignments.find((a) => a.title === assignmentTitle);
            expect(assignment).toBeTruthy();
            assignmentId = assignment!.id;
            // Creation already selects the new assignment; clicking the row
            // again would toggle it closed.
            if ((await page.locator('textarea[aria-label="Roster CSV"]').count()) === 0) {
                await titleButton.click();
            }

            const roster = `student_id,name,email\n${studentId},E2E Student,e2e@example.test`;
            await page.locator('textarea[aria-label="Roster CSV"]').fill(roster);
            await page.getByRole("button", { name: "Import CSV" }).click();
            await expect(page.getByText("1 students in roster")).toBeVisible({ timeout: 10_000 });

            await page.locator("#assignment-student").fill(studentId);
            await page.getByRole("button", { name: "Start assignment" }).click();
            await expect(page.getByText(`${studentId}: IN_PROGRESS`)).toBeVisible();
            const progress = ok(
                await call(page, `/api/admin/assignments/${assignmentId}/progress`),
                "assignment progress",
            ) as Array<{ studentId: string; status: string; maxScore: number }>;
            const row = progress.find((p) => p.studentId === studentId);
            expect(row?.status).toBe("IN_PROGRESS");
            expect(row!.maxScore).toBeGreaterThan(0);

            // The public launch surface is a metadata URL (no student HTML page
            // exists yet). Verify it from an anonymous browser context.
            const link = `${B}/api/public/assignments/${assignmentId}?studentId=${encodeURIComponent(studentId)}`;
            const studentCtx = await browser.newContext({ serviceWorkers: "block" });
            try {
                const response = await studentCtx.request.get(link);
                expect(response.ok()).toBeTruthy();
                const meta = (await response.json()) as Record<string, unknown>;
                expect(meta.title).toBe(assignmentTitle);
                expect(JSON.stringify(meta)).not.toContain(quizId);

                const studentPage = await studentCtx.newPage();
                await studentPage.goto(link);
                await expect(studentPage.locator("body")).toContainText(assignmentTitle);

                // Real student answer submission: a practice room on the built
                // MCQ quiz auto-starts Q1 on first join.
                const practice = (await studentPage.evaluate(
                    async ({ base, quizId }) => {
                        const r = await fetch(`${base}/api/public/practice`, {
                            method: "POST",
                            headers: { "Content-Type": "application/json" },
                            body: JSON.stringify({ quizId }),
                        });
                        return r.ok ? await r.json() : { error: await r.text() };
                    },
                    { base: B, quizId },
                )) as { pinCode?: string; id?: string };
                expect(practice.pinCode).toMatch(/^\d{6}$/);

                const playerName = `e2eL${stamp.toString().slice(-5)}`;
                await studentPage.goto(`${B}/j/${practice.pinCode}`);
                await studentPage.getByLabel("Your name").fill(playerName);
                await studentPage.getByRole("button", { name: "Join game" }).click();
                await expect(studentPage.getByText("Question", { exact: true })).toBeVisible({
                    timeout: 15_000,
                });
                await studentPage.locator(".kahoot-options button").first().click();
                const lock = studentPage.getByRole("button", { name: "Lock in answer" });
                await expect(lock).toBeEnabled();
                await lock.click();
                await expect(studentPage.locator(".feedback-banner")).toBeVisible({
                    timeout: 10_000,
                });

                // Session filter inputs are exercised back on the admin page.
                await page.goto(`${B}/admin/analytics`);
                const baseline = ok(
                    await call(page, "/api/admin/analytics/classes"),
                    "class baseline",
                ) as {
                    attempts: number;
                    sessionStats?: Array<{ sessionId: string; attempts: number }>;
                };
                const sessionStat = baseline.sessionStats?.find((s) => s.sessionId === practice.id);
                expect(
                    sessionStat?.attempts,
                    "student answer must reach analytics",
                ).toBeGreaterThan(0);

                const present = ok(
                    await call(page, "/api/admin/analytics/students"),
                    "student baseline",
                ) as Array<{ playerName?: string }>;
                expect(present.some((s) => s.playerName === playerName)).toBeTruthy();

                const analyticsCalls: string[] = [];
                const onRequest = (r: Request) => {
                    if (/\/api\/admin\/analytics\/(students|classes)(\?|$)/.test(r.url()))
                        analyticsCalls.push(r.url());
                };
                page.on("request", onRequest);
                const input = page.locator("#analytics-session");
                const bogus = `missing-${stamp}`;
                await input.pressSequentially(bogus, { delay: 15 });
                await page.waitForTimeout(250);
                expect(analyticsCalls, "no analytics request may fire while typing").toHaveLength(
                    0,
                );
                await page.getByRole("button", { name: "Apply filter" }).click();
                await expect.poll(() => analyticsCalls.length).toBe(2);
                expect(
                    analyticsCalls.every((url) => url.includes(`sessionId=${bogus}`)),
                ).toBeTruthy();
                page.off("request", onRequest);

                await expect(
                    page.getByRole("region", { name: "Class summary" }).getByText("0 attempts"),
                ).toBeVisible();
                await expect(
                    page
                        .getByRole("region", { name: "Student report" })
                        .getByText("No submissions yet"),
                ).toBeVisible();

                await input.fill(practice.id!);
                await page.getByRole("button", { name: "Apply filter" }).click();
                await expect(
                    page.getByRole("region", { name: "Student report" }).getByText(playerName),
                ).toBeVisible({ timeout: 10_000 });
            } finally {
                await studentCtx.close();
            }

            await page.goto(`${B}/admin/assignments`);
            await page.getByRole("button", { name: assignmentTitle }).click();
            await expect(page.getByText(studentId)).toBeVisible();
            const scoreInput = page.getByLabel(`Score for ${studentId}`);
            await scoreInput.fill("1");
            await page.getByRole("button", { name: "Save grade" }).click();
            const gradedRow = page.getByText(studentId, { exact: true }).locator("..");
            await expect(gradedRow).toContainText("GRADED", { timeout: 10_000 });
            await expect(scoreInput).toHaveValue("1");
        });

        await test.step("export and re-import a quiz bundle round-trip", async () => {
            // Use a separate quiz: the assignment quiz is referenced by the
            // async assignment and cannot be deleted without cascading it.
            const exportBuild = ok(
                await call(page, "/api/admin/bank/build", "POST", {
                    subject,
                    unit,
                    format,
                    count: 3,
                    seed: 20261128,
                    title: `E2E export ${stamp}`,
                }),
                "export quiz build",
            ) as { quizId: string; questions: number };
            quizId = exportBuild.quizId;
            await page.goto(`${B}/admin/dashboard`);
            const [download] = await Promise.all([
                page.waitForEvent("download"),
                page.getByRole("button", { name: "Export", exact: true }).click(),
            ]);
            const bundle = JSON.parse(readFileSync((await download.path())!, "utf8")) as {
                version: string;
                quizzes: Array<{
                    id: string;
                    title: string;
                    description: string;
                    questions: ExportedQuestion[];
                }>;
                settings: Record<string, string>;
            };
            expect(bundle.version).toBe("1.0");
            expect(bundle.quizzes.length).toBeGreaterThan(0);
            const entry = bundle.quizzes.find((q) => q.id === quizId);
            expect(entry, "built quiz must be in the export").toBeTruthy();

            // replace=false rejects colliding ids, so remove the quiz before
            // re-importing exactly the exported entry (the UI's replace=true
            // would wipe the shared CI bank).
            expect((await call(page, `/api/admin/quizzes/${quizId}`, "DELETE")).status).toBe(204);
            const imported = ok(
                await call(page, "/api/admin/import", "POST", {
                    json: JSON.stringify({ ...bundle, quizzes: [entry] }),
                    replace: false,
                }),
                "quiz re-import",
            ) as { importedQuestions: number };
            expect(imported.importedQuestions).toBe(entry!.questions.length);
            const roundTripped = ok(
                await call(page, `/api/admin/quizzes/${quizId}/questions`),
                "re-imported questions",
            ) as QuizQuestion[];
            const relisted = ok(
                await call(page, "/api/admin/quizzes"),
                "quizzes after import",
            ) as Array<{ id: string; title: string; description: string }>;
            const restored = relisted.find((q) => q.id === quizId);
            expect(restored?.title).toBe(entry!.title);
            expect(restored?.description).toBe(entry!.description);
            // The admin question endpoint adds quizId/orderIndex/createdAt, so
            // compare only the fields carried by the export format.
            const shape = (q: QuizQuestion | ExportedQuestion) => ({
                id: q.id,
                title: q.title,
                description: q.description,
                type: "questionType" in q ? q.questionType : q.type,
                timeLimitSec: q.timeLimitSec,
                pointsBase: q.pointsBase,
                config: parsedConfig(q.config),
            });
            expect(roundTripped.map(shape)).toEqual(entry!.questions.map(shape));
            expect((await call(page, `/api/admin/quizzes/${quizId}`, "DELETE")).status).toBe(204);
        });

        await test.step("change an admin setting and verify it survives a reload", async () => {
            const key = `e2eJourneyProbe${stamp}`;
            expect(
                (await call(page, "/api/admin/settings", "PUT", { [key]: "before" })).status,
            ).toBeLessThan(300);
            await page.goto(`${B}/admin`);
            await page.getByRole("tab", { name: "Settings" }).click();
            const input = page
                .locator("label")
                .filter({ hasText: key })
                .locator("xpath=following-sibling::input[1]");
            await expect(input).toHaveValue("before");
            await input.fill("after");
            await page.getByRole("button", { name: "Save", exact: true }).click();
            await expect(page.getByText("Saved!")).toBeVisible();

            await page.reload();
            await page.getByRole("tab", { name: "Settings" }).click();
            const afterReload = page
                .locator("label")
                .filter({ hasText: key })
                .locator("xpath=following-sibling::input[1]");
            await expect(afterReload).toHaveValue("after");
            const settings = ok(
                await call(page, "/api/admin/settings"),
                "settings after reload",
            ) as Record<string, string>;
            expect(settings[key]).toBe("after");
            await call(page, "/api/admin/settings", "PUT", { [key]: "before" });
        });
    });
});
