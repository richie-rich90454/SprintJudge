/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  darkMode: "media",
  theme: {
    // Force the square, industrial look everywhere.
    borderRadius: {
      none: "0", DEFAULT: "0", sm: "0", md: "0", lg: "0", xl: "0",
      "2xl": "0", "3xl": "0", full: "0",
    },
    boxShadow: { none: "none" },
    extend: {
      colors: {
        primary: "#3255A4",
        "primary-dark": "#2742" + "8a",
        "primary-ink": "#ffffff",
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
