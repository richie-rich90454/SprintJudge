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

export interface RoomState {
  type: "ROOM_STATE";
  status: GameStatus;
  questionCount: number;
  players: PlayerInfo[];
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

export interface GameState {
  status: GameStatus;
  pin: string | null;
  playerUuid: string | null;
  playerName: string | null;
  quizId: string | null;
  currentQuestion: QuestionDto | null;
  leaderboard: LeaderboardEntry[];
  room: RoomState | null;
  lastResult: unknown | null;
  error: string | null;
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
