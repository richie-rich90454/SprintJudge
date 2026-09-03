import { Button } from "@heroui/react";
import { useUIStore } from "../stores/useUIStore";

/** Theme switch (dark is the default). Persists to localStorage via useUIStore. */
export function ThemeToggle({ className = "" }: { className?: string }) {
    const theme = useUIStore((s) => s.theme);
    const toggleTheme = useUIStore((s) => s.toggleTheme);
    const isDark = theme === "dark";

    return (
        <Button
            isIconOnly
            variant="ghost"
            aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
            onPress={toggleTheme}
            className={`text-lg min-h-[44px] min-w-[44px] ${className}`}
        >
            {isDark ? (
                <svg
                    width="18"
                    height="18"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                >
                    <circle cx="12" cy="12" r="4" />
                    <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
                </svg>
            ) : (
                <svg
                    width="18"
                    height="18"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                >
                    <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
                </svg>
            )}
        </Button>
    );
}
