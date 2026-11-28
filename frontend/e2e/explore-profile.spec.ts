import { expect, test, type Page } from "@playwright/test";
import {
    BACKEND,
    adminCall,
    discardQuiz,
    loginAdmin,
    practicePin,
    seedQuiz,
} from "./explore-player-helper";

// The PWA service worker reloads on controllerchange and cancels profile POSTs.
test.use({ serviceWorkers: "block" });

const ID_KEY = "player_profile_id";
const TOKEN_KEY = "player_profile_token";

interface ProfileReport {
    profile: { id: string; nickname: string; linkToken: string };
    stats: { sessions: number; totalScore: number; answers: number; correct: number };
    history: Array<{ score: number; quizTitle: string | null }>;
}

interface ApiResult {
    status: number;
    json: any;
}

/** Relative path: the Vite proxy forwards /api to the CI backend on :8080. */
function profileApi(
    page: Page,
    path: string,
    method: "GET" | "POST" | "PATCH" | "DELETE",
    body?: unknown,
    token?: string,
): Promise<ApiResult> {
    return page.evaluate(
        async ({ path, method, body, token }) => {
            const response = await fetch(`/api/public/profiles${path}`, {
                method,
                headers: {
                    "Content-Type": "application/json",
                    ...(token ? { Authorization: `Bearer ${token}` } : {}),
                },
                body: body === undefined ? undefined : JSON.stringify(body),
            });
            const text = await response.text();
            let json: unknown = null;
            try {
                json = JSON.parse(text);
            } catch {
                // Empty or non-JSON bodies (e.g. a 405 page) stay null.
            }
            return { status: response.status, json };
        },
        { path, method, body, token },
    );
}

async function storedIdentity(page: Page): Promise<{ id: string | null; token: string | null }> {
    return page.evaluate(
        (keys) => ({
            id: localStorage.getItem(keys.id),
            token: localStorage.getItem(keys.token),
        }),
        { id: ID_KEY, token: TOKEN_KEY },
    );
}

async function clearIdentity(page: Page): Promise<void> {
    await page.evaluate(
        (keys) => {
            localStorage.removeItem(keys.id);
            localStorage.removeItem(keys.token);
        },
        { id: ID_KEY, token: TOKEN_KEY },
    );
}

async function setIdentity(page: Page, id: string, token: string): Promise<void> {
    await page.evaluate(
        (args) => {
            localStorage.setItem(args.idKey, args.id);
            localStorage.setItem(args.tokenKey, args.token);
        },
        { idKey: ID_KEY, tokenKey: TOKEN_KEY, id, token },
    );
}

/** The sound label is localized, so it stays Arabic after this spec sets locale=ar. */
const unmuteButton = (page: Page) =>
    page.getByRole("button", { name: /^(unmute sounds|تشغيل الأصوات)$/i });

async function openFreshProfile(page: Page): Promise<void> {
    await page.goto("/profile");
    await clearIdentity(page);
    await page.reload();
    await expect(page.getByRole("heading", { name: "Create a profile" })).toBeVisible();
}

async function createProfileViaUi(page: Page, nickname: string): Promise<ProfileReport> {
    await page.getByLabel("Nickname").fill(nickname);
    await page.getByRole("button", { name: "Create profile", exact: true }).click();
    await expect(page.getByRole("heading", { name: nickname })).toBeVisible({ timeout: 10_000 });
    const { id, token } = await storedIdentity(page);
    expect(id).toBeTruthy();
    expect(token).toHaveLength(64);
    return (await profileApi(page, `/${encodeURIComponent(id!)}`, "GET", undefined, token!)).json;
}

/** The JOINED frame carries the capability pair needed to claim a game identity. */
async function captureJoinedIdentity(page: Page): Promise<void> {
    await page.addInitScript(() => {
        const w = window as unknown as {
            __oqJoined: Array<{ uuid: string; rejoinToken: string }>;
        };
        w.__oqJoined = [];
        const NativeSocket = window.WebSocket;
        (window as unknown as { WebSocket: typeof WebSocket }).WebSocket =
            class extends NativeSocket {
                constructor(url: string | URL, protocols?: string | string[]) {
                    super(url, protocols);
                    this.addEventListener("message", (event: MessageEvent<string>) => {
                        try {
                            const msg = JSON.parse(event.data) as {
                                type?: string;
                                uuid?: string;
                                rejoinToken?: string;
                            };
                            if (msg.type === "JOINED" && msg.uuid && msg.rejoinToken) {
                                w.__oqJoined.push({ uuid: msg.uuid, rejoinToken: msg.rejoinToken });
                            }
                        } catch {
                            // Ignore non-JSON frames.
                        }
                    });
                }
            } as typeof WebSocket;
    });
}

