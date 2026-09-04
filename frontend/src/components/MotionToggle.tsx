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
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="6" y="4" width="4" height="16" rx="1" />
                    <rect x="14" y="4" width="4" height="16" rx="1" />
                </svg>
            ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
                </svg>
            )}
        </button>
    );
}
