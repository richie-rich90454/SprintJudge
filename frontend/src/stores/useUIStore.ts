import { create } from "zustand";

const THEME_KEY = "oq-theme";
const SOUND_KEY = "oq-sound";
const MOTION_KEY = "oq-motion";
const AVATAR_KEY = "oq-avatar";

const THEME_COLORS = { light: "#f7f6f2", dark: "#0c0f14" } as const;

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

function readSound(): "on" | "off" {
    try {
        if (localStorage.getItem(SOUND_KEY) === "off") return "off";
    } catch {
        /* ignore */
    }
    return "on";
}

function readMotion(): "full" | "reduced" | "system" {
    try {
        const m = localStorage.getItem(MOTION_KEY);
        if (m === "full" || m === "reduced" || m === "system") return m;
    } catch {
        /* ignore */
    }
    return "system";
}

/** Motion is reduced when the user asked for it OR the OS prefers it. */
export function motionReduced(): boolean {
    try {
        const m = localStorage.getItem(MOTION_KEY);
        if (m === "reduced") return true;
        if (m === "full") return false;
        return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    } catch {
        return false;
    }
}

export function readAvatar(): string {
    try {
        return localStorage.getItem(AVATAR_KEY) ?? "⚡";
    } catch {
        return "⚡";
    }
}

interface UIState {
    theme: "light" | "dark";
    sound: "on" | "off";
    motion: "full" | "reduced" | "system";
    avatar: string;
    pin: string | null;
    setTheme: (t: "light" | "dark") => void;
    toggleTheme: () => void;
    setSound: (s: "on" | "off") => void;
    toggleSound: () => void;
    setMotion: (m: "full" | "reduced" | "system") => void;
    setAvatar: (a: string) => void;
    setPin: (p: string | null) => void;
}

export const useUIStore = create<UIState>((set, get) => ({
    theme: readTheme(),
    sound: readSound(),
    motion: readMotion(),
    avatar: readAvatar(),
    pin: null,
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
    setSound: (sound) => {
        try {
            localStorage.setItem(SOUND_KEY, sound);
        } catch {
            /* ignore */
        }
        set({ sound });
    },
    toggleSound: () => get().setSound(get().sound === "on" ? "off" : "on"),
    setMotion: (motion) => {
        try {
            localStorage.setItem(MOTION_KEY, motion);
        } catch {
            /* ignore */
        }
        set({ motion });
    },
    setAvatar: (avatar) => {
        try {
            localStorage.setItem(AVATAR_KEY, avatar);
        } catch {
            /* ignore */
        }
        set({ avatar });
    },
    setPin: (pin) => set({ pin }),
}));
