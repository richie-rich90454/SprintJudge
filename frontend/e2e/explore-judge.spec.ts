import { expect, test, type APIRequestContext } from "@playwright/test";
import {
    BACKEND,
    discardQuiz,
    hostGame,
    loginAdmin,
    seedQuiz,
    type SeedQuestion,
} from "./explore-player-helper";

// The PWA service worker reloads on controllerchange and cancels join/submit traffic.
test.use({ serviceWorkers: "block" });

// The CI backend boots with --sprintjudge.executor.public-run=true (see playwright.config.ts).
const RUN_URL = `${BACKEND}/api/public/run`;
const ALL_LANGUAGES = ["python", "node", "java", "c", "cpp"];

interface RunResult {
    ok: boolean;
    output: string;
    error: string;
    status: string;
}

interface SubmitFrame {
    type: string;
    questionId: string;
    language?: string;
    response?: { source?: string; language?: string };
}

async function runCode(
    request: APIRequestContext,
    language: string,
    sourceCode: string,
    stdin = "",
    timeoutSec = 5,
): Promise<{ result: RunResult; elapsedMs: number }> {
    const started = Date.now();
    const response = await request.post(RUN_URL, {
        data: { language, sourceCode, stdin, timeoutSec },
        timeout: 20_000,
    });
    expect(response.status(), `${language}: POST /api/public/run`).toBe(200);
    const result = (await response.json()) as RunResult;
    return { result, elapsedMs: Date.now() - started };
}

const CORRECT: { language: string; sourceCode: string; expected: string }[] = [
    { language: "python", sourceCode: "print('py-ok')", expected: "py-ok" },
    { language: "node", sourceCode: "console.log('node-ok')", expected: "node-ok" },
    {
        language: "java",
        sourceCode:
            'public class Main { public static void main(String[] a) { System.out.println("java-ok"); } }',
        expected: "java-ok",
    },
    {
        language: "c",
        sourceCode: '#include <stdio.h>\nint main(void){ printf("c-ok\\n"); return 0; }',
        expected: "c-ok",
    },
    {
        language: "cpp",
        sourceCode: '#include <iostream>\nint main(){ std::cout << "cpp-ok" << std::endl; }',
        expected: "cpp-ok",
    },
];

