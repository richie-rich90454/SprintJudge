import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

// Form login is served by the backend, not the Vite dev server. The admin a11y
// tests authenticate directly against it, then scan the backend-served SPA.
const BACKEND = process.env.SPRINTJUDGE_BACKEND_URL ?? "http://127.0.0.1:8080";
const ADMIN_PASSWORD = process.env.SPRINTJUDGE_ADMIN_PASSWORD ?? "changeme";

const VIEWPORTS = [
    { name: "desktop", width: 1440, height: 900 },
    { name: "mobile", width: 390, height: 844 },
] as const;

async function createPracticePin(request: any): Promise<string> {
    const start = await request.post("/api/public/practice", { data: {} });
    expect(start.ok()).toBeTruthy();
    const { pinCode } = (await start.json()) as { pinCode: string };
    return pinCode;
}

for (const vp of VIEWPORTS) {
    test.describe(`screens @ ${vp.name}`, () => {
        test.use({ viewport: { width: vp.width, height: vp.height } });

        test("landing", async ({ page }) => {
            await page.goto("/");
            await page.waitForLoadState("networkidle");
            await expect(page).toHaveScreenshot(`landing-${vp.name}.png`, {
                maxDiffPixelRatio: 0.02,
                animations: "disabled",
            });
        });

        test("join", async ({ page }) => {
            await page.goto("/join");
            await page.waitForLoadState("networkidle");
            await expect(page).toHaveScreenshot(`join-${vp.name}.png`, {
                maxDiffPixelRatio: 0.02,
                animations: "disabled",
            });
        });

        test("practice", async ({ page }) => {
            await page.goto("/practice");
            await page.waitForLoadState("networkidle");
            await expect(page).toHaveScreenshot(`practice-${vp.name}.png`, {
                // The bank list is seeded data and varies between fresh DBs,
                // so this data-driven view needs a looser pixel tolerance.
                maxDiffPixelRatio: 0.06,
                animations: "disabled",
            });
        });

        test("host", async ({ page, request }) => {
            const pin = await createPracticePin(request);
            await page.goto(`/host?pin=${pin}`);
            await page.waitForLoadState("networkidle");
            await page.waitForTimeout(1500);
            await expect(page).toHaveScreenshot(`host-${vp.name}.png`, {
                maxDiffPixelRatio: 0.02,
                animations: "disabled",
            });
        });

        test("results", async ({ page }) => {
            await page.goto("/results");
            await page.waitForLoadState("networkidle");
            await expect(page).toHaveScreenshot(`results-${vp.name}.png`, {
                maxDiffPixelRatio: 0.02,
                animations: "disabled",
            });
        });

        test("admin login", async ({ page }) => {
            await page.goto("/admin/login");
            await page.waitForLoadState("networkidle");
            await expect(page).toHaveScreenshot(`admin-login-${vp.name}.png`, {
                maxDiffPixelRatio: 0.02,
                animations: "disabled",
            });
        });
    });
}

test.describe("a11y", () => {
    test("landing has no serious/critical a11y violations", async ({ page }) => {
        await page.goto("/");
        await page.waitForLoadState("networkidle");
        const results = await new AxeBuilder({ page }).analyze();
        const bad = results.violations.filter(
            (v) => v.impact === "serious" || v.impact === "critical",
        );
        expect(bad).toEqual([]);
    });

    test("join has no serious/critical a11y violations", async ({ page }) => {
        await page.goto("/join");
        await page.waitForLoadState("networkidle");
        const results = await new AxeBuilder({ page }).analyze();
        const bad = results.violations.filter(
            (v) => v.impact === "serious" || v.impact === "critical",
        );
        expect(bad).toEqual([]);
    });

    test("host has no serious/critical a11y violations", async ({ page, request }) => {
        const pin = await createPracticePin(request);
        await page.goto(`/host?pin=${pin}`);
        await page.waitForLoadState("networkidle");
        await page.waitForTimeout(1500);
        const results = await new AxeBuilder({ page }).analyze();
        const bad = results.violations.filter(
            (v) => v.impact === "serious" || v.impact === "critical",
        );
        expect(bad).toEqual([]);
    });

    test("results has no serious/critical a11y violations", async ({ page }) => {
        await page.goto("/results");
        await page.waitForLoadState("networkidle");
        const results = await new AxeBuilder({ page }).analyze();
        const bad = results.violations.filter(
            (v) => v.impact === "serious" || v.impact === "critical",
        );
        expect(bad).toEqual([]);
    });

    test("admin login has no serious/critical a11y violations", async ({ page }) => {
        await page.goto("/admin/login");
        await page.waitForLoadState("networkidle");
        const results = await new AxeBuilder({ page }).analyze();
        const bad = results.violations.filter(
            (v) => v.impact === "serious" || v.impact === "critical",
        );
        expect(bad).toEqual([]);
    });

    test("solo has no serious/critical a11y violations", async ({ page }) => {
        await page.goto("/solo");
        await page.waitForLoadState("networkidle");
        const results = await new AxeBuilder({ page }).analyze();
        const bad = results.violations.filter(
            (v) => v.impact === "serious" || v.impact === "critical",
        );
        expect(bad).toEqual([]);
    });

    test("explore has no serious/critical a11y violations", async ({ page }) => {
        await page.goto("/explore");
        await page.waitForLoadState("networkidle");
        const results = await new AxeBuilder({ page }).analyze();
        const bad = results.violations.filter(
            (v) => v.impact === "serious" || v.impact === "critical",
        );
        expect(bad).toEqual([]);
    });

    async function loginAsAdmin(page: import("@playwright/test").Page) {
        await page.goto(`${BACKEND}/admin/login`);
        await page.fill('input[name="username"]', "admin");
        await page.fill('input[name="password"]', ADMIN_PASSWORD);
        await page.click('button[type="submit"]');
        await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 15000 });
    }

    test("admin bank has no serious/critical a11y violations", async ({ page }) => {
        await loginAsAdmin(page);
        await page.goto(`${BACKEND}/admin/bank`);
        await page.waitForLoadState("networkidle");
        const results = await new AxeBuilder({ page }).analyze();
        const bad = results.violations.filter(
            (v) => v.impact === "serious" || v.impact === "critical",
        );
        expect(bad).toEqual([]);
    });

    test("admin volume has no serious/critical a11y violations", async ({ page }) => {
        await loginAsAdmin(page);
        await page.goto(`${BACKEND}/admin/volume`);
        await page.waitForLoadState("networkidle");
        const results = await new AxeBuilder({ page }).analyze();
        const bad = results.violations.filter(
            (v) => v.impact === "serious" || v.impact === "critical",
        );
        expect(bad).toEqual([]);
    });

    test("watch has no serious/critical a11y violations", async ({ page, request }) => {
        const pin = await createPracticePin(request);
        await page.goto(`/watch/${pin}`);
        await page.waitForLoadState("networkidle");
        const results = await new AxeBuilder({ page }).analyze();
        const bad = results.violations.filter(
            (v) => v.impact === "serious" || v.impact === "critical",
        );
        expect(bad).toEqual([]);
    });
});

test.describe("reduced motion", () => {
    test("applies motion-reduced class under prefers-reduced-motion", async ({ page }) => {
        await page.emulateMedia({ reducedMotion: "reduce" });
        await page.goto("/");
        await page.waitForLoadState("networkidle");
        await expect(page.locator("html")).toHaveClass(/motion-reduced/);
    });
});