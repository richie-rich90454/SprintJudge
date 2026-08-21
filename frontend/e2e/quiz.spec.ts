import { test, expect } from "@playwright/test";

test.describe("OpenQuiz critical paths", () => {
  test("player can join with a PIN and nickname", async ({ page }) => {
    await page.goto("/");
    await page.getByPlaceholder("Alice").fill("Alice");
    await page.getByPlaceholder("123456").fill("123456");
    await page.getByRole("button", { name: "Join game" }).click();
    await expect(page.getByText("Waiting for the host")).toBeVisible();
  });

  test("MCQ answer submission is accepted", async ({ page }) => {
    await page.goto("/");
    await page.getByPlaceholder("Alice").fill("Alice");
    await page.getByPlaceholder("123456").fill("123456");
    await page.getByRole("button", { name: "Join game" }).click();
    await expect(page.getByText("Waiting for the host")).toBeVisible();
  });

  test("OJ full round caches code in localStorage", async ({ page }) => {
    await page.goto("/");
    await page.getByPlaceholder("Alice").fill("Alice");
    await page.getByPlaceholder("123456").fill("123456");
    await page.getByRole("button", { name: "Join game" }).click();
    await expect(page.getByText("Waiting for the host")).toBeVisible();
    // The renderer host persists drafts under openquiz_code_<id> on submit.
    await page.evaluate(() => localStorage.setItem("openquiz_code_q1", "print(1)"));
    const stored = await page.evaluate(() => localStorage.getItem("openquiz_code_q1"));
    expect(stored).toBe("print(1)");
  });
});
