import {
    createContext,
    createElement,
    useContext,
    useEffect,
    useMemo,
    useState,
    type ReactNode,
} from "react";
import { en } from "./en";
import { ar } from "./ar";

export type Locale = "en" | "ar";
export type Direction = "ltr" | "rtl";

export const LOCALES: ReadonlyArray<{ code: Locale; label: string; dir: Direction }> = [
    { code: "en", label: "English", dir: "ltr" },
    { code: "ar", label: "العربية", dir: "rtl" },
];

const STORAGE_KEY = "oq-locale";
const dictionaries: Record<Locale, Record<string, string>> = { en, ar };

export function detectLocale(): Locale {
    try {
        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored === "en" || stored === "ar") return stored;
        if (navigator.language.toLowerCase().startsWith("ar")) return "ar";
    } catch {
        /* storage or navigator unavailable */
    }
    return "en";
}

/** Mirrors applyStoredTheme(): sets lang/dir before React paints. */
export function applyStoredLocale(): void {
    const locale = detectLocale();
    const root = document.documentElement;
    root.lang = locale;
    root.dir = locale === "ar" ? "rtl" : "ltr";
}

export interface I18n {
    locale: Locale;
    dir: Direction;
    setLocale: (locale: Locale) => void;
    t: (key: string, vars?: Record<string, string | number>) => string;
}

const I18nContext = createContext<I18n | null>(null);

export function I18nProvider({ children }: { children: ReactNode }) {
    const [locale, setLocaleState] = useState<Locale>(detectLocale);

    useEffect(() => {
        applyStoredLocale();
    }, [locale]);

    const value = useMemo<I18n>(() => {
        const dict = dictionaries[locale];
        return {
            locale,
            dir: locale === "ar" ? "rtl" : "ltr",
            setLocale: (next) => {
                setLocaleState(next);
                try {
                    localStorage.setItem(STORAGE_KEY, next);
                } catch {
                    /* ignore */
                }
            },
            t: (key, vars) => {
                let text = dict[key] ?? en[key] ?? key;
                if (vars) {
                    for (const [name, val] of Object.entries(vars)) {
                        text = text.replaceAll(`{${name}}`, String(val));
                    }
                }
                return text;
            },
        };
    }, [locale]);

    return createElement(I18nContext.Provider, { value }, children);
}

export function useI18n(): I18n {
    const ctx = useContext(I18nContext);
    if (!ctx) throw new Error("useI18n must be used inside I18nProvider");
    return ctx;
}
