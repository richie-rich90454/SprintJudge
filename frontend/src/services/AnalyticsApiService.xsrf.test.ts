import { afterEach, describe, expect, test, vi } from "vitest";
import { buildAdaptiveSet } from "./AnalyticsApiService";

afterEach(() => {
    document.cookie = "XSRF-TOKEN=; Max-Age=0";
    vi.unstubAllGlobals();
});

describe("buildAdaptiveSet CSRF", () => {
    test("echoes the XSRF cookie into the POST headers", async () => {
        document.cookie = "XSRF-TOKEN=token-123";
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({}),
        });
        vi.stubGlobal("fetch", fetchMock);

        await buildAdaptiveSet({
            subject: "MATH",
            size: 5,
            mode: "balanced",
            targetDifficulty: 0.5,
        });

        expect(fetchMock).toHaveBeenCalledWith(
            "/api/admin/analytics/adaptive-set",
            expect.objectContaining({
                method: "POST",
                headers: expect.objectContaining({ "X-XSRF-TOKEN": "token-123" }),
            }),
        );
    });
});
