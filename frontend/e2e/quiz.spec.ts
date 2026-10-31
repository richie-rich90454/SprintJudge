import { test, expect } from "@playwright/test";

/**
 * Requires a running backend with a seeded library and the Vite dev server.
 * `make dev` is the intended runner. The tests use the public practice API so
 * they never depend on an admin session.
 */
test.describe("SprintJudge live loop", () => {
    test("student joins a practice room, sees a question, and survives refresh", async ({
        page,
        request,
    }) => {
        const banksResponse = await request.get("/api/public/banks");
        expect(banksResponse.ok()).toBeTruthy();
        const banks = (await banksResponse.json()) as Array<{ id: string; title: string }>;
        expect(banks.length).toBeGreaterThan(0);

        const start = await request.post("/api/public/practice", {
            data: { quizId: banks[0].id },
        });
        expect(start.ok()).toBeTruthy();
        const { pinCode } = (await start.json()) as { pinCode: string };
        expect(pinCode).toMatch(/^\d{6}$/);

        await page.goto(`/j/${pinCode}`);
        await page.getByLabel("Your name").fill("Ada");
        await page.getByRole("button", { name: "Join game" }).click();

        await expect(page.getByText("Question", { exact: true })).toBeVisible({
            timeout: 15000,
        });

        await page.reload();
        await expect(page.getByText("Question", { exact: true })).toBeVisible({
            timeout: 15000,
        });
        await expect(page.getByText("No game", { exact: true })).toHaveCount(0);
    });

});
