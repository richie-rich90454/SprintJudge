import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
    testDir: "./e2e",
    // All specs share one backend/database; parallel workers create and delete
    // rooms, quizzes and settings and make each other flaky.
    workers: 1,
    timeout: 30000,
    use: {
        baseURL: "http://localhost:5173",
        headless: true,
    },
    // One baseline per project across platforms: CI runs Linux, developers
    // often run Windows. Rasterisation differs, so allow a modest pixel ratio.
    snapshotPathTemplate: "{testDir}/{testFilePath}-snapshots/{arg}-{projectName}.png",
    expect: {
        toHaveScreenshot: { maxDiffPixelRatio: 0.15, animations: "disabled" },
    },
    projects: [
        {
            name: "chromium",
            use: {
                ...devices["Desktop Chrome"],
                channel: process.env.CI ? undefined : "chrome",
            },
        },
    ],
});
