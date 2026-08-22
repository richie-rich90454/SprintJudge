/** SprintJudge mark — inlined so it never depends on a network fetch. */
export function LogoMark({ size = 34 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" role="img" aria-label="SprintJudge">
      <rect width="64" height="64" rx="14" fill="#C8102E" />
      <path
        d="M40.5 22.6c-1.9-2.3-4.7-3.4-7.7-3.4-5.6 0-9.4 3.4-9.4 8.1 0 4.2 2.9 6.3 7.9 7.6 3.9 1 5.4 1.9 5.4 3.9 0 2.1-1.9 3.4-4.6 3.4-2.9 0-5.2-1.3-6.8-3.7l-4 3.9c2.3 3.2 6 5.2 10.8 5.2 6 0 10-3.5 10-8.5 0-4.5-3-6.8-8.3-8.2-3.7-.9-5-1.7-5-3.3 0-1.8 1.7-3 4.2-3 2.3 0 4.2.9 5.7 2.8z"
        fill="#fff"
      />
      <rect x="14" y="47" width="36" height="3" rx="1.5" fill="#F9E3E6" />
    </svg>
  );
}
