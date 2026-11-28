import { expect, test, type BrowserContext, type Page } from "@playwright/test";
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

/*
 * Exploratory host-control E2E against the CI-equivalent backend.
 *
 * Assumptions / gaps discovered while reading the real UI (frontend/src/screens/HostView.tsx
 * and backend GameWebSocket.java), recorded rather than papered over:
 *  - "practice room" here means an admin-created, host-led STANDARD room. A PRACTICE-mode
 *    room (POST /api/public/practice) auto-starts and auto-advances and has no host seat,
 *    so none of the controls below exist there.
 *  - There is no pause/resume host command in this build. The only input gate is the
 *    ACTIVE -> REVIEW transition, so gating is asserted across reveal/timer-review.
 *  - There is no dedicated Skip button, host-transfer or host-revoke UI. The real skip
 *    path is a NEXT_QUESTION frame sent while ACTIVE; host authority is verified by the
 *    server's host-only guard (a student's command is rejected) and KICK_PLAYER is the
 *    only seat-revocation control. Those frames are sent over the page's own socket.
 */
const ADA = "Ada";
const GRACE = "Grace";
const OPTIONS = ["var", "final", "static", "let"];
const EXPLANATION =
    "A final variable cannot be reassigned, which is how Java expresses a constant.";

function mcq(id: string, title: string, correctIndex: number, timeLimitSec: number): SeedQuestion {
    return {
        id,
        title,
        questionType: "MCQ",
        config: { options: OPTIONS, correctIndex, explanation: EXPLANATION },
        timeLimitSec,
        pointsBase: 100,
    };
}

interface WsRec {
    type: string;
    message?: unknown;
}

/** Capture each page's real socket + inbound frames so unsupported-by-UI host
 * commands and server-side rejections can be driven/observed in a real browser. */
async function instrumentSocket(page: Page): Promise<void> {
    await page.addInitScript(() => {
        const NativeSocket = window.WebSocket;
        const state = {
            sockets: [] as WebSocket[],
            inbound: [] as { type: string; message?: unknown }[],
        };
        (window as unknown as { __oqWs: typeof state }).__oqWs = state;
        (window as unknown as { WebSocket: typeof WebSocket }).WebSocket =
            class extends NativeSocket {
                constructor(url: string | URL, protocols?: string | string[]) {
                    super(url, protocols);
                    state.sockets.push(this);
                    this.addEventListener("message", (ev: MessageEvent<string>) => {
                        try {
                            state.inbound.push(JSON.parse(ev.data));
                        } catch {
                            /* ignore non-JSON frames */
                        }
                    });
                }
            } as typeof WebSocket;
    });
}

async function rawSend(page: Page, msg: Record<string, unknown>): Promise<void> {
    await page.evaluate((frame) => {
        const state = (window as unknown as { __oqWs: { sockets: WebSocket[] } }).__oqWs;
        const socket = state.sockets.filter((s) => s.readyState === WebSocket.OPEN).at(-1);
        if (!socket) throw new Error("no open WebSocket to send on");
        socket.send(JSON.stringify(frame));
    }, msg);
}

async function inbound(page: Page): Promise<WsRec[]> {
    return page.evaluate(
        () => (window as unknown as { __oqWs: { inbound: WsRec[] } }).__oqWs.inbound,
    );
}

async function secondsLeft(page: Page): Promise<number> {
    const label = await page.getByRole("timer").getAttribute("aria-label", { timeout: 5000 });
    const match = /(\d+) seconds remaining/.exec(label ?? "");
    return match ? Number(match[1]) : -1;
}

async function scoreOf(host: Page, name: string): Promise<number> {
    const row = host.locator(".lb-row").filter({ hasText: name });
    await expect(row).toBeVisible();
    return Number((await row.locator("span").last().innerText()).replace(/,/g, ""));
}

async function openHost(context: BrowserContext, pin: string): Promise<Page> {
    const host = await context.newPage();
    await instrumentSocket(host);
    await host.goto(`${BACKEND}/host?pin=${pin}`);
    await expect(host.getByRole("button", { name: "Start round" })).toBeVisible({ timeout: 15000 });
    return host;
}

async function joinStudent(context: BrowserContext, pin: string, name: string): Promise<Page> {
    const student = await context.newPage();
    await instrumentSocket(student);
    await student.goto(`/j/${pin}`);
    await student.getByLabel("Your name").fill(name);
    await student.getByRole("button", { name: "Join game" }).click();
    await expect(student).toHaveURL(/\/play/, { timeout: 15000 });
    await expect(student.getByText("Waiting for the host to start the next question.")).toBeVisible(
        {
            timeout: 15000,
        },
    );
    return student;
}

