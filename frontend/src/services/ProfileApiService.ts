export interface ProfileIdentity {
    id: string;
    token: string;
}

export interface ProfileSummary {
    id: string;
    nickname: string;
    playerUuid: string | null;
    linkToken: string;
    createdAt: number;
}

export interface ProfileStats {
    sessions: number;
    totalScore: number;
    bestScore: number;
    answers: number;
    correct: number;
    accuracy: number;
    bestStreak: number;
}

export interface ScoreEntry {
    sessionId: string;
    quizId: string;
    quizTitle: string | null;
    score: number;
    correct: number;
    answers: number;
    playedAt: number;
}

export interface Achievement {
    id: string;
    title: string;
    description: string;
    unlocked: boolean;
}

export interface PortfolioEntry {
    quizId: string;
    quizTitle: string | null;
    sessions: number;
    bestScore: number;
    lastPlayedAt: number;
}

export interface ProfileReport {
    profile: ProfileSummary;
    stats: ProfileStats;
    history: ScoreEntry[];
    achievements: Achievement[];
    portfolio: PortfolioEntry[];
}

const ID_KEY = "player_profile_id";
const TOKEN_KEY = "player_profile_token";

/** Local capability credentials; no password and no login is involved. */
export function storedIdentity(): ProfileIdentity | null {
    try {
        const id = localStorage.getItem(ID_KEY);
        const token = localStorage.getItem(TOKEN_KEY);
        return id && token ? { id, token } : null;
    } catch {
        return null;
    }
}

export function storeIdentity(identity: ProfileIdentity): void {
    try {
        localStorage.setItem(ID_KEY, identity.id);
        localStorage.setItem(TOKEN_KEY, identity.token);
    } catch {
        // Storage disabled: the profile still works until this tab closes.
    }
}

export function forgetIdentity(): void {
    try {
        localStorage.removeItem(ID_KEY);
        localStorage.removeItem(TOKEN_KEY);
    } catch {
        // Nothing to forget when storage is unavailable.
    }
}

function bearer(identity: ProfileIdentity): string {
    return `Bearer ${identity.token}`;
}

async function request<T>(path: string, identity?: ProfileIdentity, body?: Record<string, string>): Promise<T> {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (identity) headers.Authorization = bearer(identity);
    let res: Response;
    try {
        res = await fetch(`/api/public/profiles${path}`, {
            method: body ? "POST" : "GET",
            credentials: "include",
            headers,
            body: body ? JSON.stringify(body) : undefined,
        });
    } catch {
        throw new Error("Network Error");
    }
    if (!res.ok) throw new Error(`Request failed with status code ${res.status}`);
    return (await res.json()) as T;
}

/** Creates an opt-in profile. Linking needs the live rejoin token from a game. */
export function createProfile(
    nickname: string,
    playerUuid?: string,
    rejoinToken?: string,
): Promise<ProfileReport> {
    const body: Record<string, string> = { nickname };
    if (playerUuid) body.playerUuid = playerUuid;
    if (rejoinToken) body.rejoinToken = rejoinToken;
    return request<ProfileReport>("", undefined, body);
}

export function fetchProfile(identity: ProfileIdentity): Promise<ProfileReport> {
    return request<ProfileReport>(`/${encodeURIComponent(identity.id)}`, identity);
}

export function updateProfile(
    identity: ProfileIdentity,
    changes: { nickname?: string; playerUuid?: string; rejoinToken?: string },
): Promise<ProfileReport> {
    const body: Record<string, string> = {};
    if (changes.nickname !== undefined) body.nickname = changes.nickname;
    if (changes.playerUuid) body.playerUuid = changes.playerUuid;
    if (changes.rejoinToken) body.rejoinToken = changes.rejoinToken;
    return patch<ProfileReport>(`/${encodeURIComponent(identity.id)}`, identity, body);
}

async function patch<T>(path: string, identity: ProfileIdentity, body: Record<string, string>): Promise<T> {
    let res: Response;
    try {
        res = await fetch(`/api/public/profiles${path}`, {
            method: "PATCH",
            credentials: "include",
            headers: { "Content-Type": "application/json", Authorization: bearer(identity) },
            body: JSON.stringify(body),
        });
    } catch {
        throw new Error("Network Error");
    }
    if (!res.ok) throw new Error(`Request failed with status code ${res.status}`);
    return (await res.json()) as T;
}
