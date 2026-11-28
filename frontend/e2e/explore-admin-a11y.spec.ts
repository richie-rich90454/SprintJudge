import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

// The PWA service worker claims the page and reloads on controllerchange,
// cancelling the form POST before Spring Security sees it.
test.use({ serviceWorkers: "block" });

/**
 * Exploratory keyboard/axe sweep of the admin surfaces against the CI-equivalent
 * backend (prod profile, seeded bank, ci-admin-password). Form login is served by
 * Spring Security; the jar serves the same SPA the Vite build produces.
 *
 * There are no in-app links to the standalone admin routes, so navigation between
 * screens uses page.goto/goBack (browser-chrome navigation is out of page scope).
 * Everything *inside* each screen is driven by the keyboard. Known a11y gaps are
 * reported as annotations so they do not hide serious/critical axe violations or
 * a broken keyboard journey.
 */
const B = process.env.SPRINTJUDGE_BACKEND_URL ?? "http://127.0.0.1:8080";
const PASSWORD = process.env.SPRINTJUDGE_ADMIN_PASSWORD ?? "ci-admin-password";

interface FocusInfo {
    tag: string;
    id: string;
    name: string;
    text: string;
}

async function focusInfo(page: Page): Promise<FocusInfo> {
    return page.evaluate(() => {
        const el = document.activeElement as HTMLElement | null;
        return {
            tag: el?.tagName ?? "",
            id: el?.id ?? "",
            name: el?.getAttribute("aria-label") ?? "",
            text: (el?.textContent ?? "").trim(),
        };
    });
}

/** Keyboard-only walk from the document start; no pointer input. */
async function tabUntil(page: Page, matches: (f: FocusInfo) => boolean, what: string) {
    for (let i = 0; i < 20; i += 1) {
        await page.keyboard.press("Tab");
        const info = await focusInfo(page);
        if (matches(info)) return info;
    }
    throw new Error(`Could not reach ${what} with Tab`);
}

async function expectNoSeriousViolations(page: Page, screen: string) {
    const results = await new AxeBuilder({ page }).analyze();
    const bad = results.violations
        .filter((v) => v.impact === "serious" || v.impact === "critical")
        .map((v) => `${v.id}: ${v.help}`);
    expect.soft(bad, `${screen}: serious/critical axe violations`).toEqual([]);
}

/** Record a known gap as a finding instead of failing the keyboard journey. */
function reportFinding(screen: string, finding: string, observed: unknown) {
    test.info().annotations.push({
        type: "a11y-finding",
        description: `${screen}: ${finding} (observed ${JSON.stringify(observed)})`,
    });
}

/** Keyboard-only Spring Security form login; asserts the focus path as it goes. */
async function loginAsAdmin(page: Page): Promise<void> {
    await page.goto(`${B}/admin/login`);
    await expect(page.locator("#al-user")).toBeVisible();
    expect.soft(await page.getByRole("textbox", { name: "Username" }).count()).toBe(1);
    expect.soft(await page.getByLabel("Password").count()).toBe(1);
    await expectNoSeriousViolations(page, "admin login");

    expect((await tabUntil(page, (f) => f.id === "al-user", "username field")).id).toBe("al-user");
    await page.keyboard.type("admin");
    await page.keyboard.press("Tab");
    expect((await focusInfo(page)).id).toBe("al-pass");
    await page.keyboard.type(PASSWORD);
    await page.keyboard.press("Tab");
    expect((await focusInfo(page)).text).toBe("Sign in");
    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 15_000 });
    await expect(page.getByText("Start a CSA class")).toBeVisible();
}

