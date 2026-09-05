export type QuestionType =
    | "MCQ"
    | "TRUE_FALSE"
    | "MULTIPLE_SELECT"
    | "NUMERIC"
    | "OUTPUT_PRED"
    | "FILL_BLANK"
    | "DRAG_SORT"
    | "CLICK_BUG"
    | "CODE_COMPLETION"
    | "COMPLEXITY"
    | "OJ_FULL"
    | "OJ_PATCH";

export type GameStatus = "LOBBY" | "ACTIVE" | "REVIEW" | "ENDED";

export interface QuestionDto {
    id: string;
    type: QuestionType;
    title: string;
    description: string;
    timeLimitSec: number;
    pointsBase: number;
    languagesAllowed: string[] | null;
    config: unknown;
}

export interface PlayerInfo {
    uuid: string;
    name: string;
    score: number;
}

export type GameMode = "STANDARD" | "AUTO_PILOT" | "PRACTICE" | "EXAM" | "TEAM" | "BATTLE";

export interface RoomState {
    type: "ROOM_STATE";
    status: GameStatus;
    questionCount: number;
    currentQuestionId: string | null;
    players: PlayerInfo[];
    gameMode: GameMode;
}

export interface QuestionStart {
    type: "QUESTION_START";
    question: QuestionDto;
    timeLimitSec: number;
    startedAtEpochMs: number;
}

export interface LeaderboardEntry {
    uuid: string;
    name: string;
    score: number;
    rank: number;
}

export interface LeaderboardDelta {
    type: "LEADERBOARD_DELTA";
    seq: number;
    resync: boolean;
    entries: LeaderboardEntry[];
}

export interface SubmissionResult {
    submission: {
        questionId: string;
        allPassed: boolean;
        score: number;
        passed?: number;
        totalTests?: number;
        aiFeedback?: string;
    };
}

export interface GameState {
    status: GameStatus;
    pin: string | null;
    playerUuid: string | null;
    rejoinToken: string | null;
    playerName: string | null;
    role: "player" | "host";
    quizId: string | null;
    currentQuestion: QuestionDto | null;
    leaderboard: LeaderboardEntry[];
    room: RoomState | null;
    lastResult: SubmissionResult | null;
    error: string | null;
    gameMode: GameMode;
    review: GameReview | null;
}

export interface GameReview {
    type: "GAME_REVIEW";
    rankings: LeaderboardEntry[];
    questions: QuestionReview[];
    players: PlayerReview[];
    classStats: ClassStats;
}

export interface QuestionReview {
    questionId: string;
    title: string;
    questionType: string;
    timeLimitSec: number;
    pointsBase: number;
    answer: unknown;
    totalAttempts: number;
    correctCount: number;
    correctRate: number;
    avgAttempts: number;
    options: string[] | null;
}

export interface PlayerReview {
    playerUuid: string;
    playerName: string;
    totalScore: number;
    answers: PlayerAnswer[];
}

export interface PlayerAnswer {
    questionId: string;
    correct: boolean;
    scoreEarned: number;
    attemptCount: number;
}

export interface ClassStats {
    totalPlayers: number;
    totalQuestions: number;
    avgScore: number;
    totalCorrect: number;
    totalAttempts: number;
    hardestQuestionId: string;
    easiestQuestionId: string;
}

export const ALL_QUESTION_TYPES: QuestionType[] = [
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
];
