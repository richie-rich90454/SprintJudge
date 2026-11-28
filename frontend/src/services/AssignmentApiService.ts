import axios, { AxiosInstance } from "axios";

/** Async assignment: scheduled release, optional due date, created from a quiz. */
export interface Assignment {
    id: string;
    quizId: string;
    title: string;
    description: string | null;
    releaseAt: number | null;
    dueAt: number | null;
    createdBy: string;
    createdAt: number;
    ltiDeploymentId: string | null;
}

export interface RosterStudent {
    id: string;
    assignmentId: string;
    studentId: string;
    name: string;
    email: string | null;
}

export interface StudentProgress {
    id: string;
    assignmentId: string;
    studentId: string;
    status: "NOT_STARTED" | "IN_PROGRESS" | "SUBMITTED" | "GRADED";
    startedAt: number | null;
    submittedAt: number | null;
    score: number | null;
    maxScore: number | null;
    gradedAt: number | null;
}

export interface GradeResult {
    assignmentId: string;
    studentId: string;
    score: number;
    maxScore: number;
    grade: string;
    passbackPayload: string;
}

export interface LtiLaunchResult {
    valid: boolean;
    issuer: string;
    audience: string;
    subject: string;
    deploymentId: string;
    contextId?: string;
    signedLaunch: string;
}

/** Axios client for the assignment admin/public REST surface. */
export class AssignmentApiService {
    private static _instance: AssignmentApiService | null = null;
    private readonly client: AxiosInstance;

    static get instance(): AssignmentApiService {
        if (!this._instance) this._instance = new AssignmentApiService();
        return this._instance;
    }

    constructor(base = "/api") {
        this.client = axios.create({
            baseURL: base,
            headers: { "Content-Type": "application/json" },
            withCredentials: true,
            xsrfCookieName: "XSRF-TOKEN",
            xsrfHeaderName: "X-XSRF-TOKEN",
        });
    }

    list(): Promise<Assignment[]> {
        return this.client.get<Assignment[]>("/admin/assignments").then((r) => r.data);
    }

    create(input: {
        quizId: string;
        title: string;
        description?: string;
        releaseAt?: number | null;
        dueAt?: number | null;
        ltiDeploymentId?: string;
    }): Promise<Assignment> {
        return this.client.post<Assignment>("/admin/assignments", input).then((r) => r.data);
    }

    get(id: string): Promise<Assignment> {
        return this.client
            .get<Assignment>(`/admin/assignments/${encodeURIComponent(id)}`)
            .then((r) => r.data);
    }

    roster(id: string): Promise<RosterStudent[]> {
        return this.client
            .get<RosterStudent[]>(`/admin/assignments/${encodeURIComponent(id)}/roster`)
            .then((r) => r.data);
    }

    importRoster(id: string, csv: string): Promise<{ imported: number }> {
        return this.client
            .post<{ imported: number }>(`/admin/assignments/${encodeURIComponent(id)}/roster`, {
                csv,
            })
            .then((r) => r.data);
    }

    progress(id: string): Promise<StudentProgress[]> {
        return this.client
            .get<StudentProgress[]>(`/admin/assignments/${encodeURIComponent(id)}/progress`)
            .then((r) => r.data);
    }

    recordGrade(
        id: string,
        studentId: string,
        score: number,
        maxScore: number,
    ): Promise<GradeResult> {
        return this.client
            .post<GradeResult>(`/admin/assignments/${encodeURIComponent(id)}/grades`, {
                studentId,
                score,
                maxScore,
            })
            .then((r) => r.data);
    }

    start(id: string, studentId: string): Promise<StudentProgress> {
        return this.client
            .post<StudentProgress>(`/public/assignments/${encodeURIComponent(id)}/start`, {
                studentId,
            })
            .then((r) => r.data);
    }

    studentProgress(id: string, studentId: string): Promise<StudentProgress> {
        const q = new URLSearchParams({ studentId });
        return this.client
            .get<StudentProgress>(
                `/public/assignments/${encodeURIComponent(id)}/progress?${q.toString()}`,
            )
            .then((r) => r.data);
    }

    ltiLaunch(claims: Record<string, unknown>): Promise<LtiLaunchResult> {
        return this.client
            .post<LtiLaunchResult>("/public/assignments/lti/launch", claims)
            .then((r) => r.data);
    }
}

export const assignmentApi = AssignmentApiService.instance;
