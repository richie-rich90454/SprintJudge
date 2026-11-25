import { useI18n } from "../i18n";
import { useEffect } from "react";
import { SpeakerHigh, SpeakerX } from "@phosphor-icons/react";
import { useUIStore } from "../stores/useUIStore";
import { audio } from "../services/AudioEngine";

/** Header sound switch. Persists via useUIStore and drives the Tone engine. */
export function SoundToggle({ className = "" }: { className?: string }) {
    const { t } = useI18n();
    const sound = useUIStore((s) => s.sound);
    const toggleSound = useUIStore((s) => s.toggleSound);
    const on = sound === "on";

    useEffect(() => {
        try {
            audio.setMuted(!on);
        } catch {
            /* engine boots on first user gesture; preference applies then */
        }
    }, [on]);

    return (
        <button
            type="button"
            aria-label={on ? t("ui.muteSounds") : t("ui.unmuteSounds")}
            title={on ? t("ui.soundOn") : t("ui.soundOff")}
            aria-pressed={on}
            onClick={toggleSound}
            className={`btn btn-ghost btn-icon text-lg ${className}`.trim()}
        >
            {on ? <SpeakerHigh size={18} weight="bold" /> : <SpeakerX size={18} weight="bold" />}
        </button>
    );
}
