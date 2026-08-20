/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  darkMode: "media",
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Noto Sans"', "system-ui", "sans-serif"],
        mono: ['"Noto Sans Mono"', "monospace"],
      },
      colors: {
        border: "#dadce0",
        surface: "#ffffff",
        "surface-alt": "#f8f9fa",
        primary: "#1a73e8",
        "primary-dark": "#1557b0",
        success: "#137333",
        danger: "#d93025",
        muted: "#5f6368",
      },
      boxShadow: {
        card: "0 1px 3px rgba(0,0,0,0.12)",
      },
      minHeight: {
        tap: "44px",
      },
    },
  },
  plugins: [],
};
