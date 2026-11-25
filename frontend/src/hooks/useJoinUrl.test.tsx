import { afterEach, describe, expect, test, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import axios from "axios";
import { useJoinUrl } from "./useJoinUrl";

vi.mock("axios", () => ({ default: { get: vi.fn() } }));

const get = vi.mocked(axios.get);
const realLocation = globalThis.location;

describe("useJoinUrl", () => {
    afterEach(() => {
        vi.clearAllMocks();
        vi.stubGlobal("location", realLocation);
    });

    test("falls back to the current origin while the LAN lookup is pending", () => {
        get.mockReturnValue(new Promise(() => {}));
        const { result } = renderHook(() => useJoinUrl("1234"));
        expect(result.current).toBe(`${window.location.origin}/j/1234`);
    });

    test("uses the advertised LAN address without a trailing slash", async () => {
        get.mockResolvedValue({ data: { urls: ["http://192.168.1.5:3000/"] } });
        const { result } = renderHook(() => useJoinUrl("1234"));
        await waitFor(() => expect(result.current).toBe("http://192.168.1.5:3000/j/1234"));
        expect(get).toHaveBeenCalledWith("/api/public/net");
    });

    test("ignores empty responses and rejected lookups", async () => {
        get.mockResolvedValueOnce({ data: {} });
        const empty = renderHook(() => useJoinUrl(""));
        expect(empty.result.current).toBe(window.location.origin);

        get.mockRejectedValueOnce(new Error("offline"));
        const failed = renderHook(() => useJoinUrl("99"));
        await waitFor(() => expect(get).toHaveBeenCalledTimes(2));
        expect(failed.result.current).toBe(`${window.location.origin}/j/99`);
    });

    test("stops listening for a LAN address after unmount", async () => {
        let resolve!: (value: unknown) => void;
        get.mockReturnValue(new Promise((r) => (resolve = r)));
        const { result, unmount } = renderHook(() => useJoinUrl("1234"));
        unmount();
        resolve({ data: { urls: ["http://192.168.1.5"] } });
        await Promise.resolve();
        expect(result.current).toBe(`${window.location.origin}/j/1234`);
    });

    test("treats numeric and bracketed IPv6 loopback hosts as loopback", async () => {
        for (const origin of ["http://127.0.0.1:3000", "http://[::1]:3000"]) {
            vi.stubGlobal("location", { origin });
            get.mockResolvedValue({ data: { urls: ["http://192.168.1.5"] } });
            const { result, unmount } = renderHook(() => useJoinUrl("1234"));
            await waitFor(() => expect(result.current).toBe("http://192.168.1.5/j/1234"));
            unmount();
        }
    });

    test("a non-loopback host ignores the advertised LAN address", async () => {
        vi.stubGlobal("location", { origin: "http://example.com" });
        get.mockResolvedValue({ data: { urls: ["http://192.168.1.5"] } });
        const { result } = renderHook(() => useJoinUrl("1234"));
        await waitFor(() => expect(get).toHaveBeenCalledOnce());
        expect(result.current).toBe("http://example.com/j/1234");
    });

    test("uses the origin directly on a non-loopback host", async () => {
        const hostname = new URL(window.location.origin).hostname;
        if (hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1") {
            get.mockResolvedValue({ data: { urls: ["http://192.168.1.5"] } });
            const { result } = renderHook(() => useJoinUrl("1234"));
            await waitFor(() => expect(result.current).toBe("http://192.168.1.5/j/1234"));
        } else {
            get.mockResolvedValue({ data: { urls: ["http://192.168.1.5"] } });
            const { result } = renderHook(() => useJoinUrl("1234"));
            await waitFor(() => expect(get).toHaveBeenCalledOnce());
            expect(result.current).toBe(`${window.location.origin}/j/1234`);
        }
    });
});
