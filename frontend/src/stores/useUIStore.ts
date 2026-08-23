import { create } from "zustand";

export type AppView = "join" | "play" | "host" | "admin";

/** Detects the initial view from the URL path so OAuth redirects land correctly. */
function detectInitialView(): AppView {
  const path = window.location.pathname;
  if (path.startsWith("/admin")) return "admin";
  return "join";
}

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
  view: detectInitialView(),
  theme: "light",
  modal: null,
  pin: null,
  setView: (view) => set({ view }),
  setTheme: (theme) => set({ theme }),
  openModal: (modal) => set({ modal }),
  setPin: (pin) => set({ pin }),
}));
