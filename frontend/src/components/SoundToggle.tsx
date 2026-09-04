import { useEffect } from "react";
import { useUIStore } from "../stores/useUIStore";
import { audio } from "../services/AudioEngine";

/** Header sound switch. Persists via useUIStore and drives the Tone engine. */
export function SoundToggle({ className = "" }: { className?: string }) {
    const sound = useUIStore((s) => s.sound);
    const toggleSound = useUIStore((s) => s.toggleSound);
    const on = sound === "on";

    useEffect(() => {
        try {
            audio.setMuted(!on);
        } catch {
            /* engine boots on first user gesture; preference applies then */
        }
    }, [on ]);

    return (
        <button
            type="button"
            aria-label={on ? "Mute sounds" : "Unmute sounds"}
            aria-pressed={on}
            onClick={toggleSound}
            className={`btn btn-ghost btn-icon text-lg ${className}`.trim()}
        >
            {on ? (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M11 5 6 9H2v6h4l5 4V5z" />
                    <path d="M15.5 8.5a5 5 0 0 1 0 7" />
                    <path d="M18.5 5.5a9 9 0 0 1 0 13" />
                </svg>
            ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M11 5 6 9H2v6h4l5 4V5z" />
                    <line x1="23" y1="9" x2="17" y2="15" />
                    <line x1="17" y1="9" x2="23" y2="15" />
                </svg>
            )}
        </button>
    );
}
