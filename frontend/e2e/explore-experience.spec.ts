import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";

function probe<T>(page: Page, read: () => T): Promise<T> {
    return page.evaluate(read);
}

async function expectDragOrder(rows: Locator, texts: string[]) {
    await expect(rows).toHaveCount(texts.length);
    for (let i = 0; i < texts.length; i += 1) {
        await expect(rows.nth(i).locator("span").first()).toHaveText(`${i + 1}.`);
        await expect(rows.nth(i).locator("span").nth(1)).toHaveText(texts[i]);
    }
}

async function activeInfo(page: Page) {
    return probe(page, () => {
        const el = document.activeElement as HTMLElement | null;
        return {
            tag: el?.tagName ?? "",
            text: (el?.textContent ?? "").trim(),
            aria: el?.getAttribute("aria-label") ?? "",
            id: el?.id ?? "",
        };
    });
}

test.describe("explore experience", () => {
    test.use({ serviceWorkers: "block" });

    test("locale EN -> AR -> EN updates direction, navigation, headings, buttons and labels", async ({
        page,
    }) => {
        await page.route("**/api/public/banks", (route) =>
            route.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
        );
        await page.goto("/");

        await expect(page.locator("html")).toHaveAttribute("lang", "en");
        await expect(page.locator("html")).toHaveAttribute("dir", "ltr");
        await expect(page.getByRole("link", { name: "Practice solo" })).toBeVisible();
        await expect(page.getByRole("link", { name: "Browse quizzes" })).toBeVisible();
        await expect(page.locator(".skip-link")).toHaveText("Skip to main content");
        await expect(page.getByRole("button", { name: "About SprintJudge" })).toBeVisible();

        await page.locator("#oq-locale").selectOption("ar");
        await expect(page.locator("html")).toHaveAttribute("lang", "ar");
        await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
        await expect(page.getByRole("link", { name: "تدريب فردي" })).toBeVisible();
        await expect(page.getByRole("link", { name: /الاختبارات/ })).toBeVisible();
        await expect(page.locator(".skip-link")).toContainText("المحتوى الرئيسي");
        const aboutAr = page.getByRole("button", { name: "حول SprintJudge" });
        await expect(aboutAr).toBeVisible();
        await aboutAr.click();
        await expect(page.getByRole("dialog")).toBeVisible();
        await expect(page.getByRole("button", { name: "إغلاق النافذة" })).toBeVisible();
        await page.keyboard.press("Escape");
        await expect(page.getByRole("dialog")).toHaveCount(0);
        await expect(aboutAr).toBeFocused();

        await page.goto("/explore");
        await expect(page.getByRole("heading", { name: /الاختبارات/ })).toBeVisible();
        await expect(page.getByText("مكتبة الاختبارات")).toBeVisible();
        await expect(page.getByRole("textbox", { name: "البحث عن اختبارات" })).toBeVisible();
        await expect(
            page.getByRole("link", { name: "الصفحة الرئيسية لـ SprintJudge" }),
        ).toBeVisible();

        await page.locator("#oq-locale").selectOption("en");
        await expect(page.locator("html")).toHaveAttribute("lang", "en");
        await expect(page.locator("html")).toHaveAttribute("dir", "ltr");
        await expect(page.getByRole("heading", { name: "Browse quizzes" })).toBeVisible();
        await expect(page.getByText("Quiz library")).toBeVisible();
        await expect(page.getByRole("textbox", { name: "Search quizzes" })).toBeVisible();
        await expect(page.getByRole("link", { name: "SprintJudge home" })).toBeVisible();
        await expect(page.getByRole("button", { name: "About SprintJudge" })).toBeVisible();
    });

    test("keyboard only: skip link, tab order, Escape modal, arrow reorder and arrow tabs", async ({
        page,
    }) => {
        await page.goto("/");

        // Skip link first, from a fresh document so focus starts at the top.
        await page.keyboard.press("Tab");
        expect((await activeInfo(page)).text).toContain("Skip to main content");
        await page.keyboard.press("Enter");
        expect(await probe(page, () => document.activeElement?.id ?? "")).toBe("main-content");
        await page.keyboard.press("Tab");
        expect((await activeInfo(page)).id).toBe("join-name");

        // Let React render before tabbing, or the first Tab is consumed by a
        // still-blank body and the tab order appears shifted by one.
        await page.reload();
        await expect(page.locator(".skip-link")).toBeVisible();
        await page.keyboard.press("Tab");
        expect((await activeInfo(page)).text).toContain("Skip to main content");
        await page.keyboard.press("Tab");
        expect((await activeInfo(page)).aria).toMatch(/^(Mute sounds|Unmute sounds)$/);
        await page.keyboard.press("Tab");
        expect((await activeInfo(page)).aria).toMatch(/^Motion:/);
        await page.keyboard.press("Tab");
        expect((await activeInfo(page)).aria).toBe("About SprintJudge");
        await page.keyboard.press("Tab");
        expect((await activeInfo(page)).id).toBe("join-name");
        await page.keyboard.press("Tab");
        expect((await activeInfo(page)).id).toBe("join-pin");
        await page.keyboard.press("Tab");
        expect((await activeInfo(page)).text).toBe("Practice solo");
        await page.keyboard.press("Tab");
        expect((await activeInfo(page)).text).toBe("Browse quizzes");
        await page.keyboard.press("Tab");
        expect((await activeInfo(page)).id).toBe("oq-locale");

        // Modal from a fresh document, reached by four Tabs (no pointer input).
        await page.reload();
        await expect(page.locator(".skip-link")).toBeVisible();
        for (let i = 0; i < 4; i += 1) {
            await page.keyboard.press("Tab");
        }
        expect((await activeInfo(page)).aria).toBe("About SprintJudge");
        await page.keyboard.press("Enter");
        await expect(page.getByRole("dialog")).toBeVisible();
        await page.keyboard.press("Escape");
        await expect(page.getByRole("dialog")).toHaveCount(0);
        // Radix restores opener focus in a setTimeout, after the dialog is gone.
        await expect(page.getByRole("button", { name: "About SprintJudge" })).toBeFocused();
        expect((await activeInfo(page)).tag).not.toBe("BODY");

        // Mount the real DragSortRenderer via Vite's module server: the arrow
        // buttons are the renderer's own keyboard affordance.
        await page.evaluate(async () => {
            const { DragSortRenderer } =
                (await import("/src/services/renderers/DragSortRenderer.ts")) as {
                    DragSortRenderer: new (
                        container: HTMLElement,
                        config: unknown,
                        onChange: (response: unknown) => void,
                    ) => { mount(): void };
                };
            const host = document.createElement("div");
            host.id = "oq-drag-host";
            document.body.append(host);
            (window as unknown as { __oqDrag?: unknown[] }).__oqDrag = [];
            const renderer = new DragSortRenderer(
                host,
                {
                    lines: [
                        { id: "a", text: "Alpha" },
                        { id: "b", text: "Beta" },
                        { id: "c", text: "Gamma" },
                    ],
                },
                (response) => {
                    (window as unknown as { __oqDrag: unknown[] }).__oqDrag.push(response);
                },
            );
            renderer.mount();
        });

        const rows = page.locator("#oq-drag-host div[draggable='true']");
        await expectDragOrder(rows, ["Alpha", "Beta", "Gamma"]);
        await expect(page.getByRole("button", { name: "Move Alpha up" })).toBeDisabled();
        await expect(page.getByRole("button", { name: "Move Gamma down" })).toBeDisabled();

        await page.getByRole("button", { name: "Move Beta up" }).focus();
        await page.keyboard.press("Enter");
        await expectDragOrder(rows, ["Beta", "Alpha", "Gamma"]);
        expect(
            await probe(page, () => {
                const p = window as unknown as { __oqDrag: unknown[] };
                return p.__oqDrag[p.__oqDrag.length - 1];
            }),
        ).toEqual({ order: ["b", "a", "c"] });

        await page.getByRole("button", { name: "Move Gamma up" }).focus();
        await page.keyboard.press("Space");
        await expectDragOrder(rows, ["Beta", "Gamma", "Alpha"]);
        await expect(page.getByRole("button", { name: "Move Beta up" })).toBeDisabled();

        await page.goto("/results");
        await page.waitForLoadState("networkidle");
        const tabs = page.getByRole("tab");
        await expect(tabs.first()).toHaveAttribute("aria-selected", "true");
        await tabs.first().focus();
        await page.keyboard.press("ArrowRight");
        await expect(tabs.nth(1)).toHaveAttribute("aria-selected", "true");
        expect((await activeInfo(page)).text).toBe("Answer Key");
        await page.keyboard.press("ArrowLeft");
        await expect(tabs.first()).toHaveAttribute("aria-selected", "true");
    });

    test("emulated reduced motion flips the html class live without a reload", async ({ page }) => {
        await page.addInitScript(() => localStorage.removeItem("oq-motion"));
        await page.emulateMedia({ reducedMotion: "no-preference" });
        await page.goto("/");

        const html = page.locator("html");
        await expect(html).not.toHaveClass(/motion-reduced/);
        await page.evaluate(() => {
            (window as unknown as { __oqAlive?: string }).__oqAlive = "alive";
        });

        await page.emulateMedia({ reducedMotion: "reduce" });
        await expect(html).toHaveClass(/motion-reduced/);
        await page.emulateMedia({ reducedMotion: "no-preference" });
        await expect(html).not.toHaveClass(/motion-reduced/);
        expect(
            await probe(page, () => (window as unknown as { __oqAlive?: string }).__oqAlive),
        ).toBe("alive");
    });

    test("forced API 500 shows a localized error with a working retry", async ({ page }) => {
        let calls = 0;
        await page.route("**/api/public/banks", (route) => {
            calls += 1;
            return route.fulfill({ status: 500, contentType: "application/json", body: "{}" });
        });
        await page.goto("/explore");

        await expect(page.getByText("Could not load the library")).toBeVisible({
            timeout: 15000,
        });
        await expect(page.getByText("Check your connection and try again.")).toBeVisible();
        const retry = page.getByRole("button", { name: "Retry" });
        await expect(retry).toBeVisible();
        await expect(retry).toBeEnabled();
        const before = calls;
        await retry.click();
        await expect(page.getByText("Could not load the library")).toBeVisible();
        expect(calls).toBeGreaterThan(before);

        await page.locator("#oq-locale").selectOption("ar");
        await expect(page.getByText(/تحميل المكتبة/)).toBeVisible();
        await expect(page.getByText(/اتصالك/)).toBeVisible();
        await expect(page.getByRole("button", { name: "حاول مرة أخرى" })).toBeVisible();
    });
});

