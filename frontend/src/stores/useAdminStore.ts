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
  languagesAllowed: type === "OJ_FULL" || type === "OJ_PATCH" ? ["c", "cpp", "java", "node", "python"] : null,
  timeLimitSec: 30,
  pointsBase: 100,
  config: {},
  orderIndex: 0,
});

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
    const data = await adminApi.getQuiz(quizId);
    set({ questions: data.questions, activeQuizId: quizId });
  },

  openWizard: (quizId) => {
    set({ wizardOpen: true, wizardStep: "type", wizardType: "MCQ", draft: emptyDraft("MCQ", quizId), activeQuizId: quizId });
  },

  closeWizard: () => set({ wizardOpen: false }),

  setStep: (wizardStep) => set({ wizardStep }),

  setType: (wizardType) => {
    const quizId = get().activeQuizId ?? "";
    set({ wizardType, draft: { ...emptyDraft(wizardType, quizId), ...get().draft, questionType: wizardType } });
  },

  setDraft: (patch) => set({ draft: { ...get().draft, ...patch } }),

  saveQuestion: async () => {
    const draft = get().draft as QuestionPayload;
    if (draft.id) await adminApi.updateQuestion(draft);
    else await adminApi.addQuestion(draft);
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
