import { readRichStem } from "./richStem";

// Advisory thresholds mirror scripts/check-bank.mjs so the wizard warns about
// the same editorial issues the bank gate reports. Legal scans (protected
// program names) stay in the bank gate and are intentionally not duplicated.
const SCRATCH = /\bWait compute\b|\bWait recompute\b|\bLet me keep\b|intended answer is|final output uses corrected|I.ll edit mentally|need only one correct|Error. Leave|\bUse\s+\d+(?:\.\d+)?\.$/i;
const PLACEHOLDER = /lorem ipsum|\bfixme\b|\bquestion \d+ about\b|\(review needed\)|\bI set [A-D]\.|Error\. Leave|need only one correct|I.ll edit mentally|final output uses corrected|The correct answer is [A-D]/i;
const TERMINAL = /[.!?]['")\]]?$/;
const DANGLING = /\b(?:an|the|of|to|in|on|for|with|at|by|from|and|or|into)\s*[.!?]*$/i;
const CODE_TYPES = new Set(["OUTPUT_PRED", "CLICK_BUG", "CODE_COMPLETION", "OJ_FULL", "OJ_PATCH"]);
const OPTION_TYPES = new Set(["MCQ", "OUTPUT_PRED", "COMPLEXITY", "MULTIPLE_SELECT"]);

export interface QualityFinding {
    level: "advisory" | "error";
    message: string;
}

export interface QualityInput {
    type: string;
    title: string;
    description: string;
    config: Record<string, unknown>;
}

const str = (v: unknown) => (typeof v === "string" ? v.trim() : "");

export function checkItemQuality(input: QualityInput): QualityFinding[] {
    const findings: QualityFinding[] = [];
    const advisory = (message: string) => findings.push({ level: "advisory", message });
    const error = (message: string) => findings.push({ level: "error", message });
    const stem = str(input.description) || str(input.title);

    if (!stem) error("Stem is empty: add the question text.");
    const explanation = str(input.config["explanation"]);
    if (explanation.length < 60) advisory("Explanation under 60 chars: teach the why, not just the answer.");
    if (!str(input.config["hint"])) advisory("Empty hint: consider a short nudge.");

    const options = Array.isArray(input.config["options"])
        ? (input.config["options"] as unknown[]).filter((o): o is string => typeof o === "string")
        : [];
    if (OPTION_TYPES.has(input.type) && options.length < 2) error("Need 2+ options for this type.");

    const correctIndex = input.config["correctIndex"];
    const correctIndices = input.config["correctIndices"];
    const hasCorrect = (typeof correctIndex === "number" && Number.isFinite(correctIndex)) ||
        (Array.isArray(correctIndices) && correctIndices.length > 0);
    if (options.length > 0 && !hasCorrect) error("No answer: set a correct option index.");

    const seen = new Set<string>();
    for (let i = 0; i < options.length; i++) {
        const key = options[i].trim().toLowerCase();
        if (key && seen.has(key)) error(`Duplicate option text: ${options[i].trim()}.`);
        seen.add(key);
    }
    const correctSet = new Set<number>(
        typeof correctIndex === "number" ? [correctIndex] :
        Array.isArray(correctIndices) ? correctIndices.filter((n): n is number => typeof n === "number") : [],
    );
    const misconceptions = Array.isArray(input.config["misconceptions"])
        ? (input.config["misconceptions"] as unknown[]).map(str)
        : [];
    options.forEach((_, i) => {
        if (correctSet.has(i)) return;
        const why = misconceptions[i] ?? "";
        if (!why) advisory(`Distractor ${i + 1} has no misconception named.`);
        else if (why.length < 12) advisory(`Distractor ${i + 1} misconception under 12 chars: name the specific wrong belief.`);
    });
    if (options.length >= 3) {
        const lengths = options.map((o) => o.trim().length).sort((a, b) => a - b);
        const median = lengths[Math.floor(lengths.length / 2)];
        if (median > 0) {
            options.forEach((text, i) => {
                const ratio = text.trim().length / median;
                if (ratio < 0.3 || ratio > 3.5) {
                    advisory(`Option ${i + 1} is ${ratio.toFixed(1)}x the median length: consider parallel options.`);
                }
            });
        }
    }

    if (CODE_TYPES.has(input.type) && stem &&
        !/[;{}()]|=>|\bint\b|\bvoid\b|\bString\b|\bfor\b|\bif\b|\bnew\b|\breturn\b/.test(stem)) {
        advisory("Code-type stem carries no code in the stem text.");
    }

    const text = `${stem} ${explanation} ${str(input.config["hint"])} ${options.join(" ")}`;
    if (SCRATCH.test(text) || PLACEHOLDER.test(text)) error("Author scratch or placeholder text found in content.");
    if (explanation && !TERMINAL.test(explanation)) {
        advisory(DANGLING.test(explanation) ? "Explanation ends mid-phrase." : "Explanation has no terminal punctuation.");
    }

    const rich = readRichStem(input.config);
    if ((rich.imageUrl || rich.audioUrl) && !str(rich.mediaLicense)) {
        advisory("Stem media has no mediaLicense: record the source and license before shipping.");
    }
    return findings;
}
