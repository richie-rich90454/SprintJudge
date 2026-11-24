import { test, expect } from "@playwright/test";

// The PWA service worker claims the page and reloads on controllerchange,
// cancelling the form POST before it reaches Spring Security.
test.use({ serviceWorkers: "block" });

/**
 * Regression guard for the CSA admin flow, run against the backend-served SPA
 * (the production path). A Spring Security 7 XOR CSRF handler once rejected the
 * raw XSRF-TOKEN cookie that axios echoes, so every admin POST returned 403;
 * Jackson 3 also rejected DTOs with omitted primitive fields with 400.
 *
 * Requires a running backend with a seeded library. In CI the backend is started
 * with SPRINTJUDGE_ADMIN_PASSWORD=ci-admin-password. Set SPRINTJUDGE_BACKEND_URL
 * to override the target.
 */
const ADMIN_PASSWORD = process.env.SPRINTJUDGE_ADMIN_PASSWORD ?? "ci-admin-password";
const BACKEND = process.env.SPRINTJUDGE_BACKEND_URL ?? "http://127.0.0.1:8080";

test.describe("admin flows", () => {
    test("sign in and build a CSA class from the dashboard", async ({ page }) => {
        await page.goto(`${BACKEND}/admin/login`);
        await page.locator('input[name="username"]').fill("admin");
        await page.locator('input[name="password"]').fill(ADMIN_PASSWORD);
        await page.locator('button[type="submit"]').click();
        await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 15000 });
        await expect(page.getByText("Start a CSA class")).toBeVisible({ timeout: 15000 });

        const posts: number[] = [];
        page.on("response", (r) => {
            if (r.request().method() === "POST" && r.url().includes("/api/admin/")) posts.push(r.status());
        });

        await page.locator("button").filter({ hasText: "Using Objects and Methods" }).first().click();

        await expect(page).toHaveURL(/\/host\?pin=\d{6}/, { timeout: 30000 });
        await expect(page.getByText(/Could not build the CSA game/)).toHaveCount(0);
        expect(posts.length).toBeGreaterThanOrEqual(2);
        expect(posts.every((s) => s >= 200 && s < 300)).toBeTruthy();
    });
});
