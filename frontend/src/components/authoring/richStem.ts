/** Media and display fields attached to a stem through config.rich. */
export interface RichStem {
    imageUrl?: string;
    imageAlt?: string;
    imageCaption?: string;
    audioUrl?: string;
    audioTranscript?: string;
    mediaLicense?: string;
    latex?: string;
    code?: string;
    codeLanguage?: string;
}

export function readRichStem(config: unknown): RichStem {
    if (!config || typeof config !== "object") return {};
    const rich = (config as Record<string, unknown>)["rich"];
    return rich && typeof rich === "object" ? (rich as RichStem) : {};
}

/** Only same-origin paths, http(s), and inline raster data are previewable. */
export function isSafeMediaUrl(url: string, kind: "image" | "audio"): boolean {
    const value = url.trim();
    if (/^https?:\/\/\S+$/i.test(value)) return true;
    if (value.startsWith("/") && !value.startsWith("//")) return true;
    const prefix =
        kind === "image" ? "data:image/(png|jpeg|gif|webp)" : "data:audio/(mpeg|mp3|wav|ogg|webm)";
    return new RegExp("^" + prefix + ";base64,[A-Za-z0-9+/=]+$", "i").test(value);
}

const LATEX_SYMBOLS: Record<string, string> = {
    "\\times": "×",
    "\\div": "÷",
    "\\le": "≤",
    "\\ge": "≥",
    "\\ne": "≠",
    "\\approx": "≈",
    "\\infty": "∞",
    "\\sum": "Σ",
    "\\int": "∫",
    "\\alpha": "α",
    "\\beta": "β",
    "\\theta": "θ",
    "\\pi": "π",
    "\\Delta": "Δ",
    "\\sqrt": "√",
    "\\%": "%",
};

/** Approximate, text-only math preview until a math renderer is mounted. */
export function latexToText(source: string): string {
    return source
        .replace(/^\$+|\$+$/g, "")
        .replace(/\\(?:left|right)\b/g, "")
        .replace(/\^\{([^}]*)\}/g, "↑$1")
        .replace(/_\{([^}]*)\}/g, "↓$1")
        .replace(/\\[a-zA-Z%]+/g, (token) => LATEX_SYMBOLS[token] ?? token)
        .replace(/\\([{}])/g, "$1")
        .replace(/\s+/g, " ")
        .trim();
}
