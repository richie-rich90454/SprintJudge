import { test, expect, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";

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

    test("all admin actions respond successfully through the browser session", async ({ page }) => {
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
            const out: Record<string, number> = {};
            out.listQuizzes = (await call("/api/admin/quizzes", "GET")).status;
            const quizzes = JSON.parse((await call("/api/admin/quizzes", "GET")).body ?? "[]");
            const questionsBody = await call(`/api/admin/quizzes/${quizzes[0].id}/questions`, "GET");
            out.listQuestions = questionsBody.status;
            const template = JSON.parse(questionsBody.body)[0] as Record<string, unknown> | undefined;
            if (template) {
                const questionId = "e2eq-" + Date.now();
                const payload = {
                    id: questionId,
                    quizId: quizzes[0].id,
                    title: "E2E question",
                    description: "temporary",
                    questionType: template.questionType,
                    languagesAllowed: null,
                    timeLimitSec: 30,
                    pointsBase: 100,
                    config: template.config,
                    orderIndex: 0,
                };
                out.addQuestion = (await call(`/api/admin/quizzes/${quizzes[0].id}/questions`, "POST", payload)).status;
                out.updateQuestion = (await call(`/api/admin/questions/${questionId}`, "PUT", {
                    ...payload,
                    title: "E2E question edited",
                })).status;
                out.deleteQuestion = (await call(`/api/admin/questions/${questionId}`, "DELETE")).status;
            }
            out.getSettings = (await call("/api/admin/settings", "GET")).status;
            out.export = (await call("/api/admin/export", "GET")).status;
            const built = await call("/api/admin/bank/build", "POST", {
                subject: "JAVA_PROGRAMMING",
                count: 3,
                title: "E2E admin matrix",
            });
            out.build = built.status;
            const builtQuiz = JSON.parse(built.body).quizId as string;
            out.createGame = (await call("/api/admin/games", "POST", {
                quizId: quizzes[0].id,
                gameMode: "STANDARD",
            })).status;
            out.saveSettings = (await call("/api/admin/settings", "PUT", { e2eProbe: "1" })).status;
            const quizId = "e2e-" + Date.now();
            out.createQuiz = (await call("/api/admin/quizzes", "POST", {
                id: quizId,
                title: "E2E matrix quiz",
                description: "temporary",
            })).status;
            out.deleteQuiz = (await call(`/api/admin/quizzes/${quizId}`, "DELETE")).status;
            out.deleteBuiltQuiz = (await call(`/api/admin/quizzes/${builtQuiz}`, "DELETE")).status;
            return out;
        }, B);
        for (const [action, status] of Object.entries(results)) {
            expect(status, `${action} returned ${status}`).toBeLessThan(300);
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
            const text = await page.locator("body").innerText();
            expect(text.length, `${mode} host screen was empty`).toBeGreaterThan(10);
        }
    });
});
