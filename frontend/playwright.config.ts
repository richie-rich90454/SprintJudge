import { defineConfig, devices } from "@playwright/test";

// jar-smoke points the specs at an already-running backend on :8091.
const externalBackend = process.env.SPRINTJUDGE_BACKEND_URL;

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
                channel: process.env.PLAYWRIGHT_CHANNEL || undefined,
            },
        },
    ],
    // Bare `npx playwright test` boots both servers (the jar comes from
    // `make package` / CI). reuseExistingServer prevents a second boot when
    // the Makefile or CI has already started them.
    webServer: [
        ...(externalBackend
            ? []
            : [
                  {
                      command:
                          "java -jar ../target/sprintjudge.jar --spring.profiles.active=prod --server.port=8080 --sprintjudge.executor.public-run=true --server.servlet.session.cookie.secure=false",
                      url: "http://127.0.0.1:8080/actuator/health",
                      reuseExistingServer: true,
                      timeout: 180000,
                      env: {
                          SPRINTJUDGE_ADMIN_PASSWORD:
                              process.env.SPRINTJUDGE_ADMIN_PASSWORD ?? "ci-admin-password",
                      },
                  },
              ]),
        {
            command: "npm run dev -- --host 127.0.0.1",
            url: "http://localhost:5173",
            reuseExistingServer: true,
            timeout: 60000,
        },
    ],
});
