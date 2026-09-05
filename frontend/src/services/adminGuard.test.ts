import { describe, test, expect, vi, beforeEach } from "vitest";

const getSettings = vi.hoisted(() => vi.fn());
const isAxiosError = vi.hoisted(() => vi.fn());

vi.mock("axios", () => ({ default: { isAxiosError } }));
vi.mock("./AdminApiService", () => ({ adminApi: { getSettings } }));

import { requireAdmin } from "./adminGuard";

function authError(status: number) {
    return Object.assign(new Error(`status ${status}`), {
        isAxiosError: true,
        response: { status },
    });
}

beforeEach(() => {
    vi.clearAllMocks();
    isAxiosError.mockImplementation(
        (e: unknown) => (e as { isAxiosError?: boolean })?.isAxiosError === true,
    );
    getSettings.mockResolvedValue({});
});

describe("requireAdmin", () => {
    test("passes when the admin session is valid", async () => {
        await expect(requireAdmin()).resolves.toBeUndefined();
    });

    test("bounces to the login page on 401", async () => {
        getSettings.mockRejectedValue(authError(401));
        const err = (await requireAdmin().then(
            () => null,
            (e: unknown) => e,
        )) as Response | null;
        expect(err).toBeInstanceOf(Response);
        expect(err?.status).toBe(307);
        expect((err as unknown as { options?: { to?: string } })?.options?.to).toBe(
            "/admin/login",
        );
    });

    test("bounces to the login page on 403", async () => {
        getSettings.mockRejectedValue(authError(403));
        await expect(requireAdmin()).rejects.toBeInstanceOf(Response);
    });

    test("lets the dashboard show its own error on 500", async () => {
        getSettings.mockRejectedValue(authError(500));
        await expect(requireAdmin()).resolves.toBeUndefined();
    });

    test("lets the dashboard show its own error when the backend is down", async () => {
        getSettings.mockRejectedValue(new Error("network down"));
        await expect(requireAdmin()).resolves.toBeUndefined();
    });
});