function joinedIdentity(page: Page): Promise<{ uuid: string; rejoinToken: string }> {
    return page.evaluate(() => {
        const w = window as unknown as {
            __oqJoined: Array<{ uuid: string; rejoinToken: string }>;
        };
        return w.__oqJoined[0];
    });
}

test.describe("explore profiles and settings", () => {
    test("create and rename a profile; device credential storage acts as the picker", async ({
        page,
    }) => {
        test.setTimeout(30_000);
        const tag = Date.now().toString(36).slice(-6);
        const first = `Probe${tag}`;
        const renamed = `Renamed${tag}`;

        await openFreshProfile(page);
        await createProfileViaUi(page, first);

        // Rename is a real PATCH; the heading and the stored report must agree.
        await page.getByLabel("Nickname").fill(renamed);
        await page.getByRole("button", { name: "Save", exact: true }).click();
        await expect(page.getByRole("heading", { name: renamed })).toBeVisible({ timeout: 10_000 });

        await page.reload();
        await expect(page.getByRole("heading", { name: renamed })).toBeVisible();
        await expect(page.getByText("0 quizzes | 0 points | 0/0 correct")).toBeVisible();
        const identity = await storedIdentity(page);
        const report: ProfileReport = (
            await profileApi(page, `/${identity.id}`, "GET", undefined, identity.token!)
        ).json;
        expect(report.profile.nickname).toBe(renamed);

        // "Forget this device" drops the local credential, so the picker-equivalent
        // create form returns; restoring the same id/token selects the profile again.
        await page.getByRole("button", { name: "Forget this device" }).click();
        await expect(page.getByRole("heading", { name: "Create a profile" })).toBeVisible();
        expect((await storedIdentity(page)).id).toBeNull();
        await setIdentity(page, identity.id!, identity.token!);
        await page.reload();
        await expect(page.getByRole("heading", { name: renamed })).toBeVisible();
    });

    test("switching the active profile scopes game score to that profile", async ({
        context,
        request,
    }) => {
        test.setTimeout(60_000);
        const tag = Date.now().toString(36).slice(-6);
        const admin = await context.newPage();
        await loginAdmin(admin);
        const quizId = await seedQuiz(admin, [
            {
                id: `e2e-profile-${tag}`,
                title: "Which keyword declares a constant in Java?",
                questionType: "MCQ",
                config: { options: ["var", "final", "static", "let"], correctIndex: 1 },
                timeLimitSec: 60,
                pointsBase: 100,
            },
        ]);
        try {
            const pin = await practicePin(request, quizId);
            const player = await context.newPage();
            await captureJoinedIdentity(player);
            await player.goto(`/j/${pin}`);
            await player.getByLabel("Your name").fill(`Scoped${tag}`);
            await player.getByRole("button", { name: "Join game" }).click();
            await expect(
                player.getByRole("heading", { name: "Which keyword declares a constant in Java?" }),
            ).toBeVisible({ timeout: 15_000 });

            const profile = await context.newPage();
            await openFreshProfile(profile);
            const profileA = await createProfileViaUi(profile, `Owner${tag}`);
            await expect.poll(async () => joinedIdentity(player), { timeout: 10_000 }).toBeTruthy();
            const gameIdentity = await joinedIdentity(player);
            const linked = await profileApi(
                profile,
                `/${profileA.profile.id}`,
                "PATCH",
                gameIdentity,
                profileA.profile.linkToken,
            );
            expect(linked.status).toBe(200);
            expect(linked.json.profile.playerUuid).toBe(gameIdentity.uuid);

            // Score the live game while profile A is active.
            await player.locator(".renderer-host").getByRole("button", { name: /final/i }).click();
            await player.getByRole("button", { name: "Lock in answer" }).click();
            await expect(player.getByText(/pts/)).toBeVisible({ timeout: 10_000 });

            // Speed-decay makes the exact award dynamic; read it from the report
            // and compare it to what the profile UI renders.
            const readScore = async () =>
                (
                    await profileApi(
                        profile,
                        `/${profileA.profile.id}`,
                        "GET",
                        undefined,
                        profileA.profile.linkToken,
                    )
                ).json as ProfileReport;
            await expect
                .poll(async () => (await readScore()).stats.totalScore, { timeout: 10_000 })
                .toBeGreaterThan(0);
            const scored = (await readScore()).stats.totalScore;

            await profile.reload();
            await expect(profile.getByRole("heading", { name: `Owner${tag}` })).toBeVisible();
            await expect(profile.getByText(/quiz(?:zes)? \| .* points \|/)).toContainText(
                `1 quiz | ${scored} points | 1/1 correct`,
            );
            await expect(profile.getByText(`${scored} pts`, { exact: true })).toBeVisible();

            // Switch to a second profile mid-session: the game tab stays live,
            // but B's history is empty and A's score is not visible.
            const createdB = await profileApi(profile, "", "POST", { nickname: `Other${tag}` });
            expect(createdB.status).toBe(201);
            const b: ProfileReport = createdB.json;
            await setIdentity(profile, b.profile.id, b.profile.linkToken);
            await profile.reload();
            await expect(profile.getByRole("heading", { name: `Other${tag}` })).toBeVisible();
            await expect(profile.getByText("No results linked yet")).toBeVisible();
            await expect(profile.getByText(`${scored} pts`, { exact: true })).toHaveCount(0);
            await expect(
                player.getByRole("heading", { name: "Which keyword declares a constant in Java?" }),
            ).toBeVisible();
            expect(b.stats.sessions).toBe(0);

            await setIdentity(profile, profileA.profile.id, profileA.profile.linkToken);
            await profile.reload();
            await expect(profile.getByText(`${scored} pts`, { exact: true })).toBeVisible({
                timeout: 10_000,
            });
            await profile.close();
            await player.close();
        } finally {
            await discardQuiz(admin, quizId);
            await admin.close();
        }
    });

    test("display preferences persist by device, not by profile", async ({
        browser,
        page,
        context,
    }) => {
        test.setTimeout(60_000);
        const admin = await context.newPage();
        await loginAdmin(admin);
        const quizId = await seedQuiz(admin, [
            {
                id: `e2e-theme-${Date.now().toString(36).slice(-6)}`,
                title: "Theme probe",
                questionType: "MCQ",
                config: { options: ["a", "b"], correctIndex: 0 },
                timeLimitSec: 60,
                pointsBase: 100,
            },
        ]);
        try {
            const hostSession = await adminCall<{ pinCode: string }>(
                admin,
                "/api/admin/games",
                "POST",
                {
                    quizId,
                    gameMode: "STANDARD",
                },
            );
            const host = await context.newPage();
            await host.goto(`${BACKEND}/host?pin=${hostSession.pinCode}`);
            await expect(host.getByText("Stage theme")).toBeVisible({ timeout: 15_000 });

            await page.goto("/");
            await expect(page.getByRole("button", { name: "Mute sounds" })).toBeVisible();
            await expect(
                page.getByRole("button", { name: "Motion: system. Activate for full." }),
            ).toBeVisible();
            await page.getByRole("button", { name: "Mute sounds" }).click();
            await expect(unmuteButton(page)).toBeVisible();
            await page.getByRole("button", { name: "Motion: system. Activate for full." }).click();
            await page.getByRole("button", { name: "Motion: full. Activate for reduced." }).click();
            await expect(page.locator("html")).toHaveClass(/motion-reduced/);
            await page.locator("#oq-locale").selectOption("ar");
            await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
            await host.getByRole("button", { name: "Neon" }).click();
            await expect(host.getByRole("button", { name: "Neon" })).toHaveAttribute(
                "aria-pressed",
                "true",
            );

            const snapshot = async (target: Page) =>
                target.evaluate(() => ({
                    sound: localStorage.getItem("oq-sound"),
                    motion: localStorage.getItem("oq-motion"),
                    locale: localStorage.getItem("oq-locale"),
                    theme: localStorage.getItem("oq-theme"),
                    stage: localStorage.getItem("oq-stage-theme"),
                }));
            expect(await snapshot(page)).toMatchObject({
                sound: "off",
                motion: "reduced",
                locale: "ar",
            });
            expect((await snapshot(host)).stage).toBe("neon");

            await page.reload();
            await expect(unmuteButton(page)).toBeVisible();
            await expect(page.locator("html")).toHaveClass(/motion-reduced/);
            await expect(page.locator("html")).toHaveAttribute("lang", "ar");
            await host.reload();
            await expect(host.getByRole("button", { name: "Neon" })).toHaveAttribute(
                "aria-pressed",
                "true",
            );

            // A different device/profile starts with defaults; switching the
            // local profile credential on this device keeps its own preferences.
            const other = await browser.newContext({ serviceWorkers: "block" });
            try {
                const stranger = await other.newPage();
                await stranger.goto("/");
                await expect(stranger.getByRole("button", { name: "Mute sounds" })).toBeVisible();
                await expect(stranger.locator("html")).toHaveAttribute("lang", "en");
                expect(await snapshot(stranger)).toMatchObject({
                    sound: null,
                    motion: null,
                    locale: null,
                });
            } finally {
                await other.close();
            }
            await page.goto("/profile");
            await setIdentity(page, "bogus-id", "bogus-token");
            await page.reload();
            await expect(unmuteButton(page)).toBeVisible();
            expect((await snapshot(page)).motion).toBe("reduced");
            // Global theme is deliberately locked to light; only stage theme varies.
            expect((await snapshot(page)).theme ?? "light").toBe("light");
            await host.close();
        } finally {
            await discardQuiz(admin, quizId);
            await admin.close();
        }
    });

    test("admin settings survive a plain reload (no backend restart)", async ({ page }) => {
        test.setTimeout(30_000);
        await loginAdmin(page);
        const key = `e2eProfileProbe${Date.now().toString(36).slice(-6)}`;
        await adminCall(page, "/api/admin/settings", "PUT", { [key]: "before" });
        try {
            await page.goto(`${BACKEND}/admin`);
            await page.getByRole("tab", { name: "Settings" }).click();
            const input = page
                .locator("label")
                .filter({ hasText: key })
                .locator("xpath=following-sibling::input[1]");
            await expect(input).toHaveValue("before");
            await input.fill("after");
            await page.getByRole("button", { name: "Save", exact: true }).click();
            await expect(page.getByText("Saved!")).toBeVisible();

            // Only a browser reload is needed: settings are stored server-side.
            await page.reload();
            await page.getByRole("tab", { name: "Settings" }).click();
            await expect(
                page
                    .locator("label")
                    .filter({ hasText: key })
                    .locator("xpath=following-sibling::input[1]"),
            ).toHaveValue("after");
            const settings = await adminCall<Record<string, string>>(
                page,
                "/api/admin/settings",
                "GET",
            );
            expect(settings[key]).toBe("after");
        } finally {
            await adminCall(page, "/api/admin/settings", "PUT", { [key]: "before" });
        }
    });

    test("anonymous play works with no profile selected and creates no credential", async ({
        page,
        request,
    }) => {
        test.setTimeout(45_000);
        const admin = await page.context().newPage();
        await loginAdmin(admin);
        const quizId = await seedQuiz(admin, [
            {
                id: `e2e-anon-${Date.now().toString(36).slice(-6)}`,
                title: "Which keyword declares a constant in Java?",
                questionType: "MCQ",
                config: { options: ["var", "final", "static", "let"], correctIndex: 1 },
                timeLimitSec: 60,
                pointsBase: 100,
            },
        ]);
        try {
            const pin = await practicePin(request, quizId);
            await page.goto("/profile");
            await expect(page.getByRole("heading", { name: "Create a profile" })).toBeVisible();
            expect(await storedIdentity(page)).toEqual({ id: null, token: null });

            await page.goto("/solo");
            await expect(page.getByText("Enter your practice PIN")).toBeVisible();
            await page.getByLabel("Your name").fill(`Anon${Date.now().toString(36).slice(-5)}`);
            await page.getByLabel("Game PIN").fill(pin);
            await page.getByRole("button", { name: "Join game" }).click();
            await expect(
                page.getByRole("heading", { name: "Which keyword declares a constant in Java?" }),
            ).toBeVisible({ timeout: 15_000 });
            await page.locator(".renderer-host").getByRole("button", { name: /final/i }).click();
            await page.getByRole("button", { name: "Lock in answer" }).click();
            await expect(page.getByText(/pts/)).toBeVisible({ timeout: 10_000 });
            await expect(page).toHaveURL(/\/results/, { timeout: 20_000 });
            expect(await storedIdentity(page)).toEqual({ id: null, token: null });
        } finally {
            await discardQuiz(admin, quizId);
            await admin.close();
        }
    });

    test("profile deletion is unsupported; forget only clears this device", async ({ page }) => {
        test.setTimeout(30_000);
        await openFreshProfile(page);
        const report = await createProfileViaUi(
            page,
            `DeleteProbe${Date.now().toString(36).slice(-5)}`,
        );
        const { id, token } = await storedIdentity(page);

        // No destructive control to confirm and no DELETE route on the server.
        await expect(page.getByRole("button", { name: /delete (this )?profile/i })).toHaveCount(0);
        const removed = await profileApi(page, `/${id}`, "DELETE", undefined, token!);
        expect(removed.status).toBeGreaterThanOrEqual(400);
        expect(removed.status).toBeLessThan(500);

        await page.getByRole("button", { name: "Forget this device" }).click();
        await expect(page.getByRole("heading", { name: "Create a profile" })).toBeVisible();
        expect(await storedIdentity(page)).toEqual({ id: null, token: null });
        const stillThere = await profileApi(
            page,
            `/${report.profile.id}`,
            "GET",
            undefined,
            report.profile.linkToken,
        );
        expect(stillThere.status).toBe(200);
        expect(stillThere.json.profile.nickname).toBe(report.profile.nickname);
    });
});
