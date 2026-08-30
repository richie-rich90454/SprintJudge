import axios, { AxiosInstance } from "axios";

export interface QuizDto {
    id: string;
    title: string;
    description: string;
    createdBy?: string;
    createdAt?: number;
    template?: boolean;
}

export interface QuestionPayload {
    id?: string;
    quizId: string;
    title: string;
    description: string;
    questionType: string;
    languagesAllowed: string[] | null;
    timeLimitSec: number;
    pointsBase: number;
    config: unknown;
    orderIndex: number;
}

/**
 * Thin Axios wrapper around the SprintJudge admin + public REST surface.
 */
export class AdminApiService {
    private static _instance: AdminApiService | null = null;
    private readonly client: AxiosInstance;

    static get instance(): AdminApiService {
        if (!this._instance) this._instance = new AdminApiService();
        return this._instance;
    }

    constructor(base = "/api") {
        this.client = axios.create({
            baseURL: base,
            headers: { "Content-Type": "application/json" },
        });
    }

    listQuizzes() {
        return this.client.get<QuizDto[]>("/public/quizzes").then((r) => r.data);
    }

    getQuestions(quizId: string) {
        return this.client
            .get<QuestionPayload[]>(`/admin/quizzes/${quizId}/questions`)
            .then((r) => r.data);
    }

    createQuiz(quiz: Partial<QuizDto>) {
        return this.client.post<QuizDto>("/admin/quizzes", quiz).then((r) => r.data);
    }

    deleteQuiz(id: string) {
        return this.client.delete(`/admin/quizzes/${id}`);
    }

    addQuestion(q: QuestionPayload) {
        return this.client
            .post<QuestionPayload>(`/admin/quizzes/${q.quizId}/questions`, q)
            .then((r) => r.data);
    }

    updateQuestion(q: QuestionPayload) {
        return this.client.put<QuestionPayload>(`/admin/questions/${q.id}`, q).then((r) => r.data);
    }

    deleteQuestion(id: string) {
        return this.client.delete(`/admin/questions/${id}`);
    }

    getSettings() {
        return this.client.get<Record<string, string>>("/admin/settings").then((r) => r.data);
    }

    updateSettings(settings: Record<string, string>) {
        return this.client.put("/admin/settings", settings);
    }

    createGame(quizId: string, gameMode: "STANDARD" | "AUTO_PILOT" = "STANDARD") {
        // Host identity is resolved server-side from the authenticated session.
        return this.client
            .post<{ id: string; pinCode: string }>("/admin/games", { quizId, gameMode })
            .then((r) => r.data);
    }

    exportBank() {
        return this.client.get<string>("/admin/export").then((r) => r.data);
    }

    importBank(json: string, replace: boolean) {
        return this.client
            .post<{ importedQuestions: number }>("/admin/import", { json, replace })
            .then((r) => r.data);
    }
}

export const adminApi = AdminApiService.instance;
