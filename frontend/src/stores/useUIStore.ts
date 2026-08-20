import { create } from "zustand";

export type AppView = "join" | "play" | "host" | "admin";

interface UIState {
  view: AppView;
  theme: "light" | "dark";
  modal: string | null;
  pin: string | null;
  setView: (v: AppView) => void;
  setTheme: (t: "light" | "dark") => void;
  openModal: (m: string | null) => void;
  setPin: (p: string | null) => void;
}

export const useUIStore = create<UIState>((set) => ({
  view: "join",
  theme: "light",
  modal: null,
  pin: null,
  setView: (view) => set({ view }),
  setTheme: (theme) => set({ theme }),
  openModal: (modal) => set({ modal }),
  setPin: (pin) => set({ pin }),
}));
