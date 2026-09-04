import { Sun, Moon } from "@phosphor-icons/react";
import { useUIStore } from "../stores/useUIStore";

/** Theme switch (dark is the default). Persists to localStorage via useUIStore. */
export function ThemeToggle({ className = "" }: { className?: string }) {
    const theme = useUIStore((s) => s.theme);
    const toggleTheme = useUIStore((s) => s.toggleTheme);
    const isDark = theme === "dark";

    return (
        <button
            type="button"
            aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
            onClick={toggleTheme}
            className={`btn btn-ghost btn-icon text-lg ${className}`.trim()}
        >
            {isDark ? <Sun size={18} weight="bold" /> : <Moon size={18} weight="bold" />}
        </button>
    );
}
