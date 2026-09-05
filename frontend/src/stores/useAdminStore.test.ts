import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";

const adminMock = vi.hoisted(() => ({
    listQuizzes: vi.fn(),
    getQuestions: vi.fn(),
    createQuiz: vi.fn(),
    addQuestion: vi.fn(),
    updateQuestion: vi.fn(),
}));

vi.mock("../services/AdminApiService", () => ({ adminApi: adminMock }));

import { useAdminStore } from "./useAdminStore";

function resetStore() {
    useAdminStore.setState({
        quizzes: [],
        questions: [],
        activeQuizId: null,
        wizardOpen: false,
        wizardStep: "type",
        wizardType: "MCQ",
        draft: {
            quizId: "",
            title: "",
            description: "",
            questionType: "MCQ",
            languagesAllowed: null,
            timeLimitSec: 30,
            pointsBase: 100,
            config: {},
            orderIndex: 0,
        },
    });
}

beforeEach(() => {
    vi.clearAllMocks();
    resetStore();
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("useAdminStore loading", () => {
    test("loadQuizzes stores the fetched quizzes", async () => {
        adminMock.listQuizzes.mockResolvedValue([{ id: "z1", title: "T", description: "D" }]);
        await useAdminStore.getState().loadQuizzes();
        expect(useAdminStore.getState().quizzes).toEqual([{ id: "z1", title: "T", description: "D" }]);
    });

    test("loadQuizzes propagates errors", async () => {
        adminMock.listQuizzes.mockRejectedValue(new Error("offline"));
        await expect(useAdminStore.getState().loadQuizzes()).rejects.toThrow("offline");
    });

    test("loadQuestions stores questions and marks active quiz", async () => {
        adminMock.getQuestions.mockResolvedValue([{ id: "q1" }]);
        await useAdminStore.getState().loadQuestions("quiz-1");
        expect(adminMock.getQuestions).toHaveBeenCalledWith("quiz-1");
        expect(useAdminStore.getState().questions).toEqual([{ id: "q1" }]);
        expect(useAdminStore.getState().activeQuizId).toBe("quiz-1");
    });

    test("loadQuestions with empty id does nothing", async () => {
        await useAdminStore.getState().loadQuestions("");
        expect(adminMock.getQuestions).not.toHaveBeenCalled();
        expect(useAdminStore.getState().activeQuizId).toBeNull();
    });
});

describe("useAdminStore wizard", () => {
    test("openWizard resets to a fresh MCQ draft for the quiz", () => {
        useAdminStore.getState().openWizard("quiz-7");
        const s = useAdminStore.getState();
        expect(s.wizardOpen).toBe(true);
        expect(s.wizardStep).toBe("type");
        expect(s.wizardType).toBe("MCQ");
        expect(s.draft.quizId).toBe("quiz-7");
        expect(s.draft.languagesAllowed).toBeNull();
        expect(s.activeQuizId).toBe("quiz-7");
    });

    test("closeWizard only closes the wizard", () => {
        useAdminStore.getState().openWizard("quiz-7");
        useAdminStore.getState().closeWizard();
        expect(useAdminStore.getState().wizardOpen).toBe(false);
        expect(useAdminStore.getState().activeQuizId).toBe("quiz-7");
    });

    test("setStep moves the wizard", () => {
        useAdminStore.getState().setStep("preview");
        expect(useAdminStore.getState().wizardStep).toBe("preview");
    });

    test("setType to OJ_FULL adds language allowlist and keeps statement fields", () => {
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.getState().setDraft({ title: "Q?", description: "Desc" });
        useAdminStore.getState().setType("OJ_FULL");
        const s = useAdminStore.getState();
        expect(s.wizardType).toBe("OJ_FULL");
        expect(s.draft.questionType).toBe("OJ_FULL");
        expect(s.draft.languagesAllowed).toEqual(["c", "cpp", "java", "node", "python"]);
        expect(s.draft.title).toBe("Q?");
        expect(s.draft.description).toBe("Desc");
    });

    test("setType resets type-specific config so MCQ options cannot leak", () => {
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.getState().setDraft({ config: { options: ["a", "b"] } });
        useAdminStore.getState().setType("NUMERIC");
        expect(useAdminStore.getState().draft.config).toEqual({});
    });

    test("setType falls back to defaults when previous draft fields are missing", () => {
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.setState({ draft: {} });
        useAdminStore.getState().setType("OJ_PATCH");
        const d = useAdminStore.getState().draft;
        expect(d.title).toBe("");
        expect(d.description).toBe("");
        expect(d.timeLimitSec).toBe(30);
        expect(d.pointsBase).toBe(100);
        expect(d.languagesAllowed).toEqual(["c", "cpp", "java", "node", "python"]);
    });

    test("setType without an active quiz falls back to an empty quiz id", () => {
        useAdminStore.getState().setType("MCQ");
        expect(useAdminStore.getState().draft.quizId).toBe("");
        expect(useAdminStore.getState().wizardType).toBe("MCQ");
    });

    test("setDraft merges the patch", () => {
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.getState().setDraft({ title: "Hello", pointsBase: 250 });
        const d = useAdminStore.getState().draft;
        expect(d.title).toBe("Hello");
        expect(d.pointsBase).toBe(250);
        expect(d.quizId).toBe("quiz-1");
    });
});

describe("useAdminStore persistence", () => {
    test("saveQuestion creates via addQuestion when draft has no id", async () => {
        adminMock.addQuestion.mockResolvedValue({ id: "new-q" });
        adminMock.getQuestions.mockResolvedValue([]);
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.getState().setDraft({ title: "New" });
        await useAdminStore.getState().saveQuestion();
        expect(adminMock.addQuestion).toHaveBeenCalled();
        expect(adminMock.updateQuestion).not.toHaveBeenCalled();
        expect(adminMock.getQuestions).toHaveBeenCalledWith("quiz-1");
        expect(useAdminStore.getState().wizardOpen).toBe(false);
    });

    test("saveQuestion updates via updateQuestion when draft has an id", async () => {
        adminMock.updateQuestion.mockResolvedValue({ id: "q1" });
        adminMock.getQuestions.mockResolvedValue([]);
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.getState().setDraft({ id: "q1", title: "Edited" });
        await useAdminStore.getState().saveQuestion();
        expect(adminMock.updateQuestion).toHaveBeenCalled();
        expect(adminMock.addQuestion).not.toHaveBeenCalled();
    });

    test("saveQuestion without active quiz skips the reload", async () => {
        adminMock.addQuestion.mockResolvedValue({ id: "new-q" });
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.setState({ activeQuizId: null });
        await useAdminStore.getState().saveQuestion();
        expect(adminMock.addQuestion).toHaveBeenCalled();
        expect(adminMock.getQuestions).not.toHaveBeenCalled();
        expect(useAdminStore.getState().wizardOpen).toBe(false);
    });

    test("createQuiz creates, reloads quizzes and returns the id", async () => {
        adminMock.createQuiz.mockResolvedValue({ id: "quiz-9" });
        adminMock.listQuizzes.mockResolvedValue([]);
        const id = await useAdminStore.getState().createQuiz("Title", "Desc");
        expect(id).toBe("quiz-9");
        expect(adminMock.createQuiz).toHaveBeenCalledWith({ title: "Title", description: "Desc" });
        expect(adminMock.listQuizzes).toHaveBeenCalled();
    });
});

describe("useAdminStore wizard workflows", () => {
    const TWELVE = [
        "MCQ",
        "TRUE_FALSE",
        "MULTIPLE_SELECT",
        "NUMERIC",
        "OUTPUT_PRED",
        "FILL_BLANK",
        "DRAG_SORT",
        "CLICK_BUG",
        "CODE_COMPLETION",
        "COMPLEXITY",
        "OJ_FULL",
        "OJ_PATCH",
    ] as const;
    const OJ_LANGS = ["c", "cpp", "java", "node", "python"];

    test("twelve-type sweep keeps statement fields and scopes languages per type", () => {
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.getState().setDraft({ title: "Keep me", description: "Keep too", timeLimitSec: 45, pointsBase: 250 });
        for (const t of TWELVE) {
            useAdminStore.getState().setType(t);
            const s = useAdminStore.getState();
            expect(s.wizardType).toBe(t);
            expect(s.draft.questionType).toBe(t);
            expect(s.draft.title).toBe("Keep me");
            expect(s.draft.description).toBe("Keep too");
            expect(s.draft.timeLimitSec).toBe(45);
            expect(s.draft.pointsBase).toBe(250);
            if (t === "OJ_FULL" || t === "OJ_PATCH") expect(s.draft.languagesAllowed).toEqual(OJ_LANGS);
            else expect(s.draft.languagesAllowed).toBeNull();
        }
    });

    test("open to triple type-switch to draft to save to reload flow", async () => {
        adminMock.addQuestion.mockResolvedValue({ id: "q-new" });
        adminMock.getQuestions.mockResolvedValue([{ id: "q-new" }]);
        useAdminStore.getState().openWizard("quiz-7");
        useAdminStore.getState().setType("NUMERIC");
        useAdminStore.getState().setType("MCQ");
        useAdminStore.getState().setType("FILL_BLANK");
        useAdminStore.getState().setDraft({ title: "Fill it", config: { snippet: "x = ___" } });
        await useAdminStore.getState().saveQuestion();
        expect(adminMock.addQuestion).toHaveBeenCalledWith(
            expect.objectContaining({ quizId: "quiz-7", questionType: "FILL_BLANK", title: "Fill it" }),
        );
        expect(adminMock.getQuestions).toHaveBeenCalledWith("quiz-7");
        expect(useAdminStore.getState().wizardOpen).toBe(false);
        expect(useAdminStore.getState().questions).toEqual([{ id: "q-new" }]);
    });

    test("type switch resets leaked config but preserves the statement", () => {
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.getState().setDraft({ title: "T", description: "D", config: { options: ["a", "b"], correctIndex: 0 } });
        useAdminStore.getState().setType("NUMERIC");
        const d = useAdminStore.getState().draft;
        expect(d.title).toBe("T");
        expect(d.description).toBe("D");
        expect(d.config).toEqual({});
        expect(d.questionType).toBe("NUMERIC");
    });

    test("MCQ to OJ switch adds the language allowlist mid-wizard", () => {
        useAdminStore.getState().openWizard("quiz-1");
        expect(useAdminStore.getState().draft.languagesAllowed).toBeNull();
        useAdminStore.getState().setType("OJ_PATCH");
        expect(useAdminStore.getState().draft.languagesAllowed).toEqual(OJ_LANGS);
        useAdminStore.getState().setType("MCQ");
        expect(useAdminStore.getState().draft.languagesAllowed).toBeNull();
    });

    test("save with an id updates then reloads and closes", async () => {
        adminMock.updateQuestion.mockResolvedValue({ id: "q1" });
        adminMock.getQuestions.mockResolvedValue([{ id: "q1", title: "Edited" }]);
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.getState().setDraft({ id: "q1", title: "Edited" });
        await useAdminStore.getState().saveQuestion();
        expect(adminMock.updateQuestion).toHaveBeenCalledWith(expect.objectContaining({ id: "q1" }));
        expect(adminMock.addQuestion).not.toHaveBeenCalled();
        expect(adminMock.getQuestions).toHaveBeenCalledWith("quiz-1");
        expect(useAdminStore.getState().wizardOpen).toBe(false);
    });

    test("save with an id but no active quiz skips the reload", async () => {
        adminMock.updateQuestion.mockResolvedValue({ id: "q1" });
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.getState().setDraft({ id: "q1" });
        useAdminStore.setState({ activeQuizId: null });
        await useAdminStore.getState().saveQuestion();
        expect(adminMock.updateQuestion).toHaveBeenCalled();
        expect(adminMock.getQuestions).not.toHaveBeenCalled();
    });

    test("failed save propagates and leaves the wizard open", async () => {
        adminMock.addQuestion.mockRejectedValue(new Error("validation failed"));
        useAdminStore.getState().openWizard("quiz-1");
        await expect(useAdminStore.getState().saveQuestion()).rejects.toThrow("validation failed");
        expect(useAdminStore.getState().wizardOpen).toBe(true);
        adminMock.addQuestion.mockResolvedValue({ id: "retry-ok" });
        adminMock.getQuestions.mockResolvedValue([]);
        await useAdminStore.getState().saveQuestion();
        expect(useAdminStore.getState().wizardOpen).toBe(false);
    });

    test("loadQuizzes error then success recovers the list", async () => {
        adminMock.listQuizzes.mockRejectedValueOnce(new Error("down"));
        await expect(useAdminStore.getState().loadQuizzes()).rejects.toThrow("down");
        adminMock.listQuizzes.mockResolvedValue([{ id: "a" }, { id: "b" }]);
        await useAdminStore.getState().loadQuizzes();
        expect(useAdminStore.getState().quizzes).toEqual([{ id: "a" }, { id: "b" }]);
    });

    test("loadQuestions switches the active quiz across two quizzes", async () => {
        adminMock.getQuestions.mockResolvedValueOnce([{ id: "qa" }]);
        await useAdminStore.getState().loadQuestions("quiz-a");
        expect(useAdminStore.getState().activeQuizId).toBe("quiz-a");
        adminMock.getQuestions.mockResolvedValueOnce([{ id: "qb" }]);
        await useAdminStore.getState().loadQuestions("quiz-b");
        expect(useAdminStore.getState().activeQuizId).toBe("quiz-b");
        expect(useAdminStore.getState().questions).toEqual([{ id: "qb" }]);
        expect(adminMock.getQuestions).toHaveBeenNthCalledWith(1, "quiz-a");
        expect(adminMock.getQuestions).toHaveBeenNthCalledWith(2, "quiz-b");
    });

    test("empty quiz id after an active quiz keeps the previous list", async () => {
        adminMock.getQuestions.mockResolvedValue([{ id: "qa" }]);
        await useAdminStore.getState().loadQuestions("quiz-a");
        await useAdminStore.getState().loadQuestions("");
        expect(useAdminStore.getState().activeQuizId).toBe("quiz-a");
        expect(useAdminStore.getState().questions).toEqual([{ id: "qa" }]);
    });

    test("createQuiz then openWizard targets the new quiz", async () => {
        adminMock.createQuiz.mockResolvedValue({ id: "quiz-fresh" });
        adminMock.listQuizzes.mockResolvedValue([{ id: "quiz-fresh" }]);
        const id = await useAdminStore.getState().createQuiz("Fresh", "New quiz");
        useAdminStore.getState().openWizard(id);
        expect(useAdminStore.getState().activeQuizId).toBe("quiz-fresh");
        expect(useAdminStore.getState().draft.quizId).toBe("quiz-fresh");
        expect(useAdminStore.getState().wizardStep).toBe("type");
    });

    test("setDraft merges successive patches without dropping keys", () => {
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.getState().setDraft({ title: "A" });
        useAdminStore.getState().setDraft({ description: "B" });
        useAdminStore.getState().setDraft({ timeLimitSec: 90, pointsBase: 300 });
        const d = useAdminStore.getState().draft;
        expect(d).toMatchObject({ title: "A", description: "B", timeLimitSec: 90, pointsBase: 300, quizId: "quiz-1" });
    });

    test("wizard walks all four steps in order", () => {
        useAdminStore.getState().openWizard("quiz-1");
        for (const step of ["type", "statement", "config", "preview"] as const) {
            useAdminStore.getState().setStep(step);
            expect(useAdminStore.getState().wizardStep).toBe(step);
        }
        expect(useAdminStore.getState().wizardOpen).toBe(true);
    });

    test("close keeps the draft then reopen resets it", () => {
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.getState().setDraft({ title: "Work in progress" });
        useAdminStore.getState().closeWizard();
        expect(useAdminStore.getState().wizardOpen).toBe(false);
        expect(useAdminStore.getState().draft.title).toBe("Work in progress");
        useAdminStore.getState().openWizard("quiz-1");
        expect(useAdminStore.getState().draft.title).toBe("");
        expect(useAdminStore.getState().wizardStep).toBe("type");
    });

    test("openWizard for another quiz swaps the draft quiz id", () => {
        useAdminStore.getState().openWizard("quiz-1");
        useAdminStore.getState().setDraft({ title: "Old quiz draft" });
        useAdminStore.getState().openWizard("quiz-2");
        expect(useAdminStore.getState().draft.quizId).toBe("quiz-2");
        expect(useAdminStore.getState().draft.title).toBe("");
        expect(useAdminStore.getState().activeQuizId).toBe("quiz-2");
    });

    test("save carries orderIndex and config into the create call", async () => {
        adminMock.addQuestion.mockResolvedValue({ id: "ordered" });
        adminMock.getQuestions.mockResolvedValue([]);
        useAdminStore.getState().openWizard("quiz-3");
        useAdminStore.getState().setDraft({ title: "Ordered", orderIndex: 4, config: { options: ["x"] } });
        await useAdminStore.getState().saveQuestion();
        expect(adminMock.addQuestion).toHaveBeenCalledWith(
            expect.objectContaining({ orderIndex: 4, config: { options: ["x"] } }),
        );
    });

    test("loadQuestions failure propagates without clobbering the old list", async () => {
        adminMock.getQuestions.mockResolvedValueOnce([{ id: "keep" }]);
        await useAdminStore.getState().loadQuestions("quiz-keep");
        adminMock.getQuestions.mockRejectedValueOnce(new Error("gone"));
        await expect(useAdminStore.getState().loadQuestions("quiz-keep")).rejects.toThrow("gone");
        expect(useAdminStore.getState().questions).toEqual([{ id: "keep" }]);
    });

    test("subscribe sees wizard open draft save and close in order", async () => {
        adminMock.addQuestion.mockResolvedValue({ id: "s" });
        adminMock.getQuestions.mockResolvedValue([]);
        const flags: boolean[] = [];
        const unsub = useAdminStore.subscribe((s) => flags.push(s.wizardOpen));
        useAdminStore.getState().openWizard("quiz-1");
        await useAdminStore.getState().saveQuestion();
        expect(flags).toEqual([true, true, false]);
        unsub();
        useAdminStore.getState().openWizard("quiz-1");
        expect(flags).toEqual([true, true, false]);
    });

    test("setType without prior statement fields falls back cleanly across OJ types", () => {
        useAdminStore.setState({ activeQuizId: "qz", draft: {} });
        useAdminStore.getState().setType("OJ_FULL");
        const d = useAdminStore.getState().draft;
        expect(d.title).toBe("");
        expect(d.timeLimitSec).toBe(30);
        expect(d.languagesAllowed).toEqual(OJ_LANGS);
        useAdminStore.getState().setType("OJ_PATCH");
        expect(useAdminStore.getState().draft.languagesAllowed).toEqual(OJ_LANGS);
    });

    test("full quiz authoring saga across two quizzes", async () => {
        adminMock.createQuiz.mockResolvedValue({ id: "saga-quiz" });
        adminMock.listQuizzes.mockResolvedValue([{ id: "saga-quiz" }]);
        adminMock.addQuestion.mockResolvedValue({ id: "saga-q" });
        adminMock.getQuestions.mockResolvedValue([{ id: "saga-q" }]);
        const id = await useAdminStore.getState().createQuiz("Saga", "End to end");
        useAdminStore.getState().openWizard(id);
        useAdminStore.getState().setType("MCQ");
        useAdminStore.getState().setStep("statement");
        useAdminStore.getState().setDraft({ title: "Saga Q", config: { options: ["a", "b"] } });
        useAdminStore.getState().setStep("preview");
        await useAdminStore.getState().saveQuestion();
        expect(useAdminStore.getState().questions).toEqual([{ id: "saga-q" }]);
        expect(useAdminStore.getState().wizardOpen).toBe(false);
    });
});
