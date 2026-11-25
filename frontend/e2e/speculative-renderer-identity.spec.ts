import { test, expect } from "@playwright/test";

test.use({ serviceWorkers: "block" });

/**
 * Runtime probe for the renderer callback identity finding. The harness mounts
 * the real QuestionRendererHost with a FILL_BLANK renderer and a parent whose
 * inline onResponse closes over state, so each re-render passes a fresh
 * callback. Typing must not remount the renderer or wipe the typed input, and
 * the renderer must call the newest callback.
 */
test.describe("speculative renderer callback identity", () => {
    test("fresh inline onResponse does not remount the renderer or wipe input", async ({ page }) => {
        await page.goto("/e2e/speculative-renderer-harness.html");

        const input = page.locator(".renderer-host input");
        await expect(input).toBeVisible();

        // Node marker: a remount destroys the host's children and creates a
        // new input without the marker.
        await input.evaluate((el) => el.setAttribute("data-original", "1"));

        await input.fill("speculative-alpha");
        expect(await page.evaluate(() => (window as unknown as Probe).__oqResponse)).toEqual({
            text: "speculative-alpha",
        });

        await page.getByTestId("rerender").click();
        await expect(page.getByTestId("generation")).toHaveText("1");

        await expect(input).toHaveValue("speculative-alpha");
        await expect(input).toHaveAttribute("data-original", "1");

        await input.fill("speculative-beta");
        await expect(input).toHaveAttribute("data-original", "1");
        // The renderer must use the callback from the latest render, not a stale one.
        expect(await page.evaluate(() => (window as unknown as Probe).__oqGeneration)).toBe(1);
        expect(await page.evaluate(() => (window as unknown as Probe).__oqResponse)).toEqual({
            text: "speculative-beta",
        });
    });
});

interface Probe {
    __oqResponse?: unknown;
    __oqGeneration?: number;
}