test("host controls drive a room end to end", async ({ page: admin, context }) => {
    test.setTimeout(165_000);
    const tag = Date.now().toString(36);
    const q1 = `${tag}-q1`;
    const q2 = `${tag}-q2`;
    const q3 = `${tag}-q3`;
    const q1Title = "Q1 Which keyword declares a constant?";
    const q2Title = "Q2 Which keyword declares a class?";
    const q3Title = "Q3 Which keyword makes a value constant?";

    await loginAdmin(admin);
    const quizId = await seedQuiz(admin, [
        mcq(q1, q1Title, 1, 8),
        mcq(q2, q2Title, 2, 60),
        mcq(q3, q3Title, 1, 60),
    ]);
    const pin = await hostGame(admin, quizId, "STANDARD");
    const host = await openHost(context, pin);
    let ada: Page | undefined;
    let grace: Page | undefined;
    try {
        await test.step("create room, join two distinct students, verify roster and connection", async () => {
            ada = await joinStudent(context, pin, ADA);
            grace = await joinStudent(context, pin, GRACE);
            await expect(host.getByText("2 students", { exact: true }).first()).toBeVisible({
                timeout: 10000,
            });
            await expect(host.getByRole("button", { name: `Kick ${ADA}` })).toBeVisible();
            await expect(host.getByRole("button", { name: `Kick ${GRACE}` })).toBeVisible();
            // Both seats are live: each student is on the play screen, not an error page.
            await expect(ada.getByText("No game", { exact: true })).toHaveCount(0);
            await expect(grace.getByText("No game", { exact: true })).toHaveCount(0);
        });

        await test.step("extend timer: both students see the extra time and cannot answer after the extended deadline", async () => {
            await host.getByRole("button", { name: "Start round" }).click();
            await expect(ada!.getByRole("heading", { name: q1Title })).toBeVisible({
                timeout: 15000,
            });
            await expect(grace!.getByRole("heading", { name: q1Title })).toBeVisible({
                timeout: 15000,
            });

            const adaBefore = await secondsLeft(ada!);
            const graceBefore = await secondsLeft(grace!);
            expect(adaBefore).toBeGreaterThan(0);
            expect(graceBefore).toBeGreaterThan(0);
            await host.getByRole("button", { name: "+30 seconds" }).click();
            await expect
                .poll(() => secondsLeft(ada!), { timeout: 6000 })
                .toBeGreaterThan(adaBefore + 20);
            await expect
                .poll(() => secondsLeft(grace!), { timeout: 6000 })
                .toBeGreaterThan(graceBefore + 20);

            // One student scores before the extended deadline; the other stays silent.
            await ada!.locator(".renderer-host").getByRole("button", { name: /final/i }).click();
            await ada!.getByRole("button", { name: "Lock in answer" }).click();
            await expect(ada!.locator(".feedback-banner")).toContainText("pts", { timeout: 10000 });

            // Original 8s + 30s extension: wait out the *extended* deadline.
            await expect(ada!.getByText("Round complete")).toBeVisible({ timeout: 55000 });
            await expect(grace!.getByText("Round complete")).toBeVisible({ timeout: 5000 });
            // Inputs are gated once the round is over.
            await expect(ada!.getByRole("button", { name: "Lock in answer" })).toHaveCount(0);
            await expect(grace!.getByRole("button", { name: "Lock in answer" })).toHaveCount(0);
            // A frame sent after the deadline is refused by the server gate.
            await rawSend(ada!, { type: "SUBMIT", questionId: q1, response: { selectedIndex: 1 } });
            await expect
                .poll(
                    async () =>
                        (await inbound(ada!)).some(
                            (m) => m.type === "ERROR" && /Round is locked/i.test(String(m.message)),
                        ),
                    { timeout: 8000 },
                )
                .toBe(true);
            await expect(ada!.getByText(EXPLANATION)).toBeVisible();

            expect(await scoreOf(host, ADA)).toBeGreaterThan(0);
            expect(await scoreOf(host, GRACE)).toBe(0);
        });

        await test.step("skip a question: all clients advance together and the skipped round is not scored", async () => {
            const adaScore = await scoreOf(host, ADA);
            const graceScore = await scoreOf(host, GRACE);
            await host.getByRole("button", { name: "Next question" }).click();
            await expect(ada!.getByRole("heading", { name: q2Title })).toBeVisible({
                timeout: 10000,
            });
            await expect(grace!.getByRole("heading", { name: q2Title })).toBeVisible({
                timeout: 10000,
            });

            // No Skip button exists while ACTIVE; the supported skip is the host's
            // NEXT_QUESTION frame sent on the authenticated host socket.
            await rawSend(host, { type: "NEXT_QUESTION" });
            await expect(ada!.getByRole("heading", { name: q3Title })).toBeVisible({
                timeout: 12000,
            });
            await expect(grace!.getByRole("heading", { name: q3Title })).toBeVisible({
                timeout: 12000,
            });
            await expect.poll(() => scoreOf(host, ADA), { timeout: 8000 }).toBe(adaScore);
            await expect.poll(() => scoreOf(host, GRACE), { timeout: 8000 }).toBe(graceScore);
        });

        await test.step("reveal answer: correct-answer highlighting, explanation and per-student score", async () => {
            await ada!.locator(".renderer-host").getByRole("button", { name: /final/i }).click();
            await ada!.getByRole("button", { name: "Lock in answer" }).click();
            await expect(ada!.locator(".feedback-banner")).toContainText("pts", { timeout: 10000 });
            await grace!.locator(".renderer-host").getByRole("button", { name: /var/i }).click();
            await grace!.getByRole("button", { name: "Lock in answer" }).click();
            await expect(grace!.locator(".feedback-banner")).toContainText("Not quite", {
                timeout: 10000,
            });

            await host.getByRole("button", { name: "Reveal answer" }).click();
            await expect(ada!.getByText("Round complete")).toBeVisible({ timeout: 10000 });
            await expect(grace!.getByText("Round complete")).toBeVisible({ timeout: 10000 });
            await expect(host.locator(".reveal-correct")).toContainText("Correct answer");
            await expect(host.locator(".reveal-correct")).toContainText("final");
            await expect(ada!.getByText(EXPLANATION)).toBeVisible();
            await expect(grace!.getByText(EXPLANATION)).toBeVisible();
            await expect(
                ada!.locator(".stage .grid > div").filter({ hasText: "final" }),
            ).toHaveClass(/color-mix/);
            expect(await scoreOf(host, ADA)).toBeGreaterThan(await scoreOf(host, GRACE));
        });

        await test.step("host authority: student command is refused; host can revoke a seat", async () => {
            await rawSend(grace!, { type: "NEXT_QUESTION" });
            await expect
                .poll(
                    async () =>
                        (await inbound(grace!)).some(
                            (m) => m.type === "ERROR" && /Forbidden/i.test(String(m.message)),
                        ),
                    { timeout: 8000 },
                )
                .toBe(true);
            // The refused command changed nothing: host still controls the room.
            await expect(host.getByRole("button", { name: "Next question" })).toBeVisible();
            await expect(ada!.getByText("Round complete")).toBeVisible();

            await host.getByRole("button", { name: `Kick ${GRACE}` }).click();
            await expect(host.getByRole("button", { name: `Kick ${GRACE}` })).toHaveCount(0, {
                timeout: 8000,
            });
            await expect(host.getByText("1 student", { exact: true }).first()).toBeVisible();
            await expect
                .poll(
                    async () =>
                        (await inbound(grace!)).some(
                            (m) =>
                                m.type === "ERROR" &&
                                /removed by the host/i.test(String(m.message)),
                        ),
                    { timeout: 8000 },
                )
                .toBe(true);
            await grace!.close();
            grace = undefined;
        });

        await test.step("end game: results screen and room removed", async () => {
            await host.getByRole("button", { name: "End game" }).click();
            await expect(host.getByRole("dialog")).toBeVisible();
            await host.getByRole("dialog").getByRole("button", { name: "End game" }).click();
            await expect(host.getByText("Game over", { exact: true })).toBeVisible({
                timeout: 10000,
            });
            await expect(host.getByRole("link", { name: "View results" })).toBeVisible();
            await expect(host.getByRole("button", { name: "End game" })).toHaveCount(0);

            await expect(ada!).toHaveURL(/\/results/, { timeout: 15000 });
            await expect(ada!.getByText("Results", { exact: true })).toBeVisible();
            await expect(ada!.getByText(ADA, { exact: true }).first()).toBeVisible();

            const late = await context.newPage();
            try {
                await late.goto(`/j/${pin}`);
                await late.getByLabel("Your name").fill("Latecomer");
                await late.getByRole("button", { name: "Join game" }).click();
                await expect(late.getByRole("alert")).toContainText("This game has ended.", {
                    timeout: 12000,
                });
            } finally {
                await late.close();
            }
        });
    } finally {
        await discardQuiz(admin, quizId);
        await host.close();
        await ada?.close();
        await grace?.close();
    }
});