test.describe("explore judge (real native executor)", () => {
    test("runs a correct solution in every supported language and isolates submissions", async ({
        request,
    }) => {
        for (const item of CORRECT) {
            const { result } = await runCode(request, item.language, item.sourceCode);
            expect(result.status, `${item.language} status`).toBe("ok");
            expect(result.ok, `${item.language} ok`).toBe(true);
            expect(result.output.trim(), `${item.language} output`).toBe(item.expected);
            expect(result.error, `${item.language} diagnostics`).toBe("");
        }
        // Return to python after java/c/cpp compilation: no cached artifact or
        // compiler language leaks into the next submission.
        const again = await runCode(request, CORRECT[0].language, CORRECT[0].sourceCode);
        expect(again.result.status).toBe("ok");
        expect(again.result.output.trim()).toBe(CORRECT[0].expected);
    });

    test("reports compilation, runtime, timeout and capped stdout distinctly", async ({
        request,
    }) => {
        const compile = await runCode(
            request,
            "java",
            "public class Main { public static void main(String[] a) { int x = ; } }",
            "",
            5,
        );
        expect(compile.result.status).toBe("compilation_error");
        expect(compile.result.ok).toBe(false);
        expect(compile.result.error, "javac diagnostics").toMatch(
            /\.java:|error|expected|illegal/i,
        );
        expect(compile.elapsedMs).toBeLessThan(8_000);

        const runtime = await runCode(
            request,
            "python",
            "raise RuntimeError('boom-marker')",
            "",
            5,
        );
        expect(runtime.result.status).toBe("runtime_error");
        expect(runtime.result.ok).toBe(false);
        expect(runtime.result.output).toContain("boom-marker");
        expect(runtime.result.error, "runtime failure has no compiler diagnostics").toBe("");

        const loop = await runCode(request, "python", "while True: pass", "", 2);
        expect(loop.result.status).toBe("timeout");
        expect(loop.result.ok).toBe(false);
        expect(loop.elapsedMs).toBeLessThan(10_000);

        const chatty = await runCode(request, "python", "print('Z' * 1200000)", "", 5);
        expect(chatty.result.status).toBe("stdout_exceeded_1MB");
        expect(chatty.result.ok).toBe(false);
        expect(chatty.result.output).toBe("");
        expect(chatty.elapsedMs).toBeLessThan(10_000);
    });

    test("delivers stdin exactly, including the trailing newline", async ({ request }) => {
        const source = "import sys\nsys.stdout.write(repr(sys.stdin.read()))";
        const withNewline = await runCode(request, "python", source, "alpha\nbeta\n", 5);
        expect(withNewline.result.status).toBe("ok");
        expect(withNewline.result.output).toBe("'alpha\\nbeta\\n'");

        const noNewline = await runCode(request, "python", source, "single", 5);
        expect(noNewline.result.status).toBe("ok");
        expect(noNewline.result.output).toBe("'single'");
    });

    test("console and OJ submissions show real judge results across language switches", async ({
        page: admin,
        context,
    }) => {
        test.setTimeout(120_000);
        const tag = Date.now().toString(36);
        const pyId = `e2e-judge-${tag}-py`;
        const javaId = `e2e-judge-${tag}-java`;
        const loopId = `e2e-judge-${tag}-loop`;
        const twoCases = [
            { input: "", expectedOutput: "42" },
            { input: "ignored\n", expectedOutput: "42" },
        ];
        const oj = (id: string, title: string, starter: string): SeedQuestion => ({
            id,
            title,
            questionType: "OJ_FULL",
            config: {
                starter,
                defaultLanguage: "python",
                testCases: twoCases,
                explanation: "Two cases compare your program's output.",
            },
            languagesAllowed: ALL_LANGUAGES,
            timeLimitSec: 30,
            pointsBase: 100,
        });
        const javaCorrect =
            "public class Main { public static void main(String[] a) { System.out.println(42); } }";
        const questions = [
            oj(pyId, "Judge: print forty-two", "print(42)"),
            oj(javaId, "Judge: switched to Java", javaCorrect),
            oj(loopId, "Judge: console stress", "while True: pass"),
        ];

        await loginAdmin(admin);
        const quizId = await seedQuiz(admin, questions);
        const pin = await hostGame(admin, quizId, "STANDARD");
        const host = await context.newPage();
        const student = await context.newPage();
        // Watch the real frames: the feedback banner only reports the judge's
        // verdict, so a lost source or language is otherwise invisible here.
        const submits: SubmitFrame[] = [];
        student.on("websocket", (ws) => {
            ws.on("framereceived", (frame) => {
                if (typeof frame.payload !== "string") return;
                try {
                    const msg = JSON.parse(frame.payload) as SubmitFrame;
                    if (msg.type === "SUBMIT") submits.push(msg);
                } catch {
                    /* binary/control frames are not JSON */
                }
            });
        });
        try {
            await host.goto(`${BACKEND}/host?pin=${pin}`);
            await expect(host.getByRole("button", { name: "Start round" })).toBeVisible({
                timeout: 15_000,
            });
            // Backend-served SPA, same origin as the host and the console API:
            // the Vite proxy would forward Origin: localhost:5173 and the
            // console's POST /api/public/run would be rejected as cross-origin.
            await student.goto(`${BACKEND}/j/${pin}`);
            await student.getByLabel("Your name").fill("Ada");
            await student.getByRole("button", { name: "Join game" }).click();
            await expect(student).toHaveURL(/\/play/, { timeout: 15_000 });
            await expect(
                student.getByText("Waiting for the host to start the next question."),
            ).toBeVisible({ timeout: 15_000 });
            // join() calls clearStaleDrafts(), so seed only after the JOINED ack.
            // The renderer restores these at mount, ahead of the first submission.
            await student.evaluate(
                (drafts: Record<string, string>) => {
                    for (const [id, code] of Object.entries(drafts)) {
                        localStorage.setItem(`sprintjudge_code_${id}`, code);
                    }
                },
                {
                    [pyId]: "print(42)",
                    [javaId]: javaCorrect,
                    [loopId]: "while True: pass",
                },
            );
            await host.getByRole("button", { name: "Start round" }).click();

            // Correct Python: both hidden cases pass, visible in the banner.
            await expect(student.getByRole("heading", { name: questions[0].title })).toBeVisible({
                timeout: 15_000,
            });
            await expect(student.locator(".renderer-host select")).toHaveValue("python");
            await expect(student.getByRole("button", { name: "Compile and submit" })).toBeEnabled();
            await student.getByRole("button", { name: "Compile and submit" }).click();
            await expect(student.locator(".feedback-banner")).toContainText("2/2 tests passed", {
                timeout: 25_000,
            });

            await host.getByRole("button", { name: "Reveal answer" }).click();
            await expect(student.getByText("Round complete")).toBeVisible({ timeout: 10_000 });
            await host.getByRole("button", { name: "Next question" }).click();
            await expect(student.getByRole("heading", { name: questions[1].title })).toBeVisible({
                timeout: 10_000,
            });

            // Switch to Java on a Python-default question: the same correct
            // program in a different language must still pass both cases.
            const langSelect = student.locator(".renderer-host select");
            // The renderer emits only on select change. A fresh emit after the
            // question-change reset is what puts {source, language} in the frame.
            await langSelect.selectOption("node");
            await expect(langSelect).toHaveValue("node");
            await langSelect.selectOption("java");
            await expect(langSelect).toHaveValue("java");
            expect(
                await student.evaluate(
                    (id) => localStorage.getItem(`sprintjudge_code_${id}`),
                    javaId,
                ),
                "cached Java draft survived Q1's ack",
            ).toBe(javaCorrect);

            await student.getByRole("button", { name: "Compile and submit" }).click();
            const javaSubmits = submits.filter((f) => f.questionId === javaId);
            expect(javaSubmits.length, "one SUBMIT for the Java question").toBe(1);
            expect(javaSubmits[0].language, "SUBMIT language").toBe("java");
            expect(javaSubmits[0].response?.source, "SUBMIT source").toBe(javaCorrect);
            await expect(student.locator(".feedback-banner")).toContainText("2/2 tests passed", {
                timeout: 25_000,
            });

            await host.getByRole("button", { name: "Reveal answer" }).click();
            await expect(student.getByText("Round complete")).toBeVisible({ timeout: 10_000 });
            await host.getByRole("button", { name: "Next question" }).click();
            await expect(student.getByRole("heading", { name: questions[2].title })).toBeVisible({
                timeout: 10_000,
            });

            // Live console: the infinite loop is killed by the server timeout,
            // the run button re-enables and the page still accepts input.
            const runBtn = student.getByRole("button", { name: /▶\s*Run/ });
            await expect(runBtn).toBeEnabled();
            const runResponse = student.waitForResponse(
                (r) => r.url().includes("/api/public/run") && r.request().method() === "POST",
                { timeout: 20_000 },
            );
            await runBtn.click();
            await expect(runBtn).toBeDisabled();
            const response = await runResponse;
            expect(response.status()).toBe(200);
            const runResult = (await response.json()) as RunResult;
            expect(runResult.status, "console infinite loop").toBe("timeout");
            await expect(runBtn).toBeEnabled({ timeout: 15_000 });
            await student.locator(".renderer-host select").selectOption("node");
            await expect(student.locator(".renderer-host select")).toHaveValue("node");
        } finally {
            await discardQuiz(admin, quizId);
            await host.close();
            await student.close();
        }
    });
});