test.describe("exploratory admin accessibility", () => {
    test.setTimeout(200_000);

    test("keyboard-only admin sweep with axe on every screen", async ({ page }) => {
        const stamp = Date.now();

        await test.step("login and dashboard", async () => {
            await loginAsAdmin(page);
            await expect(page.getByRole("tablist", { name: "Admin sections" })).toBeVisible();
            await expectNoSeriousViolations(page, "admin dashboard");
        });

        await test.step("bank browser: labeled filters, live results, detail open/close", async () => {
            await page.goto(`${B}/admin/bank`);
            await expect(
                page.getByRole("heading", { name: "Browse the question bank" }),
            ).toBeVisible();
            await expectNoSeriousViolations(page, "bank browser");

            const subject = page.getByRole("combobox", { name: "Subject" });
            const scope = page.getByRole("combobox", { name: "Which topics" });
            const unit = page.getByRole("textbox", { name: "Filter by unit" });
            const clear = page.getByRole("button", { name: "Clear" });
            expect.soft(await subject.count(), "subject combobox name").toBe(1);
            expect.soft(await scope.count(), "scope combobox name").toBe(1);
            expect.soft(await unit.count(), "unit textbox name").toBe(1);
            expect.soft(await clear.count(), "clear button name").toBe(1);

            // Native controls: focus + keys only.
            await subject.focus();
            await page.keyboard.press("ArrowDown");
            await page.keyboard.press("Enter");
            await expect(subject).not.toHaveValue("Java Programming");
            await page.keyboard.press("ArrowUp");
            await page.keyboard.press("Enter");
            await expect(subject).toHaveValue("Java Programming");
            await scope.focus();
            await page.keyboard.press("End");
            await expect(scope).toHaveValue("core-scope");
            await page.keyboard.press("Home");
            await expect(scope).toHaveValue("everything");

            const coverage = page.locator('p[aria-live="polite"]').first();
            await expect(coverage).toHaveAttribute("aria-live", "polite");
            await expect(coverage).toContainText(/questions across \d+ units/);

            // The bank filter matches the exact unit name, not topic keywords.
            await unit.focus();
            await page.keyboard.type("Unit 2");
            const announced = page.locator('p[aria-live="polite"]').last();
            await expect(announced).toHaveAttribute("aria-live", "polite");
            await expect(announced).toHaveText(/^[1-9]\d* questions?$/, { timeout: 10_000 });

            const result = page.locator("ul li button[aria-expanded]").first();
            await expect(result).toBeVisible();
            await expect(result).toContainText(/Unit 2/i);
            await result.focus();
            await page.keyboard.press("Enter");
            await expect(result).toHaveAttribute("aria-expanded", "true");
            const detail = page.locator('[aria-live="polite"]').last();
            await expect(detail).toContainText("Question details");
            await page.keyboard.press("Enter");
            await expect(result).toHaveAttribute("aria-expanded", "false");
            await expect(page.getByText("Question details")).toHaveCount(0);

            // Browser-level back to the dashboard where the journey started.
            await page.goBack();
            await expect(page.getByText("Start a CSA class")).toBeVisible();
        });

        await test.step("question wizard: names, focus, keyboard submit", async () => {
            await page.getByRole("tab", { name: "Quizzes" }).focus();
            await page.keyboard.press("Enter");
            await expect(page.getByRole("heading", { name: "Quiz sets" })).toBeVisible();
            await page.getByRole("button", { name: "New set" }).focus();
            await page.keyboard.press("Enter");
            const quizTitle = `A11E probe ${stamp}`;
            await page.getByRole("textbox", { name: "Quiz title" }).focus();
            await page.keyboard.type(quizTitle);
            await page.getByRole("button", { name: "Create", exact: true }).focus();
            await page.keyboard.press("Enter");
            await expect(page.getByRole("heading", { name: quizTitle })).toBeVisible();

            // Guaranteed at least one quiz card now.
            await page.getByRole("button", { name: "Add", exact: true }).first().focus();
            await page.keyboard.press("Enter");
            const dialog = page.getByRole("dialog", { name: "Question wizard" });
            await expect(dialog).toBeVisible();
            expect
                .soft(await dialog.evaluate((el) => el.contains(document.activeElement)))
                .toBe(true);
            await expectNoSeriousViolations(page, "question wizard (open)");

            for (const step of ["Type", "Statement", "Config", "Preview"]) {
                expect
                    .soft(await dialog.getByRole("button", { name: step }).count(), `${step} step`)
                    .toBe(1);
            }
            await dialog.getByRole("button", { name: "MCQ" }).focus();
            await page.keyboard.press("Enter");
            await expect(dialog.getByRole("button", { name: "Statement" })).toHaveAttribute(
                "aria-current",
                "step",
            );

            reportFinding(
                "question wizard",
                "title input has no accessible name",
                await dialog.getByRole("textbox", { name: "Title" }).count(),
            );
            reportFinding(
                "question wizard",
                "description has no accessible name",
                await dialog.getByRole("textbox", { name: "Description (Markdown)" }).count(),
            );
            expect
                .soft(
                    await dialog.getByRole("spinbutton", { name: "Time limit (s)" }).count(),
                    "time limit",
                )
                .toBe(1);
            expect
                .soft(
                    await dialog.getByRole("spinbutton", { name: "Base points" }).count(),
                    "points",
                )
                .toBe(1);

            // Empty-title submit via Enter: the validation message must be announced
            // *and* take focus as an error summary.
            await dialog.getByRole("button", { name: "Preview" }).focus();
            await page.keyboard.press("Enter");
            await dialog.getByRole("button", { name: "Save question" }).focus();
            await page.keyboard.press("Enter");
            const alert = dialog.getByRole("alert").filter({ hasText: "Title is required" });
            await expect(alert).toBeVisible();
            reportFinding(
                "question wizard",
                "validation alert does not take focus",
                await alert.evaluate((el) => el === document.activeElement),
            );

            // Name gaps above are recorded; placeholder locators keep exploring.
            await dialog.locator('input[placeholder="Title"]').fill(`A11E question ${stamp}`);
            await dialog
                .locator('textarea[placeholder="Description (Markdown)"]')
                .fill("keyboard a11e probe");
            await dialog.getByRole("button", { name: "Config" }).focus();
            await page.keyboard.press("Enter");
            for (const letter of ["A", "B", "C", "D"]) {
                const named = await dialog
                    .getByRole("textbox", { name: `Option ${letter}` })
                    .count();
                if (named !== 1)
                    reportFinding(
                        "question wizard",
                        `option ${letter} has no accessible name`,
                        named,
                    );
                await dialog
                    .locator(`input[placeholder="Option ${letter}"]`)
                    .fill(`Option ${letter}`);
            }
            const namedIndex = await dialog
                .getByRole("spinbutton", { name: "Correct option index" })
                .count();
            if (namedIndex !== 1)
                reportFinding(
                    "question wizard",
                    "correct option index has no accessible name",
                    namedIndex,
                );
            await dialog.locator('input[type="number"]').first().fill("0");
            await dialog.getByRole("button", { name: "Preview" }).focus();
            await page.keyboard.press("Enter");
            await dialog.getByRole("button", { name: "Save question" }).focus();
            await page.keyboard.press("Enter");
            await expect(dialog).toHaveCount(0, { timeout: 15_000 });
        });

        await test.step("assignments: arrow tabs, labeled create fields and roster", async () => {
            await page.goto(`${B}/admin/assignments`);
            await expect(page.getByRole("heading", { name: "Assignments" })).toBeVisible();
            await expectNoSeriousViolations(page, "assignments");

            const tablist = page.getByRole("tablist", { name: "Assignment views" });
            await tablist.getByRole("tab", { name: "All assignments" }).focus();
            await page.keyboard.press("ArrowRight");
            const createTab = tablist.getByRole("tab", { name: "New assignment" });
            await expect(createTab).toHaveAttribute("aria-selected", "true");
            await expect(createTab).toBeFocused();
            await page.keyboard.press("ArrowLeft");
            await expect(tablist.getByRole("tab", { name: "All assignments" })).toBeFocused();
            await page.keyboard.press("ArrowRight");
            expect
                .soft(await page.getByRole("tabpanel", { name: "New assignment" }).count())
                .toBe(1);
            for (const name of ["Quiz", "Title", "Description"]) {
                expect.soft(await page.getByRole("textbox", { name }).count(), name).toBe(1);
            }
            for (const name of ["Release at", "Due at"]) {
                expect.soft(await page.getByLabel(name).count(), name).toBe(1);
            }
            expect
                .soft(await page.getByRole("button", { name: "Create assignment" }).count())
                .toBe(1);

            const quizzes = (await page.evaluate(async () => {
                const res = await fetch("/api/admin/quizzes", { credentials: "include" });
                return res.ok ? ((await res.json()) as Array<{ id: string }>) : [];
            })) as Array<{ id: string }>;
            const quizId = quizzes[0]?.id;
            expect(quizId, "a quiz id is needed to create an assignment").toBeTruthy();

            await page.getByLabel("Quiz").focus();
            await page.keyboard.type(quizId!);
            await page.getByRole("textbox", { name: "Title" }).focus();
            await page.keyboard.type(`A11E assignment ${stamp}`);
            await page.getByRole("button", { name: "Create assignment" }).focus();
            await page.keyboard.press("Enter");

            const roster = page.getByRole("textbox", { name: "Roster CSV" });
            await expect(roster).toBeVisible({ timeout: 15_000 });
            await roster.focus();
            await page.keyboard.type("student_id,name\ne2e-a11y,A11y Student");
            await page.keyboard.press("Tab");
            expect((await focusInfo(page)).text).toBe("Import CSV");
            await page.keyboard.press("Enter");
            await expect(page.getByText("1 students in roster")).toBeVisible({ timeout: 10_000 });
            expect
                .soft(await page.getByRole("button", { name: "Start assignment" }).count())
                .toBe(1);
            for (const rowButton of await page
                .getByRole("button", { name: /^A11E assignment / })
                .all()) {
                expect.soft(await rowButton.evaluate((el) => el.tabIndex >= 0)).toBe(true);
            }
        });

        await test.step("analytics: text alternatives, labeled filters, Apply", async () => {
            await page.goto(`${B}/admin/analytics`);
            await expect(
                page.getByRole("heading", { name: "How is the class doing?" }),
            ).toBeVisible();
            await expectNoSeriousViolations(page, "analytics");

            const summary = page.getByRole("region", { name: "Class summary" });
            const students = page.getByRole("region", { name: "Student report" });
            const items = page.getByRole("region", { name: "Item report" });
            await expect(summary).toBeVisible();
            await expect(students).toBeVisible();
            await expect(items).toBeVisible();

            // Bars are aria-hidden; the adjacent percentage text is the alternative.
            expect.soft(await summary.getByText(/\d+% correct/).count()).toBeGreaterThan(0);
            const tables = page.getByRole("table");
            expect.soft(await tables.count()).toBeGreaterThan(0);
            for (let i = 0; i < (await tables.count()); i += 1) {
                expect
                    .soft(
                        await tables.nth(i).locator("thead th[scope=col]").count(),
                        `table ${i} headers`,
                    )
                    .toBeGreaterThan(0);
            }

            expect
                .soft(await page.getByRole("textbox", { name: "Session id (optional)" }).count())
                .toBe(1);
            expect.soft(await page.getByRole("combobox", { name: "Subject" }).count()).toBe(1);
            expect.soft(await page.getByRole("combobox", { name: "Mode" }).count()).toBe(1);
            expect.soft(await page.getByRole("spinbutton", { name: "Questions" }).count()).toBe(1);
            expect
                .soft(await page.getByRole("spinbutton", { name: "Target difficulty" }).count())
                .toBe(1);
            expect
                .soft(await page.getByRole("textbox", { name: "Title (optional)" }).count())
                .toBe(1);

            const apply = page.getByRole("button", { name: "Apply filter" });
            await expect(apply).toBeVisible();
            await page.locator("#analytics-session").focus();
            await page.keyboard.type(`missing-${stamp}`);
            await page.keyboard.press("Tab");
            expect((await focusInfo(page)).text).toBe("Apply filter");
            await page.keyboard.press("Enter");
            await expect(summary.getByText("0 attempts")).toBeVisible({ timeout: 10_000 });
        });
    });
});
