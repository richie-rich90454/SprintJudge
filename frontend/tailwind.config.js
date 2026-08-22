/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  darkMode: "media",
  theme: {
    // Softly rounded industrial: crisp #3255A4 lines, friendly geometry.
    borderRadius: {
      none: "0", DEFAULT: "10px", sm: "8px", md: "12px", lg: "14px",
      xl: "18px", "2xl": "22px", "3xl": "26px", full: "9999px",
    },
    boxShadow: {
      none: "none",
      // Single flat ambient edge for floating layers only (dropdowns/modals).
      layer: "0 1px 0 rgba(26,31,46,0.04)",
    },
    extend: {
      colors: {
        primary: "#3255A4",
        "primary-dark": "#27428A",
        "primary-tint": "#E8EEFB",
        danger: "#d32f2f",
        success: "#2e7d32",
        warning: "#f57c00",
        muted: "#5f6368",
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
      },
      maxWidth: { content: "1200px" },
      minHeight: { tap: "44px" },
    },
  },
  plugins: [],
};
