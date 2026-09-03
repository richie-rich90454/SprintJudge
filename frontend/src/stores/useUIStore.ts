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

const THEME_COLORS = { light: "#f5f4f1", dark: "#0b0d12" } as const;

/** Syncs the theme-color meta with the active theme (no token disagreement). */
function syncThemeColor(t: "light" | "dark"): void {
    try {
        let meta = document.querySelector('meta[name="theme-color"]');
        if (!meta) {
            meta = document.createElement("meta");
            meta.setAttribute("name", "theme-color");
            document.head.appendChild(meta);
        }
        meta.setAttribute("content", THEME_COLORS[t]);
    } catch {
        /* ignore */
    }
}

/** Reads the persisted theme; dark is the single default (matches index.html). */
function readTheme(): "light" | "dark" {
    try {
        const t = localStorage.getItem(THEME_KEY);
        if (t === "light" || t === "dark") return t;
    } catch {
        /* ignore */
    }
    return "dark";
}

/** Applies the persisted theme to <html> (called once at boot). */
export function applyStoredTheme(): void {
    const t = readTheme();
    document.documentElement.classList.toggle("dark", t === "dark");
    syncThemeColor(t);
}

/** Live-syncs the theme-color meta with OS changes until the user picks manually. */
export function watchSystemTheme(): void {
    try {
        syncThemeColor(readTheme());
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
        syncThemeColor(theme);
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
