import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import {
    getStudentReports,
    getClassReport,
    getItemReports,
    buildAdaptiveSet,
    type AdaptiveSetRequest,
} from "./AnalyticsApiService";

const fetchMock = vi.fn();
vi.stubGlobal("fetch", fetchMock);

function ok<T>(data: T): Response {
    return { ok: true, status: 200, json: () => Promise.resolve(data) } as Response;
}

function http(status: number): Response {
    return { ok: false, status, json: () => Promise.resolve({}) } as Response;
}

beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockResolvedValue(ok([]));
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("getStudentReports", () => {
    test("omits the query when no session is given", async () => {
        await expect(getStudentReports()).resolves.toEqual([]);
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/admin/analytics/students",
            expect.objectContaining({ credentials: "include" }),
        );
    });

    test("omits the query for a blank session id", async () => {
        await getStudentReports("   ");
        expect(fetchMock).toHaveBeenCalledWith("/api/admin/analytics/students", expect.anything());
    });

    test("trims and encodes a session id", async () => {
        fetchMock.mockResolvedValue(ok([{ playerUuid: "p" }]));
        await expect(getStudentReports(" s 1 ")).resolves.toEqual([{ playerUuid: "p" }]);
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/admin/analytics/students?sessionId=s%201",
            expect.objectContaining({ credentials: "include" }),
        );
    });
});

describe("getClassReport", () => {
    test("omits the query when no session is given", async () => {
        await expect(getClassReport()).resolves.toEqual([]);
        expect(fetchMock).toHaveBeenCalledWith("/api/admin/analytics/classes", expect.anything());
    });

    test("omits the query for a blank session id", async () => {
        await getClassReport("");
        expect(fetchMock).toHaveBeenCalledWith("/api/admin/analytics/classes", expect.anything());
    });

    test("trims and encodes a session id", async () => {
        fetchMock.mockResolvedValue(ok({ sessionCount: 1 }));
        await expect(getClassReport(" g/1 ")).resolves.toEqual({ sessionCount: 1 });
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/admin/analytics/classes?sessionId=g%2F1",
            expect.anything(),
        );
    });
});

describe("getItemReports", () => {
    test("encodes the subject", async () => {
        fetchMock.mockResolvedValue(ok([{ questionId: "q" }]));
        await expect(getItemReports("A B")).resolves.toEqual([{ questionId: "q" }]);
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/admin/analytics/items?subject=A%20B",
            expect.objectContaining({ credentials: "include" }),
        );
    });
});

describe("buildAdaptiveSet", () => {
    test("POSTs the payload as JSON", async () => {
        const payload: AdaptiveSetRequest = {
            subject: "BIOLOGY",
            unit: "Cells",
            size: 10,
            mode: "adaptive",
            targetDifficulty: 0.6,
            title: "Set",
            seed: 7,
        };
        fetchMock.mockResolvedValue(ok({ quizId: "q1", questionCount: 10 }));
        await expect(buildAdaptiveSet(payload)).resolves.toEqual({
            quizId: "q1",
            questionCount: 10,
        });
        expect(fetchMock).toHaveBeenCalledWith("/api/admin/analytics/adaptive-set", {
            credentials: "include",
            headers: { "Content-Type": "application/json" },
            method: "POST",
            body: JSON.stringify(payload),
        });
    });
});

describe("request errors", () => {
    test("network failure maps to a plain error", async () => {
        fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));
        await expect(getStudentReports()).rejects.toThrow("Network Error");
    });

    test("non-2xx responses map to a status error", async () => {
        fetchMock.mockResolvedValue(http(500));
        await expect(getClassReport()).rejects.toThrow("Request failed with status code 500");
        await expect(
            buildAdaptiveSet({
                subject: "X",
                size: 1,
                mode: "balanced",
                targetDifficulty: 0.5,
            }),
        ).rejects.toThrow("Request failed with status code 500");
    });
});
