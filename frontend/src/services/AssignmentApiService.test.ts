import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";

const client = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
}));

const createMock = vi.hoisted(() => vi.fn(() => client));

vi.mock("axios", () => ({ default: { create: createMock } }));

import axios from "axios";
import { AssignmentApiService, assignmentApi } from "./AssignmentApiService";

function ok<T>(data: T) {
    return { data };
}

beforeEach(() => {
    client.get.mockReset();
    client.post.mockReset();
    client.get.mockResolvedValue(ok([]));
    client.post.mockResolvedValue(ok({}));
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("AssignmentApiService setup", () => {
    test("default client targets /api with credentials and xsrf", () => {
        new AssignmentApiService();
        expect(axios.create).toHaveBeenCalledWith({
            baseURL: "/api",
            headers: { "Content-Type": "application/json" },
            withCredentials: true,
            xsrfCookieName: "XSRF-TOKEN",
            xsrfHeaderName: "X-XSRF-TOKEN",
        });
    });

    test("custom base url is honored", () => {
        new AssignmentApiService("http://x/api");
        expect(axios.create).toHaveBeenLastCalledWith(
            expect.objectContaining({ baseURL: "http://x/api" }),
        );
    });

    test("the exported singleton is shared", () => {
        expect(assignmentApi).toBe(AssignmentApiService.instance);
        expect(AssignmentApiService.instance).toBe(assignmentApi);
    });
});

describe("AssignmentApiService endpoints", () => {
    const svc = new AssignmentApiService();

    test("list GETs all assignments", async () => {
        client.get.mockResolvedValue(ok([{ id: "a1" }]));
        await expect(svc.list()).resolves.toEqual([{ id: "a1" }]);
        expect(client.get).toHaveBeenCalledWith("/admin/assignments");
    });

    test("create POSTs the new assignment", async () => {
        const input = { quizId: "q1", title: "T", description: "D", releaseAt: 1, dueAt: 2, ltiDeploymentId: "d" };
        client.post.mockResolvedValue(ok({ id: "a1" }));
        await expect(svc.create(input)).resolves.toEqual({ id: "a1" });
        expect(client.post).toHaveBeenCalledWith("/admin/assignments", input);
    });

    test("get encodes the id", async () => {
        client.get.mockResolvedValue(ok({ id: "a/b" }));
        await expect(svc.get("a/b")).resolves.toEqual({ id: "a/b" });
        expect(client.get).toHaveBeenCalledWith("/admin/assignments/a%2Fb");
    });

    test("roster GETs the encoded roster path", async () => {
        client.get.mockResolvedValue(ok([{ id: "r1" }]));
        await expect(svc.roster("a b")).resolves.toEqual([{ id: "r1" }]);
        expect(client.get).toHaveBeenCalledWith("/admin/assignments/a%20b/roster");
    });

    test("importRoster POSTs the csv", async () => {
        client.post.mockResolvedValue(ok({ imported: 3 }));
        await expect(svc.importRoster("a1", "s,email")).resolves.toEqual({ imported: 3 });
        expect(client.post).toHaveBeenCalledWith("/admin/assignments/a1/roster", { csv: "s,email" });
    });

    test("progress GETs the progress list", async () => {
        client.get.mockResolvedValue(ok([{ id: "p1" }]));
        await expect(svc.progress("a1")).resolves.toEqual([{ id: "p1" }]);
        expect(client.get).toHaveBeenCalledWith("/admin/assignments/a1/progress");
    });

    test("recordGrade POSTs the grade payload", async () => {
        client.post.mockResolvedValue(ok({ grade: "A" }));
        await expect(svc.recordGrade("a1", "s1", 8, 10)).resolves.toEqual({ grade: "A" });
        expect(client.post).toHaveBeenCalledWith("/admin/assignments/a1/grades", {
            studentId: "s1",
            score: 8,
            maxScore: 10,
        });
    });

    test("start POSTs the public start endpoint", async () => {
        client.post.mockResolvedValue(ok({ id: "p1" }));
        await expect(svc.start("a1", "s1")).resolves.toEqual({ id: "p1" });
        expect(client.post).toHaveBeenCalledWith("/public/assignments/a1/start", { studentId: "s1" });
    });

    test("studentProgress GETs with a query string", async () => {
        client.get.mockResolvedValue(ok({ id: "p1" }));
        await expect(svc.studentProgress("a1", "s 1")).resolves.toEqual({ id: "p1" });
        expect(client.get).toHaveBeenCalledWith(
            "/public/assignments/a1/progress?studentId=s+1",
        );
    });

    test("ltiLaunch POSTs the claims", async () => {
        const claims = { sub: "s1" };
        client.post.mockResolvedValue(ok({ valid: true }));
        await expect(svc.ltiLaunch(claims)).resolves.toEqual({ valid: true });
        expect(client.post).toHaveBeenCalledWith("/public/assignments/lti/launch", claims);
    });
});
