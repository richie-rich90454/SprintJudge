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
