import { create } from "zustand";

export type AppView = "join" | "play" | "host" | "admin" | "admin-login";

/** Detects the initial view from the URL path. */
function detectInitialView(): AppView {
    const path = window.location.pathname;
    if (path === "/admin/login") return "admin-login";
    if (path.startsWith("/admin")) return "admin";
    return "join";
}

const THEME_KEY = "oq-theme";

/** Reads the persisted theme; falls back to the OS preference, dark if unset. */
function readTheme(): "light" | "dark" {
    try {
        const t = localStorage.getItem(THEME_KEY);
        if (t === "light" || t === "dark") return t;
    } catch {
        /* ignore */
    }
    try {
        return window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
    } catch {
        return "dark";
    }
}

/** Applies the persisted (or OS) theme to <html> (called once at boot). */
export function applyStoredTheme(): void {
    const t = readTheme();
    document.documentElement.classList.toggle("dark", t === "dark");
}

/** Live-syncs the theme with OS changes until the user picks manually. */
export function watchSystemTheme(): void {
    try {
        const mq = window.matchMedia("(prefers-color-scheme: light)");
        mq.addEventListener("change", (e) => {
            if (!localStorage.getItem(THEME_KEY)) {
                document.documentElement.classList.toggle("dark", !e.matches);
            }
        });
    } catch {
        /* ignore */
    }
}

interface UIState {
    view: AppView;
    theme: "light" | "dark";
    modal: string | null;
    pin: string | null;
    setView: (v: AppView) => void;
    setTheme: (t: "light" | "dark") => void;
    toggleTheme: () => void;
    openModal: (m: string | null) => void;
    setPin: (p: string | null) => void;
}

export const useUIStore = create<UIState>((set, get) => ({
    view: detectInitialView(),
    theme: readTheme(),
    modal: null,
    pin: null,
    setView: (view) => set({ view }),
    setTheme: (theme) => {
        document.documentElement.classList.toggle("dark", theme === "dark");
        try {
            localStorage.setItem(THEME_KEY, theme);
        } catch {
            /* ignore */
        }
        set({ theme });
    },
    toggleTheme: () => get().setTheme(get().theme === "dark" ? "light" : "dark"),
    openModal: (modal) => set({ modal }),
    setPin: (pin) => set({ pin }),
}));
