function letter(i: number): string {
    return String.fromCharCode(65 + i);
}

/** Human-readable answer key: letters + option text, never raw JSON. */
export function formatAnswer(
    type: string,
    answer: unknown,
    options: string[] | null,
): string {
    if (answer == null || typeof answer !== "object") return String(answer ?? "—");
    const a = answer as Record<string, unknown>;
    const opt = (i: unknown): string => {
        const n = Number(i);
        if (!Number.isInteger(n) || n < 0) return String(i ?? "?");
        const text = options?.[n];
        return text != null ? `${letter(n)} — ${text}` : letter(n);
    };
    switch (type) {
        case "MCQ":
        case "OUTPUT_PRED":
        case "COMPLEXITY":
            return `Correct: ${opt(a["correctIndex"])}`;
        case "TRUE_FALSE":
            return `Correct: ${a["correct"] === true ? "True" : a["correct"] === false ? "False" : "?"}`;
        case "MULTIPLE_SELECT": {
            const idx = Array.isArray(a["correctIndices"]) ? (a["correctIndices"] as unknown[]) : [];
            return `Correct: ${idx.map(opt).join(", ") || "?"}`;
        }
        case "NUMERIC": {
            const v = a["answer"];
            const t = a["tolerance"];
            return `Correct: ${String(v ?? "?")}${t != null && t !== "" ? ` (± ${String(t)})` : ""}`;
        }
        case "FILL_BLANK":
            return `Correct: ${String(a["answer"] ?? "?")}`;
        case "DRAG_SORT": {
            const order = Array.isArray(a["correctOrder"]) ? (a["correctOrder"] as unknown[]) : [];
            const parts = order.map((id, k) => {
                const text = options?.[Number(id)];
                return `${k + 1}. ${text ?? String(id)}`;
            });
            return `Correct order: ${parts.join(" · ") || "?"}`;
        }
        case "CLICK_BUG":
            return `Buggy line: ${String(a["bugLine"] ?? "?")}`;
        case "CODE_COMPLETION":
            return `Expected:\n${String(a["expected"] ?? "?")}`;
        default:
            return `Correct: ${JSON.stringify(answer)}`;
    }
}
