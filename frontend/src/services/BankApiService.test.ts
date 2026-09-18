import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { getBankCoverage, getBankQuestion, getBankVolume, searchBank } from "./BankApiService";

const fetchMock = vi.fn();
vi.stubGlobal("fetch", fetchMock);

function ok<T>(data: T): Response {
    return { ok: true, status: 200, json: () => Promise.resolve(data) } as Response;
}

function http(status: number): Response {
    return { ok: false, status, json: () => Promise.resolve({}) } as Response;
}

const header = {
    id: "bio-1.1-0001",
    subject: "Biology",
    unit: "Cells",
    topic: "Membranes",
    scope: "current-scope",
    format: "CONCEPT_MCQ",
    stem: "Which structure controls what enters the cell?",
};

beforeEach(() => {
    vi.clearAllMocks();
    fetchMock.mockResolvedValue(ok([]));
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("searchBank", () => {
    test("sends subject, unit, scope, and format as URL params with credentials", async () => {
        fetchMock.mockResolvedValue(ok([header]));
        await expect(
            searchBank({ subject: "Biology", unit: " Cells ", scope: "current-scope", format: "CONCEPT_MCQ" }),
        ).resolves.toEqual([header]);
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/admin/bank/search?subject=Biology&unit=Cells&scope=current-scope&format=CONCEPT_MCQ",
            expect.objectContaining({ credentials: "include" }),
        );
    });

    test("sends only the subject when no filters are given", async () => {
        fetchMock.mockResolvedValue(ok([]));
        await expect(searchBank({ subject: "Chemistry" })).resolves.toEqual([]);
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/admin/bank/search?subject=Chemistry",
            expect.objectContaining({ credentials: "include" }),
        );
    });

    test("network failure maps to a plain error", async () => {
        fetchMock.mockRejectedValue(new TypeError("fetch failed"));
        await expect(searchBank({ subject: "Biology" })).rejects.toThrow("Network Error");
    });
});

describe("getBankQuestion", () => {
    test("GETs the answer-free header", async () => {
        fetchMock.mockResolvedValue(ok(header));
        await expect(getBankQuestion("bio-1.1-0001")).resolves.toEqual(header);
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/admin/bank/question/bio-1.1-0001",
            expect.objectContaining({ credentials: "include" }),
        );
    });

    test("encodes special characters in the id", async () => {
        fetchMock.mockResolvedValue(ok(header));
        await getBankQuestion("a b/c");
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/admin/bank/question/a%20b%2Fc",
            expect.objectContaining({ credentials: "include" }),
        );
    });

    test("404 propagates for an unknown id", async () => {
        fetchMock.mockResolvedValue(http(404));
        await expect(getBankQuestion("gone")).rejects.toThrow("404");
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/admin/bank/question/gone",
            expect.objectContaining({ credentials: "include" }),
        );
    });
});

describe("getBankCoverage", () => {
    test("GETs the subject coverage report and reshapes units", async () => {
        fetchMock.mockResolvedValue(
            ok({ "Unit 1": { total: 2, formats: ["CONCEPT_MCQ"], scopes: ["core-scope"] } }),
        );
        await expect(getBankCoverage("BIOLOGY")).resolves.toEqual({
            subject: "BIOLOGY",
            total: 2,
            units: [{ unit: "Unit 1", count: 2, formats: ["CONCEPT_MCQ"], scopes: ["core-scope"] }],
        });
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/admin/bank/coverage/BIOLOGY",
            expect.objectContaining({ credentials: "include" }),
        );
    });

    test("empty coverage reshapes to zero totals", async () => {
        fetchMock.mockResolvedValue(ok({}));
        await expect(getBankCoverage("BIOLOGY")).resolves.toEqual({
            subject: "BIOLOGY",
            total: 0,
            units: [],
        });
    });
});

describe("getBankVolume", () => {
    test("GETs the per-subject volume map", async () => {
        fetchMock.mockResolvedValue(ok({ BIOLOGY: 2 }));
        await expect(getBankVolume()).resolves.toEqual({ BIOLOGY: 2 });
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/admin/bank/volume",
            expect.objectContaining({ credentials: "include" }),
        );
    });
});
