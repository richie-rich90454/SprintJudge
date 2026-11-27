import { useI18n } from "../i18n";
import { useEffect, useState } from "react";
import { MusicNote, Pause } from "@phosphor-icons/react";
import { audio } from "../services/AudioEngine";
import { useUIStore } from "../stores/useUIStore";

const KEY = "oq-music";

function readMusic(): boolean {
    try {
        return localStorage.getItem(KEY) === "on";
    } catch {
        return false;
    }
}

/** Separate background-music switch. SFX stay under the master SoundToggle. */
export function MusicToggle({ className = "" }: { className?: string }) {
    const { t } = useI18n();
    const [on, setOn] = useState(readMusic);
    const sound = useUIStore((s) => s.sound);

    useEffect(() => {
        if (!on || sound !== "on") return;
        try {
            void audio.resume();
            audio.startMusic();
        } catch {
            /* engine unavailable until the next user gesture */
        }
        return () => audio.stopMusic();
    }, [on, sound]);

    const toggle = () => {
        const next = !on;
        setOn(next);
        try {
            localStorage.setItem(KEY, next ? "on" : "off");
        } catch {
            /* storage unavailable: preference lasts for this tab */
        }
    };

    return (
        <button
            type="button"
            aria-label={on ? t("ui.turnBackgroundMusicOff") : t("ui.turnBackgroundMusicOn")}
            title={
                sound === "off"
                    ? t("ui.musicFollowsTheMasterSoundSwitch")
                    : t("ui.musicState", { state: on ? "on" : "off" })
            }
            aria-pressed={on}
            onClick={toggle}
            className={`btn btn-ghost btn-icon text-lg ${className}`.trim()}
        >
            {on ? <MusicNote size={18} weight="bold" /> : <Pause size={18} weight="bold" />}
        </button>
    );
}
