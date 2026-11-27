import { expect, test, type Page } from "@playwright/test";
import {
    BACKEND,
    discardQuiz,
    hostGame,
    loginAdmin,
    practicePin,
    seedQuiz,
    type SeedQuestion,
} from "./explore-player-helper";

// The PWA service worker reloads on controllerchange and cancels join/submit traffic.
test.use({ serviceWorkers: "block" });

const MCQ_TITLE = "Which keyword declares a constant in Java?";
const MCQ_EXPLANATION =
    "A final variable cannot be reassigned, which is how Java expresses a constant.";
const MULTI_TITLE = "Select the primitive Java types";
const FILL_TITLE = "Fill the blank: the JVM runs Java bytecode";
const NUMERIC_TITLE = "What is six times seven?";
const CODE_TITLE = "Print forty-two from Python";

function journeyQuestions(tag: string, timeLimitSec = 45): SeedQuestion[] {
    return [
        {
            id: `e2e-${tag}-mcq`,
            title: MCQ_TITLE,
            questionType: "MCQ",
            config: {
                options: ["var", "final", "static", "let"],
                correctIndex: 1,
                explanation: MCQ_EXPLANATION,
            },
            timeLimitSec,
            pointsBase: 100,
        },
        {
            id: `e2e-${tag}-multi`,
            title: MULTI_TITLE,
            questionType: "MULTIPLE_SELECT",
            config: {
                options: ["int", "String", "boolean", "List"],
                correctIndices: [0, 2],
                explanation: "int and boolean are primitives; String and List are reference types.",
            },
            timeLimitSec,
            pointsBase: 200,
        },
        {
            id: `e2e-${tag}-fill`,
            title: FILL_TITLE,
            questionType: "FILL_BLANK",
            config: {
                snippet: "The JVM executes ___ bytecode.",
                answer: "Java",
                explanation: "Java source compiles to Java bytecode, which the JVM executes.",
            },
            timeLimitSec,
            pointsBase: 100,
        },
        {
            id: `e2e-${tag}-numeric`,
            title: NUMERIC_TITLE,
            questionType: "NUMERIC",
            config: {
                answer: 42,
                tolerance: 0,
                explanation: "Six times seven is forty-two.",
            },
            timeLimitSec,
            pointsBase: 100,
        },
        {
            id: `e2e-${tag}-code`,
            title: CODE_TITLE,
            questionType: "OJ_FULL",
            config: {
                starter: "print(42)",
                defaultLanguage: "python",
                testCases: [{ input: "", expectedOutput: "42" }],
                explanation: "The judge runs your program and compares its output.",
            },
            languagesAllowed: ["python"],
            timeLimitSec: 90,
            pointsBase: 200,
        },
    ];
}

function singleMcq(tag: string, timeLimitSec: number): SeedQuestion[] {
    return [
        {
            id: `e2e-${tag}-single`,
            title: MCQ_TITLE,
            questionType: "MCQ",
            config: {
                options: ["var", "final", "static", "let"],
                correctIndex: 1,
                explanation: MCQ_EXPLANATION,
            },
            timeLimitSec,
            pointsBase: 100,
        },
    ];
}

async function openHost(
    context: import("@playwright/test").BrowserContext,
    pin: string,
): Promise<Page> {
    const host = await context.newPage();
    await host.goto(`${BACKEND}/host?pin=${pin}`);
    await expect(host.getByText(pin, { exact: true })).toBeVisible({ timeout: 15000 });
    await expect(host.getByRole("button", { name: "Start round" })).toBeVisible({ timeout: 15000 });
    return host;
}

async function revealAndAdvance(host: Page, student: Page, nextTitle?: string): Promise<void> {
    await host.getByRole("button", { name: "Reveal answer" }).click();
    await expect(student.getByText("Round complete")).toBeVisible({ timeout: 10000 });
    if (nextTitle) {
        await host.getByRole("button", { name: "Next question" }).click();
        await expect(student.getByRole("heading", { name: nextTitle })).toBeVisible({
            timeout: 10000,
        });
    }
}

