export interface StudentReport {
    playerUuid: string;
    playerName: string;
    sessions: number;
    attempts: number;
    correct: number;
    accuracy: number;
    avgScore: number;
    avgAttemptsPerItem: number;
}

export interface SessionStat {
    sessionId: string;
    hostUserId: string;
    players: number;
    questions: number;
    attempts: number;
    accuracy: number;
    avgScore: number;
}

export interface ClassReport {
    sessionCount: number;
    players: number;
    questions: number;
    attempts: number;
    correct: number;
    accuracy: number;
    avgScore: number;
    hardestQuestionId: string;
    easiestQuestionId: string;
    sessionStats: SessionStat[];
}

export interface ItemReport {
    questionId: string;
    bankId: string;
    title: string;
    unit: string;
    topic: string;
    attempts: number;
    correct: number;
    pValue: number;
    discrimination: number;
    avgAttempts: number;
    bankDifficulty: number | null;
    bankDiscrimination: number | null;
    p25Sec: number | null;
    medianSec: number | null;
    p75Sec: number | null;
}

export type AdaptiveMode = "balanced" | "adaptive";

export interface AdaptiveSetRequest {
    subject: string;
    unit?: string;
    size: number;
    mode: AdaptiveMode;
    targetDifficulty: number;
    title?: string;
    seed?: number;
}

export interface AdaptiveSetResult {
    quizId: string;
    questionCount: number;
    skipped: number;
    avgDifficulty: number;
    avgDiscrimination: number;
    selectedIds: string[];
    difficultyBands: Record<string, number>;
}

/** Echo the Spring CSRF cookie, exactly like the Axios admin clients. */
function xsrfHeaders(): Record<string, string> {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return match ? { "X-XSRF-TOKEN": match[1] } : {};
}

/** Same-origin cookies and JSON error messages as the other admin services. */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
    let res: Response;
    try {
        res = await fetch(`/api${path}`, {
            credentials: "include",
            headers: { "Content-Type": "application/json", ...xsrfHeaders() },
            ...init,
        });
    } catch {
        throw new Error("Network Error");
    }
    if (!res.ok) throw new Error(`Request failed with status code ${res.status}`);
    return (await res.json()) as T;
}

export function getStudentReports(sessionId?: string): Promise<StudentReport[]> {
    const q = sessionId?.trim() ? `?sessionId=${encodeURIComponent(sessionId.trim())}` : "";
    return request<StudentReport[]>(`/admin/analytics/students${q}`);
}

export function getClassReport(sessionId?: string): Promise<ClassReport> {
    const q = sessionId?.trim() ? `?sessionId=${encodeURIComponent(sessionId.trim())}` : "";
    return request<ClassReport>(`/admin/analytics/classes${q}`);
}

export function getItemReports(subject: string): Promise<ItemReport[]> {
    return request<ItemReport[]>(`/admin/analytics/items?subject=${encodeURIComponent(subject)}`);
}

export function buildAdaptiveSet(payload: AdaptiveSetRequest): Promise<AdaptiveSetResult> {
    return request<AdaptiveSetResult>("/admin/analytics/adaptive-set", {
        method: "POST",
        body: JSON.stringify(payload),
    });
}
