/**
 * SprintJudge mark: a stage arch inside a stage arch, with a brass cue dot.
 * Inlined so it never needs a network fetch and inherits the current ink.
 */
export function LogoMark({ size = 34 }: { size?: number }) {
    return (
        <svg width={size} height={size} viewBox="0 0 64 64" role="img" aria-label="SprintJudge">
            <path
                d="M8 56V30a24 24 0 0 1 48 0v26"
                fill="none"
                stroke="currentColor"
                strokeWidth="3"
            />
            <path
                d="M21 56V34a11 11 0 0 1 22 0v22"
                fill="none"
                stroke="var(--oq-accent)"
                strokeWidth="2"
            />
            <circle cx="32" cy="15" r="3.5" fill="var(--oq-accent)" />
        </svg>
    );
}
