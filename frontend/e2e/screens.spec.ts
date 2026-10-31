import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

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
                maxDiffPixelRatio: 0.02,
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
});

test.describe("reduced motion", () => {
    test("applies motion-reduced class under prefers-reduced-motion", async ({ page }) => {
        await page.emulateMedia({ reducedMotion: "reduce" });
        await page.goto("/");
        await page.waitForLoadState("networkidle");
        await expect(page.locator("html")).toHaveClass(/motion-reduced/);
    });
});