test.describe("explore player journey", () => {
    test("landing to host-led multi-format play, review, refresh and socket reconnect", async ({
        page: admin,
        context,
    }) => {
        test.setTimeout(75_000);
        const tag = Date.now().toString(36);
        await loginAdmin(admin);
        const quizId = await seedQuiz(admin, journeyQuestions(tag));
        const pin = await hostGame(admin, quizId, "STANDARD");
        const host = await openHost(context, pin);
        const student = await context.newPage();
        try {
            // Capture sockets so a transport drop can be forced deterministically;
            // the backend exposes no endpoint to kill a player connection.
            await student.addInitScript(() => {
                const NativeSocket = window.WebSocket;
                const sockets: WebSocket[] = [];
                (window as unknown as { __oqSockets: WebSocket[] }).__oqSockets = sockets;
                (window as unknown as { WebSocket: typeof WebSocket }).WebSocket =
                    class extends NativeSocket {
                        constructor(url: string | URL, protocols?: string | string[]) {
                            super(url, protocols);
                            sockets.push(this);
                        }
                    } as typeof WebSocket;
            });

            await student.goto("/");
            await expect(student.getByText("Join a live game")).toBeVisible();
            await student.getByLabel("Your name").fill("Ada");
            await student.getByLabel("Game PIN").fill(pin);
            await student.getByRole("button", { name: "Join game" }).click();

            await host.getByRole("button", { name: "Start round" }).click();
            await expect(student.getByRole("heading", { name: MCQ_TITLE })).toBeVisible({
                timeout: 15000,
            });

            // MCQ: correct single choice.
            await student.locator(".renderer-host").getByRole("button", { name: /final/i }).click();
            await student.getByRole("button", { name: "Lock in answer" }).click();
            await expect(student.getByText(/pts/)).toBeVisible({ timeout: 10000 });

            // Refresh mid-game: the score survives on the host board and the
            // current question is re-sent to the reclaimed seat.
            const scoreRow = host.locator(".lb-row").filter({ hasText: "Ada" });
            await expect(scoreRow).toBeVisible();
            const scoreBefore = (await scoreRow.innerText()).trim();
            await student.reload();
            await expect(student.getByRole("heading", { name: MCQ_TITLE })).toBeVisible({
                timeout: 15000,
            });
            await expect(student.getByText("No game", { exact: true })).toHaveCount(0);
            await expect(scoreRow).toBeVisible({ timeout: 10000 });
            expect((await scoreRow.innerText()).trim()).toBe(scoreBefore);

            // Drop the socket and let the client reconnect + rejoin by token.
            const socketsBefore = await student.evaluate(
                () => (window as unknown as { __oqSockets: WebSocket[] }).__oqSockets.length,
            );
            await student.evaluate(() =>
                (window as unknown as { __oqSockets: WebSocket[] }).__oqSockets.at(-1)?.close(),
            );
            await expect
                .poll(
                    async () =>
                        student.evaluate(
                            () =>
                                (window as unknown as { __oqSockets: WebSocket[] }).__oqSockets
                                    .length,
                        ),
                    { timeout: 15000 },
                )
                .toBe(socketsBefore + 1);
            await expect(student.getByRole("heading", { name: MCQ_TITLE })).toBeVisible();
            await expect(scoreRow).toBeVisible({ timeout: 10000 });
            expect((await scoreRow.innerText()).trim()).toBe(scoreBefore);

            await host.getByRole("button", { name: "Reveal answer" }).click();
            await expect(student.getByText("Round complete")).toBeVisible({ timeout: 10000 });
            await expect(student.getByText(MCQ_EXPLANATION)).toBeVisible();

            // MULTIPLE_SELECT: one of two correct options -> partial ("Not quite").
            await host.getByRole("button", { name: "Next question" }).click();
            await expect(student.getByRole("heading", { name: MULTI_TITLE })).toBeVisible({
                timeout: 10000,
            });
            const multiButtons = student.locator(".renderer-host button");
            await multiButtons.nth(0).click();
            await multiButtons.nth(1).click();
            await student.getByRole("button", { name: "Lock in answer" }).click();
            await expect(student.getByText("Not quite")).toBeVisible({ timeout: 10000 });
            await revealAndAdvance(host, student, FILL_TITLE);

            // FILL_BLANK.
            await student.getByPlaceholder("Fill the blank").fill("Java");
            await student.getByRole("button", { name: "Lock in answer" }).click();
            await expect(student.getByText(/pts/)).toBeVisible({ timeout: 10000 });
            await revealAndAdvance(host, student, NUMERIC_TITLE);

            // NUMERIC.
            await student.getByPlaceholder("Enter a number").fill("42");
            await student.getByRole("button", { name: "Lock in answer" }).click();
            await expect(student.getByText(/pts/)).toBeVisible({ timeout: 10000 });
            await revealAndAdvance(host, student, CODE_TITLE);

            // Code: the starter program is already a valid answer; the real
            // judge runs it and reports pass/fail through the player UI.
            await expect(student.getByRole("button", { name: "Compile and submit" })).toBeEnabled();
            await student.getByRole("button", { name: "Compile and submit" }).click();
            await expect(student.getByText(/tests passed/)).toBeVisible({ timeout: 25_000 });
            await revealAndAdvance(host, student);

            await host.getByRole("button", { name: "Next question" }).click();
            await expect(student).toHaveURL(/\/results/, { timeout: 15000 });
            await expect(student.getByText("Results", { exact: true })).toBeVisible();
        } finally {
            await discardQuiz(admin, quizId);
            await host.close();
            await student.close();
        }
    });

    test("timer expiry on an unanswered question keeps the player in the game", async ({
        page: admin,
        context,
    }) => {
        test.setTimeout(35_000);
        const tag = Date.now().toString(36);
        await loginAdmin(admin);
        const quizId = await seedQuiz(admin, singleMcq(tag, 2));
        const pin = await hostGame(admin, quizId, "STANDARD");
        const host = await openHost(context, pin);
        const student = await context.newPage();
        try {
            await student.goto(`/j/${pin}`);
            await student.getByLabel("Your name").fill("Ada");
            await student.getByRole("button", { name: "Join game" }).click();
            await host.getByRole("button", { name: "Start round" }).click();
            await expect(student.getByRole("heading", { name: MCQ_TITLE })).toBeVisible({
                timeout: 15000,
            });

            await expect(student.getByText("Round complete")).toBeVisible({ timeout: 12000 });
            await expect(student.getByText("No game", { exact: true })).toHaveCount(0);
            await expect(student.getByText(MCQ_EXPLANATION)).toBeVisible();

            await host.getByRole("button", { name: "Next question" }).click();
            await expect(student).toHaveURL(/\/results/, { timeout: 15000 });
        } finally {
            await discardQuiz(admin, quizId);
            await host.close();
            await student.close();
        }
    });

    test("solo practice completes a full round and advances to results", async ({
        page,
        context,
        request,
    }) => {
        test.setTimeout(35_000);
        const tag = Date.now().toString(36);
        const admin = await context.newPage();
        await loginAdmin(admin);
        const quizId = await seedQuiz(admin, singleMcq(tag, 30));
        const pin = await practicePin(request, quizId);
        try {
            await page.goto("/solo");
            await expect(page.getByText("Enter your practice PIN")).toBeVisible();
            await page.getByLabel("Your name").fill("Ada");
            await page.getByLabel("Game PIN").fill(pin);
            await page.getByRole("button", { name: "Join game" }).click();
            await expect(page.getByRole("heading", { name: MCQ_TITLE })).toBeVisible({
                timeout: 15000,
            });

            await page.locator(".renderer-host").getByRole("button", { name: /final/i }).click();
            await page.getByRole("button", { name: "Lock in answer" }).click();
            await expect(page.getByText("Round complete")).toBeVisible({ timeout: 10000 });
            await expect(page.getByText(MCQ_EXPLANATION)).toBeVisible();

            await expect(page).toHaveURL(/\/results/, { timeout: 12000 });
        } finally {
            await discardQuiz(admin, quizId);
            await admin.close();
        }
    });

    test("spectator watches a full round read-only", async ({ page: admin, context }) => {
        test.setTimeout(35_000);
        const tag = Date.now().toString(36);
        await loginAdmin(admin);
        const quizId = await seedQuiz(admin, singleMcq(tag, 3));
        const pin = await hostGame(admin, quizId, "STANDARD");
        const host = await openHost(context, pin);
        const viewer = await context.newPage();
        try {
            await viewer.goto(`/watch/${pin}`);
            await expect(viewer.getByText("Projector view. Read only.")).toBeVisible({
                timeout: 15000,
            });
            await host.getByRole("button", { name: "Start round" }).click();
            await expect(viewer.getByText(MCQ_TITLE)).toBeVisible({ timeout: 15000 });
            await expect(viewer.getByText("Question 1 of 1")).toBeVisible();
            await expect(viewer.getByText("Projector view. Read only.")).toBeVisible();

            await expect(viewer.getByText("Correct answer")).toBeVisible({ timeout: 12000 });
            await expect(viewer.getByText(MCQ_EXPLANATION)).toBeVisible();
            await host.getByRole("button", { name: "Next question" }).click();
            await expect(viewer.getByText("Final standings")).toBeVisible({ timeout: 15000 });
        } finally {
            await discardQuiz(admin, quizId);
            await host.close();
            await viewer.close();
        }
    });
});