test.describe("explore experience PWA update", () => {
    // Service workers are intentionally allowed here (unlike the other specs).
    test("first install does not reload; a waiting worker reloads only on explicit accept", async ({
        page,
        context,
    }) => {
        test.setTimeout(60_000);
        const swSource = readFileSync(new URL("../public/sw.js", import.meta.url), "utf8");
        let simulateUpdate = false;

        // The PWA branch in App.tsx is PROD-only; flip that one injected flag
        // in the Vite-served module so the real registration/update UI runs.
        await patchProdFlag(context);
        await context.route("**/sw.js*", (route) =>
            route.fulfill({
                status: 200,
                contentType: "text/javascript",
                body: simulateUpdate ? swSource.replace("oq-sw-v4", "oq-sw-v5") : swSource,
            }),
        );

        // Count documents in this tab: any unexpected reload bumps the counter.
        await page.addInitScript(() => {
            const w = window as unknown as { __oqLoads?: number };
            w.__oqLoads = Number(sessionStorage.getItem("__oqLoads") ?? 0) + 1;
            sessionStorage.setItem("__oqLoads", String(w.__oqLoads));
        });
        await page.goto("/");

        await page.waitForFunction(() => Boolean(navigator.serviceWorker.controller), null, {
            timeout: 20000,
        });
        await expect(page.getByText("A new version is available.")).toHaveCount(0);
        expect(
            await probe(page, () => (window as unknown as { __oqLoads?: number }).__oqLoads),
        ).toBe(1);
        expect(
            await probe(page, () =>
                caches.keys().then((keys) => keys.some((key) => key.startsWith("oq-sw-v4"))),
            ),
        ).toBe(true);

        simulateUpdate = true;
        // reg.update() reuses Chromium's cached script and never re-fetches, so
        // the interception below never sees a new worker. A cache-busted
        // registration forces a real update check against the patched source.
        await probe(page, () => navigator.serviceWorker.register("/sw.js?v=2"));
        const banner = page.getByText("A new version is available.");
        await expect(banner).toBeVisible({ timeout: 20000 });
        const reload = page.getByRole("button", { name: "Reload" });
        await expect(reload).toBeVisible();
        // Still no interruption before the user acts.
        expect(
            await probe(page, () => (window as unknown as { __oqLoads?: number }).__oqLoads),
        ).toBe(1);

        // The accepted worker keeps the cache-busted URL; keep the reloaded
        // document on that same URL so it does not look like a second update.
        await page.addInitScript(() => {
            const sw = navigator.serviceWorker;
            const register = sw.register.bind(sw);
            sw.register = (url, options) =>
                register(String(url) === "/sw.js" ? "/sw.js?v=2" : url, options);
        });
        await reload.click();
        await page.waitForFunction(
            () => (window as unknown as { __oqLoads?: number }).__oqLoads === 2,
            null,
            { timeout: 20000 },
        );
        await expect(page.getByRole("button", { name: "About SprintJudge" })).toBeVisible();
        await expect(banner).toHaveCount(0);
        await page.waitForFunction(async () => {
            const reg = await navigator.serviceWorker.getRegistration();
            return reg?.active?.state === "activated" && !reg.waiting;
        });
        expect(
            await probe(page, () =>
                caches.keys().then((keys) => keys.some((key) => key.startsWith("oq-sw-v5"))),
            ),
        ).toBe(true);
    });
});

async function patchProdFlag(context: BrowserContext): Promise<void> {
    await context.route("**/src/App.tsx*", async (route) => {
        const headers = { ...route.request().headers() };
        delete headers["if-none-match"];
        delete headers["if-modified-since"];
        const response = await route.fetch({ headers });
        const body = (await response.text()).replace('"PROD": false', '"PROD": true');
        await route.fulfill({ response, body });
    });
}
