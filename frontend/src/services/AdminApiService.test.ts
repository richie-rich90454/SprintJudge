import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";

const client = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
}));

const createMock = vi.hoisted(() => vi.fn(() => client));

vi.mock("axios", () => ({ default: { create: createMock } }));

import axios from "axios";
import { AdminApiService } from "./AdminApiService";

function ok<T>(data: T) {
    return { data };
}

beforeEach(() => {
    vi.clearAllMocks();
    client.get.mockResolvedValue(ok([]));
    client.post.mockResolvedValue(ok({}));
    client.put.mockResolvedValue(ok({}));
    client.delete.mockResolvedValue(ok({}));
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("AdminApiService setup", () => {
    test("default client targets /api with credentials and xsrf", () => {
        new AdminApiService();
        expect(vi.mocked(axios.create)).toHaveBeenCalledWith({
            baseURL: "/api",
            headers: { "Content-Type": "application/json" },
            withCredentials: true,
            xsrfCookieName: "XSRF-TOKEN",
            xsrfHeaderName: "X-XSRF-TOKEN",
        });
    });

    test("custom base url is honored", () => {
        new AdminApiService("http://x/api");
        expect(vi.mocked(axios.create)).toHaveBeenLastCalledWith(
            expect.objectContaining({ baseURL: "http://x/api" }),
        );
    });

    test("singleton instance is shared", async () => {
        const { adminApi } = await import("./AdminApiService");
        expect(adminApi).toBe(AdminApiService.instance);
    });
});

describe("AdminApiService endpoints", () => {
    test("listQuizzes GETs the public quiz list", async () => {
        const svc = new AdminApiService();
        client.get.mockResolvedValue(ok([{ id: "q" }]));
        await expect(svc.listQuizzes()).resolves.toEqual([{ id: "q" }]);
        expect(client.get).toHaveBeenCalledWith("/public/quizzes");
    });

    test("getQuestions GETs the quiz question list", async () => {
        const svc = new AdminApiService();
        client.get.mockResolvedValue(ok([{ id: "a" }]));
        await expect(svc.getQuestions("quiz-1")).resolves.toEqual([{ id: "a" }]);
        expect(client.get).toHaveBeenCalledWith("/admin/quizzes/quiz-1/questions");
    });

    test("createQuiz POSTs the quiz body", async () => {
        const svc = new AdminApiService();
        client.post.mockResolvedValue(ok({ id: "n" }));
        await expect(svc.createQuiz({ title: "T", description: "D" })).resolves.toEqual({ id: "n" });
        expect(client.post).toHaveBeenCalledWith("/admin/quizzes", { title: "T", description: "D" });
    });

    test("deleteQuiz DELETEs the quiz", async () => {
        const svc = new AdminApiService();
        await svc.deleteQuiz("quiz-1");
        expect(client.delete).toHaveBeenCalledWith("/admin/quizzes/quiz-1");
    });

    test("addQuestion POSTs under the quiz", async () => {
        const svc = new AdminApiService();
        const q = { quizId: "quiz-1", title: "T", description: "", questionType: "MCQ", languagesAllowed: null, timeLimitSec: 30, pointsBase: 100, config: {}, orderIndex: 0 };
        client.post.mockResolvedValue(ok({ ...q, id: "new" }));
        await expect(svc.addQuestion(q)).resolves.toMatchObject({ id: "new" });
        expect(client.post).toHaveBeenCalledWith("/admin/quizzes/quiz-1/questions", q);
    });

    test("updateQuestion PUTs the question by id", async () => {
        const svc = new AdminApiService();
        const q = { id: "q1", quizId: "quiz-1", title: "T", description: "", questionType: "MCQ", languagesAllowed: null, timeLimitSec: 30, pointsBase: 100, config: {}, orderIndex: 0 };
        client.put.mockResolvedValue(ok(q));
        await expect(svc.updateQuestion(q)).resolves.toEqual(q);
        expect(client.put).toHaveBeenCalledWith("/admin/questions/q1", q);
    });

    test("deleteQuestion DELETEs the question", async () => {
        const svc = new AdminApiService();
        await svc.deleteQuestion("q1");
        expect(client.delete).toHaveBeenCalledWith("/admin/questions/q1");
    });

    test("getSettings GETs the settings map", async () => {
        const svc = new AdminApiService();
        client.get.mockResolvedValue(ok({ a: "b" }));
        await expect(svc.getSettings()).resolves.toEqual({ a: "b" });
        expect(client.get).toHaveBeenCalledWith("/admin/settings");
    });

    test("updateSettings PUTs the settings map", async () => {
        const svc = new AdminApiService();
        await svc.updateSettings({ a: "b" });
        expect(client.put).toHaveBeenCalledWith("/admin/settings", { a: "b" });
    });

    test("createGame defaults to STANDARD mode", async () => {
        const svc = new AdminApiService();
        client.post.mockResolvedValue(ok({ id: "g", pinCode: "1" }));
        await expect(svc.createGame("quiz-1")).resolves.toEqual({ id: "g", pinCode: "1" });
        expect(client.post).toHaveBeenCalledWith("/admin/games", { quizId: "quiz-1", gameMode: "STANDARD" });
    });

    test("createGame passes an explicit mode", async () => {
        const svc = new AdminApiService();
        client.post.mockResolvedValue(ok({ id: "g", pinCode: "2" }));
        await svc.createGame("quiz-1", "BATTLE");
        expect(client.post).toHaveBeenCalledWith("/admin/games", { quizId: "quiz-1", gameMode: "BATTLE" });
    });

    test("exportBank GETs the export payload", async () => {
        const svc = new AdminApiService();
        client.get.mockResolvedValue(ok("{}"));
        await expect(svc.exportBank()).resolves.toBe("{}");
        expect(client.get).toHaveBeenCalledWith("/admin/export");
    });

    test("importBank POSTs json with the replace flag", async () => {
        const svc = new AdminApiService();
        client.post.mockResolvedValue(ok({ importedQuestions: 3 }));
        await expect(svc.importBank("{}", true)).resolves.toEqual({ importedQuestions: 3 });
        expect(client.post).toHaveBeenCalledWith("/admin/import", { json: "{}", replace: true });
    });

    test("errors propagate to callers", async () => {
        const svc = new AdminApiService();
        client.get.mockRejectedValue(new Error("500"));
        await expect(svc.listQuizzes()).rejects.toThrow("500");
        client.post.mockRejectedValue(new Error("down"));
        await expect(
            svc.addQuestion({ quizId: "z", title: "", description: "", questionType: "X", languagesAllowed: null, timeLimitSec: 0, pointsBase: 0, config: {}, orderIndex: 0 }),
        ).rejects.toThrow("down");
    });
});

describe("AdminApiService error matrix", () => {
    function http(status: number) {
        return Object.assign(new Error(`Request failed with status code ${status}`), {
            response: { status },
        });
    }
    function qPayload(over: Record<string, unknown> = {}) {
        return {
            quizId: "quiz-1",
            title: "T",
            description: "",
            questionType: "MCQ",
            languagesAllowed: null,
            timeLimitSec: 30,
            pointsBase: 100,
            config: {},
            orderIndex: 0,
            ...over,
        };
    }

    test("listQuizzes 401 propagates the auth error", async () => {
        const svc = new AdminApiService();
        client.get.mockRejectedValue(http(401));
        await expect(svc.listQuizzes()).rejects.toThrow("401");
        expect(client.get).toHaveBeenCalledWith("/public/quizzes");
    });

    test("listQuizzes network failure propagates", async () => {
        const svc = new AdminApiService();
        client.get.mockRejectedValue(new Error("Network Error"));
        await expect(svc.listQuizzes()).rejects.toThrow("Network Error");
    });

    test("getQuestions 404 propagates for a missing quiz", async () => {
        const svc = new AdminApiService();
        client.get.mockRejectedValue(http(404));
        await expect(svc.getQuestions("quiz-missing")).rejects.toThrow("404");
        expect(client.get).toHaveBeenCalledWith("/admin/quizzes/quiz-missing/questions");
    });

    test("getQuestions 403 propagates for a forbidden quiz", async () => {
        const svc = new AdminApiService();
        client.get.mockRejectedValue(http(403));
        await expect(svc.getQuestions("quiz-1")).rejects.toThrow("403");
    });

    test("createQuiz 409 propagates the conflict", async () => {
        const svc = new AdminApiService();
        client.post.mockRejectedValue(http(409));
        await expect(svc.createQuiz({ title: "Dup", description: "" })).rejects.toThrow("409");
    });

    test("createQuiz 400 propagates validation errors", async () => {
        const svc = new AdminApiService();
        client.post.mockRejectedValue(http(400));
        await expect(svc.createQuiz({ title: "", description: "" })).rejects.toThrow("400");
    });

    test("deleteQuiz 403 propagates", async () => {
        const svc = new AdminApiService();
        client.delete.mockRejectedValue(http(403));
        await expect(svc.deleteQuiz("quiz-1")).rejects.toThrow("403");
    });

    test("deleteQuiz 404 propagates for a missing quiz", async () => {
        const svc = new AdminApiService();
        client.delete.mockRejectedValue(http(404));
        await expect(svc.deleteQuiz("quiz-gone")).rejects.toThrow("404");
    });

    test("addQuestion 400 propagates question validation errors", async () => {
        const svc = new AdminApiService();
        client.post.mockRejectedValue(http(400));
        await expect(svc.addQuestion(qPayload())).rejects.toThrow("400");
    });

    test("addQuestion 404 propagates for a missing parent quiz", async () => {
        const svc = new AdminApiService();
        client.post.mockRejectedValue(http(404));
        await expect(svc.addQuestion(qPayload({ quizId: "gone" }))).rejects.toThrow("404");
    });

    test("updateQuestion 404 propagates for a missing question", async () => {
        const svc = new AdminApiService();
        client.put.mockRejectedValue(http(404));
        await expect(svc.updateQuestion({ ...qPayload(), id: "gone" })).rejects.toThrow("404");
        expect(client.put).toHaveBeenCalledWith("/admin/questions/gone", expect.objectContaining({ id: "gone" }));
    });

    test("updateQuestion 409 propagates the version conflict", async () => {
        const svc = new AdminApiService();
        client.put.mockRejectedValue(http(409));
        await expect(svc.updateQuestion({ ...qPayload(), id: "q1" })).rejects.toThrow("409");
    });

    test("deleteQuestion 404 propagates", async () => {
        const svc = new AdminApiService();
        client.delete.mockRejectedValue(http(404));
        await expect(svc.deleteQuestion("gone")).rejects.toThrow("404");
    });

    test("deleteQuestion network failure propagates", async () => {
        const svc = new AdminApiService();
        client.delete.mockRejectedValue(new Error("Network Error"));
        await expect(svc.deleteQuestion("q1")).rejects.toThrow("Network Error");
    });

    test("getSettings 401 propagates", async () => {
        const svc = new AdminApiService();
        client.get.mockRejectedValue(http(401));
        await expect(svc.getSettings()).rejects.toThrow("401");
    });

    test("getSettings network failure propagates", async () => {
        const svc = new AdminApiService();
        client.get.mockRejectedValue(new Error("Network Error"));
        await expect(svc.getSettings()).rejects.toThrow("Network Error");
    });

    test("updateSettings 403 propagates", async () => {
        const svc = new AdminApiService();
        client.put.mockRejectedValue(http(403));
        await expect(svc.updateSettings({ a: "b" })).rejects.toThrow("403");
    });

    test("updateSettings 400 propagates bad settings", async () => {
        const svc = new AdminApiService();
        client.put.mockRejectedValue(http(400));
        await expect(svc.updateSettings({ bad: "!" })).rejects.toThrow("400");
    });

    test("createGame 429 propagates rate limiting", async () => {
        const svc = new AdminApiService();
        client.post.mockRejectedValue(http(429));
        await expect(svc.createGame("quiz-1", "BATTLE")).rejects.toThrow("429");
    });

    test("createGame 400 propagates for a bad mode payload", async () => {
        const svc = new AdminApiService();
        client.post.mockRejectedValue(http(400));
        await expect(svc.createGame("quiz-1")).rejects.toThrow("400");
    });

    test("exportBank 401 propagates", async () => {
        const svc = new AdminApiService();
        client.get.mockRejectedValue(http(401));
        await expect(svc.exportBank()).rejects.toThrow("401");
    });

    test("exportBank network failure propagates", async () => {
        const svc = new AdminApiService();
        client.get.mockRejectedValue(new Error("Network Error"));
        await expect(svc.exportBank()).rejects.toThrow("Network Error");
    });

    test("importBank 400 propagates malformed json errors", async () => {
        const svc = new AdminApiService();
        client.post.mockRejectedValue(http(400));
        await expect(svc.importBank("not-json", false)).rejects.toThrow("400");
    });

    test("importBank 409 propagates the replace conflict", async () => {
        const svc = new AdminApiService();
        client.post.mockRejectedValue(http(409));
        await expect(svc.importBank("{}", true)).rejects.toThrow("409");
    });

    test("importBank network failure propagates", async () => {
        const svc = new AdminApiService();
        client.post.mockRejectedValue(new Error("Network Error"));
        await expect(svc.importBank("{}", false)).rejects.toThrow("Network Error");
    });

    test("listQuizzes returns an empty list as-is", async () => {
        const svc = new AdminApiService();
        client.get.mockResolvedValue(ok([]));
        await expect(svc.listQuizzes()).resolves.toEqual([]);
    });

    test("getQuestions returns an empty list as-is", async () => {
        const svc = new AdminApiService();
        client.get.mockResolvedValue(ok([]));
        await expect(svc.getQuestions("quiz-empty")).resolves.toEqual([]);
    });

    test("listQuizzes passes through null-field payloads untouched", async () => {
        const svc = new AdminApiService();
        const rows = [{ id: "q1", title: null, description: null }];
        client.get.mockResolvedValue(ok(rows));
        await expect(svc.listQuizzes()).resolves.toEqual(rows);
    });

    test("getQuestions passes through null config payloads untouched", async () => {
        const svc = new AdminApiService();
        const rows = [{ ...qPayload(), config: null, languagesAllowed: null }];
        client.get.mockResolvedValue(ok(rows));
        await expect(svc.getQuestions("quiz-1")).resolves.toEqual(rows);
    });

    test("createGame passes through null-field game payloads untouched", async () => {
        const svc = new AdminApiService();
        const game = { id: "g", pinCode: null };
        client.post.mockResolvedValue(ok(game));
        await expect(svc.createGame("quiz-1", "EXAM")).resolves.toEqual(game);
        expect(client.post).toHaveBeenCalledWith("/admin/games", { quizId: "quiz-1", gameMode: "EXAM" });
    });

    test("create then list chains the new quiz id", async () => {
        const svc = new AdminApiService();
        client.post.mockResolvedValue(ok({ id: "chained" }));
        const created = await svc.createQuiz({ title: "Chain", description: "" });
        client.get.mockResolvedValue(ok([created]));
        await expect(svc.listQuizzes()).resolves.toEqual([{ id: "chained" }]);
    });

    test("add then fetch chains the new question", async () => {
        const svc = new AdminApiService();
        client.post.mockResolvedValue(ok({ ...qPayload(), id: "chain-q" }));
        const added = await svc.addQuestion(qPayload());
        client.get.mockResolvedValue(ok([added]));
        await expect(svc.getQuestions("quiz-1")).resolves.toEqual([expect.objectContaining({ id: "chain-q" })]);
    });

    test("createGame sweeps every supported mode", async () => {
        const svc = new AdminApiService();
        for (const mode of ["STANDARD", "AUTO_PILOT", "PRACTICE", "EXAM", "TEAM", "BATTLE"] as const) {
            client.post.mockResolvedValue(ok({ id: `g-${mode}`, pinCode: "1" }));
            await expect(svc.createGame("quiz-1", mode)).resolves.toEqual({ id: `g-${mode}`, pinCode: "1" });
            expect(client.post).toHaveBeenCalledWith("/admin/games", { quizId: "quiz-1", gameMode: mode });
        }
    });

    test("import replace flag flows false then true", async () => {
        const svc = new AdminApiService();
        client.post.mockResolvedValue(ok({ importedQuestions: 1 }));
        await svc.importBank("{}", false);
        expect(client.post).toHaveBeenLastCalledWith("/admin/import", { json: "{}", replace: false });
        client.post.mockResolvedValue(ok({ importedQuestions: 2 }));
        await expect(svc.importBank("{}", true)).resolves.toEqual({ importedQuestions: 2 });
    });

    test("deleteQuiz resolves cleanly then list reflects the removal", async () => {
        const svc = new AdminApiService();
        client.delete.mockResolvedValue(ok({}));
        await expect(svc.deleteQuiz("quiz-1")).resolves.toBeDefined();
        client.get.mockResolvedValue(ok([]));
        await expect(svc.listQuizzes()).resolves.toEqual([]);
    });

    test("getQuestions failure then success recovers the list", async () => {
        const svc = new AdminApiService();
        client.get.mockRejectedValueOnce(new Error("Network Error"));
        await expect(svc.getQuestions("quiz-1")).rejects.toThrow("Network Error");
        client.get.mockResolvedValue(ok([{ id: "back" }]));
        await expect(svc.getQuestions("quiz-1")).resolves.toEqual([{ id: "back" }]);
    });
});
