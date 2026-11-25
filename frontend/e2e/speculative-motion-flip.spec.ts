import { test, expect } from "@playwright/test";

test.use({ serviceWorkers: "block" });

/**
 * Runtime probe for the OS motion preference finding. App.tsx listens for
 * matchMedia('(prefers-reduced-motion: reduce)') change events and toggles the
 * single .motion-reduced class on <html>. Flipping the emulated OS preference
 * must update that class in place, with no reload and no manual in-app toggle.
 */
test.describe("speculative OS motion preference flip", () => {
    test("flipping prefers-reduced-motion updates the motion class without reload", async ({ page }) => {
        // A fresh context has no persisted 'oq-motion' key, so motion is 'system';
        // the init script keeps it that way across any incidental navigation.
        await page.addInitScript(() => localStorage.removeItem("oq-motion"));
        await page.emulateMedia({ reducedMotion: "no-preference" });
        await page.goto("/");

        const html = page.locator("html");
        await expect(html).not.toHaveClass(/motion-reduced/);

        // Sentinel proves the class change came from the live listener, not a reload.
        await page.evaluate(() => {
            (window as unknown as { __oqNoReload?: string }).__oqNoReload = "alive";
        });

        await page.emulateMedia({ reducedMotion: "reduce" });
        await expect(html).toHaveClass(/motion-reduced/);

        await page.emulateMedia({ reducedMotion: "no-preference" });
        await expect(html).not.toHaveClass(/motion-reduced/);

        expect(await page.evaluate(() => (window as unknown as { __oqNoReload?: string }).__oqNoReload)).toBe("alive");
    });
});
