import { create } from "zustand";
import { gameStateManager } from "../game/GameStateManager";
import { GameState } from "../types";

interface GameStore extends GameState {
    connect: (url: string) => void;
    join: (pin: string, name: string, role?: "player" | "host" | "spectator") => void;
    resume: () => boolean;
    submit: (questionId: string, response: unknown, language?: string) => void;
    hostCommand: (
        action: "NEXT_QUESTION" | "FORCE_SUBMIT" | "END_GAME",
        payload?: Record<string, unknown>,
    ) => void;
    extendTimer: (seconds: number) => void;
    kick: (uuid: string) => void;
    joinTeam: (teamId: string) => void;
    leaveTeam: () => void;
    createTeam: (name: string) => void;
    requestTeams: () => void;
    requestBracket: () => void;
    clearError: () => void;
}
export const useGameStore = create<GameStore>((set) => {
    gameStateManager.observe().subscribe((s) => set(s));
    return {
        ...gameStateManager.state,
        connect: (url) => gameStateManager.connect(url),
        join: (pin, name, role) => {
            useGameStore.setState({ pin, playerName: name });
            gameStateManager.join(pin, name, role ?? "player");
        },
        resume: () => gameStateManager.resume(),
        submit: (questionId, response, language) =>
            gameStateManager.submit(questionId, response, language),
        hostCommand: (action, payload) => gameStateManager.hostCommand(action, payload),
        extendTimer: (seconds) => gameStateManager.extendTimer(seconds),
        kick: (uuid) => gameStateManager.kickPlayer(uuid),
        joinTeam: (teamId) => gameStateManager.joinTeam(teamId),
        leaveTeam: () => gameStateManager.leaveTeam(),
        createTeam: (name) => gameStateManager.createTeam(name),
        requestTeams: () => gameStateManager.requestTeams(),
        requestBracket: () => gameStateManager.requestBracket(),
        clearError: () => set({ error: null }),
    };
});
