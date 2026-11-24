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
            "--stage-surface": "rgb(244,239,228)",
            "--stage-ink": "rgb(26,23,20)",
            "--stage-ink-soft": "rgb(90,83,72)",
            "--stage-border": "rgb(214,205,187)",
            "--stage-border-strong": "rgb(156,143,121)",
            "--stage-accent": "rgb(180,83,9)",
        },
    },
    {
        id: "neon",
        label: "Neon",
        tokens: {
            "--stage-surface": "rgb(11,16,32)",
            "--stage-ink": "rgb(233,241,255)",
            "--stage-ink-soft": "rgb(159,179,217)",
            "--stage-border": "rgb(36,50,84)",
            "--stage-border-strong": "rgb(61,81,128)",
            "--stage-accent": "rgb(56,232,198)",
        },
    },
    {
        id: "arena",
        label: "Arena",
        tokens: {
            "--stage-surface": "rgb(27,15,13)",
            "--stage-ink": "rgb(255,243,230)",
            "--stage-ink-soft": "rgb(214,169,143)",
            "--stage-border": "rgb(90,42,31)",
            "--stage-border-strong": "rgb(138,70,50)",
            "--stage-accent": "rgb(255,107,53)",
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
                        style={{ background: t.tokens["--stage-accent"] ?? "rgb(21,22,25)" }}
                    />
                    {t.label}
                </button>
            ))}
        </div>
    );
}
