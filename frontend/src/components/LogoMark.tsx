/**
 * SprintJudge mark — an abstract podium: three ascending bars in the
 * myColor ladder (growth/rank), grounded on a dark baseline.
 * Sharp squares only, inlined so it never needs a network fetch.
 */
export function LogoMark({ size = 34 }: { size?: number }) {
    return (
        <svg width={size} height={size} viewBox="0 0 64 64" role="img" aria-label="SprintJudge">
            <rect x="8" y="36" width="12" height="18" fill="#f69e6e" />
            <rect x="26" y="24" width="12" height="30" fill="#f06418" />
            <rect x="44" y="6" width="12" height="48" fill="#bf4906" />
            <rect x="4" y="56" width="56" height="4" fill="#a73c00" />
        </svg>
    );
}
