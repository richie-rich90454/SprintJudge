import { test, expect, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";

test.use({ serviceWorkers: "block" });

const B = process.env.SPRINTJUDGE_BACKEND_URL ?? "http://127.0.0.1:8080";
const PASSWORD = process.env.SPRINTJUDGE_ADMIN_PASSWORD ?? "ci-admin-password";

async function login(page: Page) {
    await page.goto(`${B}/admin/login`);
    await page.fill('input[name="username"]', "admin");
    await page.fill('input[name="password"]', PASSWORD);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/admin\/dashboard/);
}

test.describe("functionality", () => {
    test("license footer is gone and About explains the license", async ({ page }) => {
        await page.goto(B + "/");
        await expect(page.locator("footer")).toHaveCount(0);
        await page.getByRole("button", { name: "About SprintJudge" }).click();
        await expect(page.getByText(/GNU General Public License v3\.0/)).toBeVisible();
    });

    test("every catalog subject is searchable in the question bank", async ({ page }) => {
        await login(page);
        const schema = JSON.parse(
            readFileSync(new URL("../../contracts/question-schema.json", import.meta.url), "utf8"),
        );
        const subjects = schema.properties.subject.enum as string[];
        const result = await page.evaluate(async ({ base, subjects }) => {
            const token = document.cookie
                .split("; ")
                .find((cookie) => cookie.startsWith("XSRF-TOKEN="))
                ?.split("=")[1] ?? "";
            const counts: Record<string, number> = {};
            for (const subject of subjects) {
                const response = await fetch(
                    `${base}/api/admin/bank/search?subject=${subject}`,
                    { credentials: "include", headers: { "X-XSRF-TOKEN": token } },
                );
                counts[subject] = response.ok ? (await response.json()).length : -1;
            }
            return counts;
        }, { base: B, subjects });
        for (const subject of subjects) {
            expect(result[subject], `${subject} returned ${result[subject]}`).toBeGreaterThan(0);
        }
    });

    test("admin quiz, question, settings, export/import, build and game flows", async ({ page }) => {
        await login(page);
        const results = await page.evaluate(async (base) => {
            const token = document.cookie
                .split("; ")
                .find((cookie) => cookie.startsWith("XSRF-TOKEN="))
                ?.split("=")[1] ?? "";
            const call = async (path: string, method: string, body?: unknown) => {
                const response = await fetch(base + path, {
                    method,
                    credentials: "include",
                    headers: {
                        "Content-Type": "application/json",
                        "X-XSRF-TOKEN": token,
                    },
                    body: body === undefined ? undefined : JSON.stringify(body),
                });
                return { status: response.status, body: await response.text() };
            };
            const check: Record<string, boolean> = {};
            const stamp = Date.now();
            const quizId = "e2e-matrix-" + stamp;
            const questionId = "e2eq-" + stamp;
            const importedId = "e2e-import-" + stamp;

            // A seed template supplies a valid, schema-checked question config.
            const existing = JSON.parse((await call("/api/admin/quizzes", "GET")).body) as { id: string }[];
            const template = JSON.parse(
                (await call(`/api/admin/quizzes/${existing[0].id}/questions`, "GET")).body,
            )[0] as Record<string, unknown> | undefined;
            check.hasTemplate = Boolean(template);

            check.createQuiz = (await call("/api/admin/quizzes", "POST", {
                id: quizId,
                title: "E2E matrix quiz",
                description: "temporary",
            })).status === 200;
            const listed = JSON.parse((await call("/api/admin/quizzes", "GET")).body) as { id: string }[];
            check.createdQuizListed = listed.some((q) => q.id === quizId);

            if (template) {
                const payload = {
                    id: questionId,
                    quizId,
                    title: "E2E question",
                    description: "temporary",
                    questionType: template.questionType,
                    languagesAllowed: null,
                    timeLimitSec: 30,
                    pointsBase: 100,
                    config: template.config,
                    orderIndex: 0,
                };
                check.addQuestion = (await call(`/api/admin/quizzes/${quizId}/questions`, "POST", payload)).status === 200;
                const readBack = JSON.parse((await call(`/api/admin/quizzes/${quizId}/questions`, "GET")).body) as { id: string }[];
                check.questionListed = readBack.some((q) => q.id === questionId);
                check.updateQuestion = (await call(`/api/admin/questions/${questionId}`, "PUT", {
                    ...payload,
                    title: "E2E question edited",
                })).status === 200;
                const edited = JSON.parse((await call(`/api/admin/quizzes/${quizId}/questions`, "GET")).body) as { title: string }[];
                check.questionEdited = edited.some((q) => q.title === "E2E question edited");
                check.deleteQuestion = (await call(`/api/admin/questions/${questionId}`, "DELETE")).status === 204;
                const afterDelete = JSON.parse((await call(`/api/admin/quizzes/${quizId}/questions`, "GET")).body) as { id: string }[];
                check.questionDeleted = !afterDelete.some((q) => q.id === questionId);
            }

            const settings = await call("/api/admin/settings", "GET");
            check.getSettings = settings.status === 200;
            check.saveSettings = (await call("/api/admin/settings", "PUT", { e2eProbe: String(stamp) })).status === 200;
            const afterSettings = await call("/api/admin/settings", "GET");
            check.settingsPersisted = JSON.parse(afterSettings.body).e2eProbe === String(stamp);

            const exported = await call("/api/admin/export", "GET");
            check.export = exported.status === 200 && JSON.parse(exported.body).quizzes.length > 0;
            const bundle = {
                version: "1.0",
                exportedAt: 1,
                quizzes: [{ id: importedId, title: "E2E import", description: "temporary", template: false, questions: [] }],
                settings: {},
            };
            check.import = (await call("/api/admin/import", "POST", { json: JSON.stringify(bundle), replace: false })).status === 200;
            check.importedQuizListed = (JSON.parse((await call("/api/admin/quizzes", "GET")).body) as { id: string }[])
                .some((q) => q.id === importedId);
            check.deleteImportedQuiz = (await call(`/api/admin/quizzes/${importedId}`, "DELETE")).status === 204;

            const built = await call("/api/admin/bank/build", "POST", {
                subject: "JAVA_PROGRAMMING", count: 3, title: "E2E matrix built",
            });
            check.build = built.status === 200 && JSON.parse(built.body).questions === 3;
            const builtQuiz = JSON.parse(built.body).quizId as string;
            const game = await call("/api/admin/games", "POST", { quizId, gameMode: "STANDARD" });
            check.createGame = game.status === 200 && Boolean(JSON.parse(game.body).pinCode);
            check.deleteBuiltQuiz = (await call(`/api/admin/quizzes/${builtQuiz}`, "DELETE")).status === 204;
            check.deleteQuiz = (await call(`/api/admin/quizzes/${quizId}`, "DELETE")).status === 204;
            return check;
        }, B);
        for (const [action, ok] of Object.entries(results)) {
            expect(ok, `admin action failed: ${action}`).toBeTruthy();
        }
    });

    test("native executor runs every supported language without WSL", async ({ request }) => {
        const cases: { language: string; sourceCode: string; expected: string }[] = [
            { language: "python", sourceCode: "print('py-ok')", expected: "py-ok" },
            { language: "node", sourceCode: "console.log('node-ok')", expected: "node-ok" },
            {
                language: "java",
                sourceCode:
                    'public class Main { public static void main(String[] a){ System.out.println("java-ok"); } }',
                expected: "java-ok",
            },
            {
                language: "c",
                sourceCode: '#include <stdio.h>\nint main(){printf("c-ok\\n");return 0;}',
                expected: "c-ok",
            },
            {
                language: "cpp",
                sourceCode: '#include <iostream>\nint main(){std::cout << "cpp-ok" << std::endl;}',
                expected: "cpp-ok",
            },
        ];
        for (const item of cases) {
            const response = await request.post(`${B}/api/public/run`, { data: item });
            expect(response.ok(), `${item.language} run failed`).toBeTruthy();
            const result = (await response.json()) as { ok: boolean; output: string };
            expect(result.ok, `${item.language} did not succeed`).toBeTruthy();
            expect(result.output.trim(), `${item.language} output`).toBe(item.expected);
        }
    });

    test("all six game modes can be created and hosted", async ({ page }) => {
        await login(page);
        const quizId = await page.evaluate(async (base) => {
            const response = await fetch(base + "/api/admin/quizzes", { credentials: "include" });
            return (await response.json())[0].id as string;
        }, B);
        const modes = ["STANDARD", "AUTO_PILOT", "PRACTICE", "EXAM", "TEAM", "BATTLE"];
        for (const mode of modes) {
            const pin = await page.evaluate(
                async ({ base, quizId, mode }) => {
                    const token = document.cookie
                        .split("; ")
                        .find((cookie) => cookie.startsWith("XSRF-TOKEN="))
                        ?.split("=")[1] ?? "";
                    const response = await fetch(base + "/api/admin/games", {
                        method: "POST",
                        credentials: "include",
                        headers: {
                            "Content-Type": "application/json",
                            "X-XSRF-TOKEN": token,
                        },
                        body: JSON.stringify({ quizId, gameMode: mode }),
                    });
                    if (!response.ok) return null;
                    return (await response.json()).pinCode as string;
                },
                { base: B, quizId, mode },
            );
            expect(pin, `${mode} failed to create a game`).toBeTruthy();
            await page.goto(`${B}/host?pin=${pin}`);
            await expect(page.locator("body")).toContainText(pin as string, { timeout: 10000 });
            await expect(page.getByText(/share the pin|no players yet/i).first()).toBeVisible({ timeout: 10000 });
        }
    });
});
