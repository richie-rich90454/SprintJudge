import { test, expect } from "@playwright/test";

// The PWA service worker can intercept/reload the page and mask router errors.
test.use({ serviceWorkers: "block" });

/**
 * Runtime probe for the adminGuard finding. Spring Security blocks a full page
 * load of /admin/** on the backend origin, so the SPA guard is exercised on the
 * Vite origin (CI boots Vite on :5173 proxied to the same CI-equivalent backend
 * on :8080). A 500 from /api/admin/settings is a transient failure: requireAdmin
 * must throw a plain Error (not redirect to login) and the route's error
 * component must render a usable retry prompt.
 */
test.describe("speculative adminGuard failure", () => {
    test("non-authenticated transient failure shows a usable error UI with Retry", async ({
        page,
    }) => {
        await page.route("**/api/admin/settings", (route) =>
            route.fulfill({ status: 500, contentType: "application/json", body: "{}" }),
        );
        // Fresh context => no admin session; this is the non-authenticated probe.
        await page.goto("/admin");

        const alert = page.getByRole("alert");
        await expect(alert).toBeVisible({ timeout: 15000 });
        await expect(
            page.getByRole("heading", { name: "Admin service unavailable" }),
        ).toBeVisible();
        await expect(
            page.getByText("The admin service is unavailable. Reload to try again."),
        ).toBeVisible();

        const retry = page.getByRole("button", { name: "Retry" });
        await expect(retry).toBeVisible();
        await expect(retry).toBeEnabled();

        // Retry must stay usable: re-running the failing guard keeps the error
        // UI, never a blank screen or a raw exception/stack trace.
        await retry.click();
        await expect(alert).toBeVisible();
        await expect(page.locator("body")).not.toContainText("AxiosError");
        await expect(page.locator("body")).not.toContainText("at requireAdmin");
    });
});
