import { expect, type APIRequestContext, type Page } from "@playwright/test";

export const BACKEND = process.env.SPRINTJUDGE_BACKEND_URL ?? "http://127.0.0.1:8080";
const PASSWORD = process.env.SPRINTJUDGE_ADMIN_PASSWORD ?? "ci-admin-password";

export interface SeedQuestion {
    id: string;
    title: string;
    questionType: string;
    config: Record<string, unknown>;
    timeLimitSec: number;
    pointsBase: number;
    languagesAllowed?: string[] | null;
}

/** Form login is served by the backend, so the host socket inherits admin auth. */
export async function loginAdmin(page: Page): Promise<void> {
    await page.goto(`${BACKEND}/admin/login`);
    await page.locator('input[name="username"]').fill("admin");
    await page.locator('input[name="password"]').fill(PASSWORD);
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 15000 });
}

/** Same XSRF convention as the other admin specs: echo the cookie into the header. */
export async function adminCall<T>(
    page: Page,
    path: string,
    method: "GET" | "POST" | "PUT" | "DELETE",
    body?: unknown,
): Promise<T> {
    const result = await page.evaluate(
        async ({ base, path, method, body }) => {
            const token =
                document.cookie
                    .split("; ")
                    .find((cookie) => cookie.startsWith("XSRF-TOKEN="))
                    ?.split("=")[1] ?? "";
            const response = await fetch(base + path, {
                method,
                credentials: "include",
                headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": token },
                body: body === undefined ? undefined : JSON.stringify(body),
            });
            return { status: response.status, text: await response.text() };
        },
        { base: BACKEND, path, method, body },
    );
    expect(result.status, `${method} ${path}: ${result.text}`).toBeGreaterThanOrEqual(200);
    expect(result.status, `${method} ${path}: ${result.text}`).toBeLessThan(300);
    return (result.text ? JSON.parse(result.text) : undefined) as T;
}

export async function seedQuiz(page: Page, questions: SeedQuestion[]): Promise<string> {
    const quizId = `e2e-player-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`;
    await adminCall(page, "/api/admin/quizzes", "POST", {
        id: quizId,
        title: "E2E player journey",
        description: "temporary",
    });
    for (const [index, question] of questions.entries()) {
        await adminCall(page, `/api/admin/quizzes/${quizId}/questions`, "POST", {
            id: question.id,
            quizId,
            title: question.title,
            description: question.title,
            questionType: question.questionType,
            languagesAllowed: question.languagesAllowed ?? null,
            timeLimitSec: question.timeLimitSec,
            pointsBase: question.pointsBase,
            config: JSON.stringify(question.config),
            orderIndex: index,
        });
    }
    return quizId;
}

export async function hostGame(page: Page, quizId: string, gameMode: string): Promise<string> {
    const session = await adminCall<{ pinCode: string }>(page, "/api/admin/games", "POST", {
        quizId,
        gameMode,
    });
    expect(session.pinCode).toMatch(/^\d{6}$/);
    return session.pinCode;
}

export async function practicePin(request: APIRequestContext, quizId: string): Promise<string> {
    const response = await request.post("/api/public/practice", { data: { quizId } });
    expect(response.ok()).toBeTruthy();
    const { pinCode } = (await response.json()) as { pinCode: string };
    expect(pinCode).toMatch(/^\d{6}$/);
    return pinCode;
}

export async function discardQuiz(page: Page, quizId: string): Promise<void> {
    try {
        await adminCall(page, `/api/admin/quizzes/${quizId}`, "DELETE");
    } catch {
        // Best-effort cleanup: a failed test must not mask its own failure.
    }
}
