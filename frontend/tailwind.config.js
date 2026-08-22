/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  darkMode: "media",
  theme: {
    borderRadius: {
      none: "0", DEFAULT: "10px", sm: "8px", md: "12px", lg: "16px",
      xl: "18px", "2xl": "22px", "3xl": "26px", full: "9999px",
    },
    boxShadow: {
      none: "none",
      layer: "0 1px 2px rgba(20,22,26,0.06)",
    },
    extend: {
      colors: {
        // Examination red — the single brand accent.
        primary: "#C8102E",
        "primary-dark": "#9E0C23",
        "primary-tint": "#F9E3E6",
        "primary-ring": "rgba(200,16,46,0.22)",
        danger: "#B3261E",
        success: "#15803D",
        warning: "#B45309",
        muted: "#6B7080",
        border: "var(--oq-border)",
        "surface-alt": "var(--oq-row-alt)",
        bg: "var(--oq-bg)",
        surface: "var(--oq-surface)",
        ink: "var(--oq-ink)",
        line: "var(--oq-border)",
        "row-alt": "var(--oq-row-alt)",
      },
      fontFamily: {
        sans: ['"Noto Sans"', "system-ui", "sans-serif"],
        mono: ['"Noto Sans Mono"', "monospace"],
        display: ['"Noto Sans"', "system-ui", "sans-serif"],
      },
      letterSpacing: { caps: "0.14em" },
      maxWidth: { content: "1200px" },
      minHeight: { tap: "44px" },
    },
  },
  plugins: [],
};
