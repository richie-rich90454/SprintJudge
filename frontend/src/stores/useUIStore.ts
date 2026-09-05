import { create } from "zustand";

const THEME_KEY = "oq-theme";
const SOUND_KEY = "oq-sound";
const MOTION_KEY = "oq-motion";

const THEME_COLORS = { light: "#fff0e4" } as const;

/** Syncs the theme-color meta with the active theme (no token disagreement). */
function syncThemeColor(_t: "light" | "dark"): void {
    try {
        let meta = document.querySelector('meta[name="theme-color"]');
        if (!meta) {
            meta = document.createElement("meta");
            meta.setAttribute("name", "theme-color");
            document.head.appendChild(meta);
        }
        meta.setAttribute("content", THEME_COLORS.light);
    } catch {
        /* ignore */
    }
}

/** Reads the persisted theme; light-only lockdown — always light. */
function readTheme(): "light" | "dark" {
    return "light";
}

/** Applies the persisted theme to <html> (called once at boot). */
export function applyStoredTheme(): void {
    // ponytail: dark deleted on purpose — force light, drop .dark class if present.
    document.documentElement.classList.remove("dark");
    syncThemeColor("light");
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

interface UIState {
    theme: "light" | "dark";
    sound: "on" | "off";
    motion: "full" | "reduced" | "system";
    pin: string | null;
    setTheme: (t: "light" | "dark") => void;
    toggleTheme: () => void;
    setSound: (s: "on" | "off") => void;
    toggleSound: () => void;
    setMotion: (m: "full" | "reduced" | "system") => void;
    setPin: (p: string | null) => void;
}

export const useUIStore = create<UIState>((set, get) => ({
    theme: readTheme(),
    sound: readSound(),
    motion: readMotion(),
    pin: null,
    setTheme: (_theme) => {
        document.documentElement.classList.remove("dark");
        syncThemeColor("light");
        try {
            localStorage.setItem(THEME_KEY, "light");
        } catch {
            /* ignore */
        }
        set({ theme: "light" });
    },
    toggleTheme: () => get().setTheme("light"),
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
    setPin: (pin) => set({ pin }),
}));
