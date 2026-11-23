import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import {
    storedIdentity,
    storeIdentity,
    forgetIdentity,
    createProfile,
    fetchProfile,
    updateProfile,
    type ProfileIdentity,
} from "./ProfileApiService";

const fetchMock = vi.fn();
const storage = {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
};
vi.stubGlobal("fetch", fetchMock);
vi.stubGlobal("localStorage", storage);

function ok<T>(data: T): Response {
    return { ok: true, status: 200, json: () => Promise.resolve(data) } as Response;
}

function http(status: number): Response {
    return { ok: false, status, json: () => Promise.resolve({}) } as Response;
}

const identity: ProfileIdentity = { id: "p 1", token: "tok" };

beforeEach(() => {
    fetchMock.mockReset();
    storage.getItem.mockReset();
    storage.setItem.mockReset();
    storage.removeItem.mockReset();
    storage.getItem.mockReturnValue(null);
    fetchMock.mockResolvedValue(ok({ ok: true }));
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("local identity", () => {
    test("storedIdentity returns both keys when present", () => {
        storage.getItem.mockImplementation((key: string) => (key === "player_profile_id" ? "id1" : "tok1"));
        expect(storedIdentity()).toEqual({ id: "id1", token: "tok1" });
    });

    test("storedIdentity returns null when the id is missing", () => {
        storage.getItem.mockReturnValue(null);
        expect(storedIdentity()).toBeNull();
    });

    test("storedIdentity returns null when the token is missing", () => {
        storage.getItem.mockImplementation((key: string) => (key === "player_profile_id" ? "id1" : null));
        expect(storedIdentity()).toBeNull();
    });

    test("storedIdentity returns null when storage throws", () => {
        storage.getItem.mockImplementation(() => {
            throw new Error("blocked");
        });
        expect(storedIdentity()).toBeNull();
    });

    test("storeIdentity writes both keys", () => {
        storeIdentity({ id: "id1", token: "tok1" });
        expect(storage.setItem).toHaveBeenCalledWith("player_profile_id", "id1");
        expect(storage.setItem).toHaveBeenCalledWith("player_profile_token", "tok1");
    });

    test("storeIdentity tolerates a storage failure", () => {
        storage.setItem.mockImplementationOnce(() => {
            throw new Error("blocked");
        });
        expect(() => storeIdentity(identity)).not.toThrow();
    });

    test("forgetIdentity removes both keys", () => {
        forgetIdentity();
        expect(storage.removeItem).toHaveBeenCalledWith("player_profile_id");
        expect(storage.removeItem).toHaveBeenCalledWith("player_profile_token");
    });

    test("forgetIdentity tolerates a storage failure", () => {
        storage.removeItem.mockImplementationOnce(() => {
            throw new Error("blocked");
        });
        expect(() => forgetIdentity()).not.toThrow();
    });
});

describe("createProfile", () => {
    test("POSTs only the nickname when optional fields are absent", async () => {
        await expect(createProfile("Nick")).resolves.toEqual({ ok: true });
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/public/profiles",
            {
                method: "POST",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ nickname: "Nick" }),
            },
        );
    });

    test("POSTs the optional playerUuid and rejoinToken", async () => {
        await createProfile("Nick", "uuid", "token");
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/public/profiles",
            expect.objectContaining({
                body: JSON.stringify({ nickname: "Nick", playerUuid: "uuid", rejoinToken: "token" }),
            }),
        );
    });
});

describe("fetchProfile", () => {
    test("GETs the encoded id with a bearer token", async () => {
        fetchMock.mockResolvedValue(ok({ profile: { id: "p 1" } }));
        await expect(fetchProfile(identity)).resolves.toEqual({ profile: { id: "p 1" } });
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/public/profiles/p%201",
            {
                method: "GET",
                credentials: "include",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: "Bearer tok",
                },
                body: undefined,
            },
        );
    });

    test("network failure maps to a plain error", async () => {
        fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));
        await expect(fetchProfile(identity)).rejects.toThrow("Network Error");
    });

    test("non-2xx responses map to a status error", async () => {
        fetchMock.mockResolvedValue(http(404));
        await expect(fetchProfile(identity)).rejects.toThrow("Request failed with status code 404");
    });
});

describe("updateProfile", () => {
    test("PATCHes an empty body with a bearer token", async () => {
        await expect(updateProfile(identity, {})).resolves.toEqual({ ok: true });
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/public/profiles/p%201",
            {
                method: "PATCH",
                credentials: "include",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: "Bearer tok",
                },
                body: "{}",
            },
        );
    });

    test("PATCHes all supplied changes including an empty nickname", async () => {
        await updateProfile(identity, { nickname: "", playerUuid: "u", rejoinToken: "r" });
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/public/profiles/p%201",
            expect.objectContaining({
                body: JSON.stringify({ nickname: "", playerUuid: "u", rejoinToken: "r" }),
            }),
        );
    });

    test("network failure maps to a plain error", async () => {
        fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));
        await expect(updateProfile(identity, {})).rejects.toThrow("Network Error");
    });

    test("non-2xx responses map to a status error", async () => {
        fetchMock.mockResolvedValue(http(403));
        await expect(updateProfile(identity, {})).rejects.toThrow("Request failed with status code 403");
    });
});
