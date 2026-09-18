export type BankScope = "current-scope" | "legacy-scope" | "core-scope" | "everything";

export interface BankSearchParams {
    subject: string;
    unit?: string;
    scope?: BankScope;
    format?: string;
}

/** Answer-free header: teachers browse titles, never answers. */
export interface BankQuestionHeader {
    id: string;
    subject: string;
    unit: string;
    topic: string;
    scope: string;
    format: string;
    stem: string;
}

export interface BankCoverageUnit {
    unit: string;
    count: number;
    formats: string[];
    scopes: string[];
}

export interface BankCoverage {
    subject: string;
    total: number;
    units: BankCoverageUnit[];
}

/**
 * Shared fetch helper mirroring AdminApiService conventions: same-origin
 * cookies on every call, JSON everywhere, axios-style error messages so
 * callers keep matching on status codes ("404") and "Network Error".
 */
async function request<T>(path: string): Promise<T> {
    let res: Response;
    try {
        res = await fetch(`/api${path}`, {
            credentials: "include",
            headers: { "Content-Type": "application/json" },
        });
    } catch {
        throw new Error("Network Error");
    }
    if (!res.ok) throw new Error(`Request failed with status code ${res.status}`);
    return (await res.json()) as T;
}

/** Answer-free header search by subject, unit, scope, and format. */
export function searchBank(params: BankSearchParams): Promise<BankQuestionHeader[]> {
    const q = new URLSearchParams({ subject: params.subject });
    if (params.unit?.trim()) q.set("unit", params.unit.trim());
    if (params.scope) q.set("scope", params.scope);
    if (params.format) q.set("format", params.format);
    return request<BankQuestionHeader[]>(`/admin/bank/search?${q.toString()}`);
}

/** One answer-free question header for teachers. */
export function getBankQuestion(id: string): Promise<BankQuestionHeader> {
    return request<BankQuestionHeader>(`/admin/bank/question/${encodeURIComponent(id)}`);
}

/** Wire shape for one coverage unit, keyed by unit name. */
interface BankCoverageWireUnit {
    total: number;
    formats: string[];
    scopes: string[];
}

/** Format and scope coverage per unit (server shape: unit name to stats). */
export async function getBankCoverage(subject: string): Promise<BankCoverage> {
    const wire = await request<Record<string, BankCoverageWireUnit>>(
        `/admin/bank/coverage/${encodeURIComponent(subject)}`,
    );
    const units = Object.entries(wire).map(([unit, stats]) => ({
        unit,
        count: stats.total,
        formats: stats.formats,
        scopes: stats.scopes,
    }));
    const total = units.reduce((sum, u) => sum + u.count, 0);
    return { subject, total, units };
}

/** Question totals per subject. */
export function getBankVolume(): Promise<Record<string, number>> {
    return request<Record<string, number>>("/admin/bank/volume");
}
