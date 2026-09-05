import { useEffect } from "react";
import { SpeakerHigh, SpeakerX } from "@phosphor-icons/react";
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
            title={on ? "Sound: on" : "Sound: off"}
            aria-pressed={on}
            onClick={toggleSound}
            className={`btn btn-ghost btn-icon text-lg ${className}`.trim()}
        >
            {on ? (
                <SpeakerHigh size={18} weight="bold" />
            ) : (
                <SpeakerX size={18} weight="bold" />
            )}
        </button>
    );
}
