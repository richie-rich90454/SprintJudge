import { useEffect, useState, type CSSProperties } from "react";

const KEY = "oq-stage-theme";

export type StageThemeId = "classic" | "light" | "neon" | "arena";

export interface StageTheme {
    id: StageThemeId;
    label: string;
    tokens: Record<string, string>;
}

/** Each theme only overrides stage tokens, so the rest of the app is untouched. */
export const STAGE_THEMES: StageTheme[] = [
    { id: "classic", label: "Classic", tokens: {} },
    {
        id: "light",
        label: "Light",
        tokens: {
            "--stage-surface": "#f4efe4",
            "--stage-ink": "#1a1714",
            "--stage-ink-soft": "#5a5348",
            "--stage-border": "#d6cdbb",
            "--stage-border-strong": "#9c8f79",
            "--stage-accent": "#b45309",
        },
    },
    {
        id: "neon",
        label: "Neon",
        tokens: {
            "--stage-surface": "#0b1020",
            "--stage-ink": "#e9f1ff",
            "--stage-ink-soft": "#9fb3d9",
            "--stage-border": "#243254",
            "--stage-border-strong": "#3d5180",
            "--stage-accent": "#38e8c6",
        },
    },
    {
        id: "arena",
        label: "Arena",
        tokens: {
            "--stage-surface": "#1b0f0d",
            "--stage-ink": "#fff3e6",
            "--stage-ink-soft": "#d6a98f",
            "--stage-border": "#5a2a1f",
            "--stage-border-strong": "#8a4632",
            "--stage-accent": "#ff6b35",
        },
    },
];

function readTheme(): StageThemeId {
    try {
        const id = localStorage.getItem(KEY);
        if (STAGE_THEMES.some((t) => t.id === id)) return id as StageThemeId;
    } catch {
        /* ignore */
    }
    return "classic";
}

/** Shared across tabs via storage so the projector follows the host console. */
export function useStageTheme(): [StageThemeId, (id: StageThemeId) => void] {
    const [theme, setTheme] = useState<StageThemeId>(readTheme);
    useEffect(() => {
        const sync = (e: StorageEvent) => {
            if (e.key === KEY) setTheme(readTheme());
        };
        window.addEventListener("storage", sync);
        return () => window.removeEventListener("storage", sync);
    }, []);
    const choose = (id: StageThemeId) => {
        setTheme(id);
        try {
            localStorage.setItem(KEY, id);
        } catch {
            /* ignore */
        }
    };
    return [theme, choose];
}

/** Inline token overrides for the stage root; no global stylesheet changes. */
export function stageThemeStyle(id: StageThemeId): CSSProperties {
    const theme = STAGE_THEMES.find((t) => t.id === id) ?? STAGE_THEMES[0];
    return { ...theme.tokens } as CSSProperties;
}

export function StageThemePicker({ className = "" }: { className?: string }) {
    const [theme, choose] = useStageTheme();
    return (
        <div className={`flex flex-wrap gap-2 ${className}`.trim()}>
            {STAGE_THEMES.map((t) => (
                <button
                    key={t.id}
                    type="button"
                    aria-pressed={theme === t.id}
                    title={`Stage theme: ${t.label}`}
                    onClick={() => choose(t.id)}
                    className={`flex items-center gap-2 rounded-[2px] border px-3 py-2 text-sm font-bold min-h-[44px] transition-colors ${
                        theme === t.id
                            ? "border-[var(--oq-accent)] bg-[var(--oq-row-alt)] text-[var(--oq-accent)]"
                            : "border-[var(--oq-border)] hover:bg-[var(--oq-row-alt)]"
                    }`}
                >
                    <span
                        aria-hidden="true"
                        className="inline-block h-4 w-4 rounded-full border border-[var(--oq-border)]"
                        style={{ background: t.tokens["--stage-accent"] ?? "#151619" }}
                    />
                    {t.label}
                </button>
            ))}
        </div>
    );
}
