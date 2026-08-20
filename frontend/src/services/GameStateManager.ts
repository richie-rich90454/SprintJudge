import { BehaviorSubject } from "rxjs";
import { webSocketService, WsMessage } from "./WebSocketService";
import {
  GameState,
  QuestionDto,
  RoomState,
  LeaderboardEntry,
} from "../types";

const initial: GameState = {
  status: "LOBBY",
  pin: null,
  playerUuid: null,
  playerName: null,
  quizId: null,
  currentQuestion: null,
  questionEndEpochMs: null,
  leaderboard: [],
  room: null,
  lastResult: null,
  error: null,
};

export class GameStateManager {
  private static _instance: GameStateManager | null = null;
  private readonly state$ = new BehaviorSubject<GameState>(initial);

  static get instance(): GameStateManager {
    if (!this._instance) this._instance = new GameStateManager();
    return this._instance;
  }

  constructor() {
    webSocketService.onMessage().subscribe((m) => this.dispatch(m));
  }

  get state(): GameState {
    return this.state$.value;
  }

  observe() {
    return this.state$.asObservable();
  }

  connect(url: string) {
    webSocketService.connect(url);
  }

  join(pin: string, name: string, role: "player" | "host" = "player") {
    webSocketService.send({ type: "JOIN", role, name, pin });
  }

  submit(questionId: string, response: unknown, language?: string) {
    webSocketService.send({ type: "SUBMIT", questionId, response, language });
  }

  hostCommand(action: "NEXT_QUESTION" | "FORCE_SUBMIT" | "END_GAME", payload?: Record<string, unknown>) {
    webSocketService.send({ type: action, ...(payload ?? {}) } as WsMessage);
  }

  extendTimer(seconds: number) {
    webSocketService.send({ type: "EXTEND_TIMER", seconds });
  }

  kickPlayer(playerUuid: string) {
    webSocketService.send({ type: "KICK_PLAYER", playerUuid });
  }

  private patch(p: Partial<GameState>) {
    this.state$.next({ ...this.state$.value, ...p });
  }

  private dispatch(m: WsMessage) {
    switch (m.type) {
      case "JOINED":
        this.patch({
          playerUuid: m.uuid as string,
          room: m.room as unknown as RoomState,
          status: (m.room as unknown as RoomState)?.status ?? "LOBBY",
        });
        break;
      case "ROOM_STATE":
        this.patch({ room: m as unknown as RoomState });
        break;
      case "QUESTION_START": {
        const q = m.question as QuestionDto;
        const end = (m.startedAtEpochMs as number) + (m.timeLimitSec as number) * 1000;
        this.patch({
          status: "ACTIVE",
          currentQuestion: q,
          questionEndEpochMs: end,
          lastResult: null,
          error: null,
        });
        break;
      }
      case "LEADERBOARD":
        this.patch({ leaderboard: m.rankings as LeaderboardEntry[] });
        break;
      case "ROUND_RESULT":
        this.patch({ status: "REVIEW", lastResult: m });
        break;
      case "GAME_END":
        this.patch({ status: "ENDED", leaderboard: m.rankings as LeaderboardEntry[], currentQuestion: null });
        break;
      case "TIMER_UPDATE":
        this.patch({ questionEndEpochMs: m.newEndEpochMs as number });
        break;
      case "ERROR":
        this.patch({ error: m.message as string });
        break;
    }
  }
}

export const gameStateManager = GameStateManager.instance;
