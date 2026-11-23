import { useState } from "react";
import { adminApi } from "../services/AdminApiService";

const KEY = "oq-fairness";

export interface FairnessSettings {
    shuffleOptions: boolean;
    shuffleQuestions: boolean;
}

const DEFAULT: FairnessSettings = { shuffleOptions: true, shuffleQuestions: true };

export function readFairness(): FairnessSettings {
    try {
        const raw = localStorage.getItem(KEY);
        if (raw) {
            const parsed = JSON.parse(raw) as Partial<FairnessSettings>;
            return {
                shuffleOptions: parsed.shuffleOptions !== false,
                shuffleQuestions: parsed.shuffleQuestions !== false,
            };
        }
    } catch {
        /* ignore */
    }
    return DEFAULT;
}

/**
 * Host-side fairness switches. Preferences are mirrored to the persisted admin
 * settings the room builder reads, and kept locally when the host is logged out.
 */
export function FairnessControls() {
    const [settings, setSettings] = useState<FairnessSettings>(readFairness);

    const update = (key: keyof FairnessSettings) => {
        const next = { ...settings, [key]: !settings[key] };
        setSettings(next);
        try {
            localStorage.setItem(KEY, JSON.stringify(next));
        } catch {
            /* ignore */
        }
        // shortcut: server keys are advisory until the room builder consumes them.
        void adminApi
            .updateSettings({
                shuffleOptions: String(next.shuffleOptions),
                shuffleQuestions: String(next.shuffleQuestions),
            })
            .catch(() => {
                /* local toggle still applies to this browser */
            });
    };

    const rows: { key: keyof FairnessSettings; label: string; hint: string }[] = [
        {
            key: "shuffleOptions",
            label: "Shuffle answer options",
            hint: "Vary option order between players.",
        },
        {
            key: "shuffleQuestions",
            label: "Shuffle question order",
            hint: "Vary the order of questions each game.",
        },
    ];

    return (
        <div>
            <p className="label-caps mb-3">Fairness</p>
            <div className="flex flex-col gap-2">
                {rows.map((row) => (
                    <button
                        key={row.key}
                        type="button"
                        role="switch"
                        aria-checked={settings[row.key]}
                        onClick={() => update(row.key)}
                        className="flex min-h-[44px] w-full items-center justify-between gap-3 border border-[var(--oq-border)] px-3 text-left hover:bg-[var(--oq-row-alt)]"
                    >
                        <span className="min-w-0">
                            <span className="block text-sm font-bold">{row.label}</span>
                            <span className="block text-xs text-[var(--oq-ink-soft)]">
                                {row.hint}
                            </span>
                        </span>
                        <span
                            aria-hidden="true"
                            className={`mono shrink-0 rounded-[2px] px-2 py-1 text-xs font-bold ${
                                settings[row.key]
                                    ? "bg-[var(--oq-accent)] text-[var(--oq-surface)]"
                                    : "bg-[var(--oq-row-alt)] text-[var(--oq-ink-soft)]"
                            }`}
                        >
                            {settings[row.key] ? "ON" : "OFF"}
                        </span>
                    </button>
                ))}
            </div>
            <p className="text-xs text-[var(--oq-ink-soft)] mt-2">
                Applies to games started after the switch changes.
            </p>
        </div>
    );
}
