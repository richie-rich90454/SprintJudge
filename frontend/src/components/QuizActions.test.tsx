import { beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QuizActions } from "./QuizActions";

const api = vi.hoisted(() => ({
    getQuestions: vi.fn(),
    createQuiz: vi.fn(),
    addQuestion: vi.fn(),
}));

vi.mock("../services/AdminApiService", () => ({ adminApi: api }));

const questions = [
    { id: "q1", orderIndex: 2, title: "Second" },
    { id: "q2", orderIndex: 1, title: "First" },
];

describe("QuizActions", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        api.getQuestions.mockResolvedValue(questions);
        api.createQuiz.mockResolvedValue({ id: "new-quiz" });
        api.addQuestion.mockResolvedValue({});
        vi.spyOn(Math, "random").mockReturnValue(0);
    });

    test("disables both actions when no quiz is open", () => {
        render(<QuizActions title="Bank" />);
        expect(screen.getByRole("button", { name: "Duplicate" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "Remix" })).toBeDisabled();
    });

    test("ignores a direct action invocation when no quiz is open", () => {
        render(<QuizActions title="Bank" />);
        const button = screen.getByRole("button", { name: "Duplicate" });
        const propsKey = Object.keys(button).find((key) => key.startsWith("__reactProps"));
        expect(propsKey).toBeDefined();
        (button as any)[propsKey!].onClick();
        expect(api.getQuestions).not.toHaveBeenCalled();
        expect(api.createQuiz).not.toHaveBeenCalled();
    });

    test("copies questions in their saved order", async () => {
        render(<QuizActions quizId="quiz-1" title="Bank" description="Description" />);
        fireEvent.click(screen.getByRole("button", { name: "Duplicate" }));
        expect(screen.getByRole("button", { name: "Copying" })).toBeDisabled();

        await waitFor(() =>
            expect(screen.getByRole("status")).toHaveTextContent("Copy created in the dashboard."),
        );
        expect(api.createQuiz).toHaveBeenCalledWith({
            title: "Bank (copy)",
            description: "Description",
        });
        expect(api.addQuestion.mock.calls.map(([payload]) => payload.title)).toEqual([
            "First",
            "Second",
        ]);
        expect(api.addQuestion.mock.calls.every(([payload]) => payload.quizId === "new-quiz")).toBe(
            true,
        );
    });

    test("remixes question order and defaults the description", async () => {
        render(<QuizActions quizId="quiz-1" title="Bank" />);
        fireEvent.click(screen.getByRole("button", { name: "Remix" }));
        expect(screen.getByRole("button", { name: "Remixing" })).toBeDisabled();
        await waitFor(() =>
            expect(screen.getByRole("status")).toHaveTextContent("Remix created in the dashboard."),
        );
        expect(api.createQuiz).toHaveBeenCalledWith({ title: "Bank (remix)", description: "" });
        expect(api.addQuestion).toHaveBeenCalledTimes(2);
    });

    test("reports a failed copy and becomes idle again", async () => {
        api.getQuestions.mockRejectedValue(new Error("offline"));
        render(<QuizActions quizId="quiz-1" title="Bank" />);
        fireEvent.click(screen.getByRole("button", { name: "Duplicate" }));
        expect(
            await screen.findByText("Could not copy this quiz. Sign in as admin and retry."),
        ).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Duplicate" })).toBeEnabled();
    });
});
