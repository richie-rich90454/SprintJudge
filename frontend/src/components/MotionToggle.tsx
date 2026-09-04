import { PersonSimpleRun, Pause } from "@phosphor-icons/react";
import { useUIStore } from "../stores/useUIStore";

const ORDER = ["system", "full", "reduced"] as const;

/** Header motion switch. Cycles system → full → reduced, persisted via useUIStore. */
export function MotionToggle({ className = "" }: { className?: string }) {
    const motion = useUIStore((s) => s.motion);
    const setMotion = useUIStore((s) => s.setMotion);
    const next = ORDER[(ORDER.indexOf(motion) + 1) % ORDER.length];

    return (
        <button
            type="button"
            aria-label={`Motion: ${motion}. Activate for ${next}.`}
            title={`Motion: ${motion}`}
            aria-pressed={motion === "reduced"}
            onClick={() => setMotion(next)}
            className={`btn btn-ghost btn-icon text-lg ${className}`.trim()}
        >
            {motion === "reduced" ? (
                <Pause size={18} weight="bold" />
            ) : (
                <PersonSimpleRun size={18} weight="bold" />
            )}
        </button>
    );
}
