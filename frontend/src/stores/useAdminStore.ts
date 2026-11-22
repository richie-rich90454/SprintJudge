import { create } from "zustand";
import { adminApi, QuestionPayload, QuizDto } from "../services/AdminApiService";
import { QuestionType, ALL_QUESTION_TYPES } from "../types";

export type WizardStep = "type" | "statement" | "config" | "preview";

interface AdminStore {
    quizzes: QuizDto[];
    questions: QuestionPayload[];
    activeQuizId: string | null;
    wizardOpen: boolean;
    wizardStep: WizardStep;
    wizardType: QuestionType;
    draft: Partial<QuestionPayload>;
    loadQuizzes: () => Promise<void>;
    loadQuestions: (quizId: string) => Promise<void>;
    openWizard: (quizId: string) => void;
    closeWizard: () => void;
    setStep: (s: WizardStep) => void;
    setType: (t: QuestionType) => void;
    setDraft: (patch: Partial<QuestionPayload>) => void;
    saveQuestion: () => Promise<void>;
    createQuiz: (title: string, description: string) => Promise<string>;
}

const emptyDraft = (type: QuestionType, quizId: string): Partial<QuestionPayload> => ({
    quizId,
    title: "",
    description: "",
    questionType: type,
    languagesAllowed:
        type === "OJ_FULL" || type === "OJ_PATCH" ? ["c", "cpp", "java", "node", "python"] : null,
    timeLimitSec: 30,
    pointsBase: 100,
    config: defaultConfig(type),
    orderIndex: 0,
});

// The wizard renders these as `??` display defaults. Persist them into the
// draft so a question saved without touching a field matches what was shown.
function defaultConfig(type: string): Record<string, unknown> {
    switch (type) {
        case "MCQ":
        case "COMPLEXITY":
            return { options: ["", "", "", ""], correctIndex: 0 };
        case "OUTPUT_PRED":
            return { correctIndex: 0 };
        case "TRUE_FALSE":
            return { correct: true };
        case "MULTIPLE_SELECT":
            return { options: ["", "", "", ""], correctIndices: [0] };
        case "NUMERIC":
            return { answer: 0, tolerance: 0 };
        case "FILL_BLANK":
            return { answer: "" };
        case "DRAG_SORT":
            return { lines: [{ id: "0", text: "" }, { id: "1", text: "" }], correctOrder: [0, 1] };
        case "CLICK_BUG":
            return { codeLines: [""], bugLine: 0 };
        case "CODE_COMPLETION":
            return { skeleton: "", expected: "" };
        case "OJ_FULL":
        case "OJ_PATCH":
            return { testCases: [{ input: "", expectedOutput: "" }] };
        default:
            return {};
    }
}

export const useAdminStore = create<AdminStore>((set, get) => ({
    quizzes: [],
    questions: [],
    activeQuizId: null,
    wizardOpen: false,
    wizardStep: "type",
    wizardType: "MCQ",
    draft: emptyDraft("MCQ", ""),

    loadQuizzes: async () => {
        const quizzes = await adminApi.listQuizzes();
        set({ quizzes });
    },

    loadQuestions: async (quizId) => {
        if (!quizId) return;
        const questions = await adminApi.getQuestions(quizId);
        set({ questions, activeQuizId: quizId });
    },

    openWizard: (quizId) => {
        set({
            wizardOpen: true,
            wizardStep: "type",
            wizardType: "MCQ",
            draft: emptyDraft("MCQ", quizId),
            activeQuizId: quizId,
        });
    },

    closeWizard: () => set({ wizardOpen: false }),

    setStep: (wizardStep) => set({ wizardStep }),

    setType: (wizardType) => {
        const quizId = get().activeQuizId ?? "";
        const prev = get().draft;
        set({
            wizardType,
            // Statement fields carry over; type-specific config resets so an
            // MCQ options array can never leak into a NUMERIC draft.
            draft: {
                ...emptyDraft(wizardType, quizId),
                title: prev.title ?? "",
                description: prev.description ?? "",
                timeLimitSec: prev.timeLimitSec ?? 30,
                pointsBase: prev.pointsBase ?? 100,
                questionType: wizardType,
            },
        });
    },

    setDraft: (patch) => set({ draft: { ...get().draft, ...patch } }),

    saveQuestion: async () => {
        const draft = get().draft as QuestionPayload;
        const config = { ...((draft.config as Record<string, unknown> | null) ?? {}) };
        // The wizard edits these as text; the validator and evaluator expect arrays.
        if (typeof config["correctOrder"] === "string") {
            config["correctOrder"] = String(config["correctOrder"])
                .split(",")
                .map((part) => Number(part.trim()))
                .filter((n) => Number.isFinite(n));
        }
        if (typeof config["correctIndices"] === "string") {
            config["correctIndices"] = String(config["correctIndices"])
                .split(",")
                .map((part) => Number(part.trim()))
                .filter((n) => Number.isFinite(n));
        }
        const payload = { ...draft, config } as QuestionPayload;
        if (draft.id) await adminApi.updateQuestion(payload);
        else await adminApi.addQuestion(payload);
        const activeQuizId = get().activeQuizId;
        if (activeQuizId) await get().loadQuestions(activeQuizId);
        set({ wizardOpen: false });
    },

    createQuiz: async (title, description) => {
        const q = await adminApi.createQuiz({ title, description });
        await get().loadQuizzes();
        return q.id;
    },
}));

export { ALL_QUESTION_TYPES };
