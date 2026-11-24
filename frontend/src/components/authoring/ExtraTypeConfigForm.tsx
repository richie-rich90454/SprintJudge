import { ExtraQuestionType } from "./authoringTypes";

const field = "input-underline w-full";
const num = (v: unknown, fallback = 0) => {
    const n = Number(v);
    return Number.isFinite(n) ? n : fallback;
};
const addButton = "text-[var(--oq-accent)] text-sm self-start min-h-[44px] px-2 inline-flex items-center font-bold";

interface Region {
    id: string;
    label: string;
    x: number;
    y: number;
    r: number;
}
interface Pair {
    left: number;
    right: number;
}
interface Part {
    id: string;
    prompt: string;
    kind: string;
    options: string[];
    answer: string;
    points: number;
}

interface Props {
    type: ExtraQuestionType;
    config: Record<string, unknown>;
    onChange: (patch: Record<string, unknown>) => void;
}

export function ExtraTypeConfigForm({ type, config, onChange }: Props) {
    const set = (patch: Record<string, unknown>) => onChange(patch);

    if (type === "HOT_SPOT") {
        const regions = (config["regions"] as Region[]) ?? [];
        const correct = (config["correctRegionIds"] as string[]) ?? [];
        const update = (i: number, patch: Partial<Region>) =>
            set({ regions: regions.map((r, j) => (i === j ? { ...r, ...patch } : r)) });
        return (
            <div className="flex flex-col gap-4">
                <label className="text-sm flex flex-col gap-1">
                    Image URL
                    <input value={(config["imageUrl"] as string) ?? ""} onChange={(e) => set({ imageUrl: e.target.value })} className={field} />
                </label>
                <div className="flex gap-4 flex-wrap">
                    <label className="text-sm flex-1">Width (units)<input type="number" value={num(config["width"], 100)} onChange={(e) => set({ width: num(e.target.value, 100) })} className={field} /></label>
                    <label className="text-sm flex-1">Height (units)<input type="number" value={num(config["height"], 100)} onChange={(e) => set({ height: num(e.target.value, 100) })} className={field} /></label>
                </div>
                <p className="text-sm text-[var(--oq-ink-soft)]">Regions (x/y/radius are percentages of the image box)</p>
                {regions.map((r, i) => (
                    <div key={i} className="flex gap-2 flex-wrap items-end border border-[var(--oq-border)] p-2">
                        <label className="text-xs">Label<input value={r.label} onChange={(e) => update(i, { label: e.target.value })} className={field} /></label>
                        <label className="text-xs">x<input type="number" value={r.x} onChange={(e) => update(i, { x: num(e.target.value) })} className="w-16" /></label>
                        <label className="text-xs">y<input type="number" value={r.y} onChange={(e) => update(i, { y: num(e.target.value) })} className="w-16" /></label>
                        <label className="text-xs">r<input type="number" value={r.r} onChange={(e) => update(i, { r: num(e.target.value) })} className="w-16" /></label>
                        <label className="text-xs flex items-center gap-1 min-h-[44px]">
                            <input type="checkbox" checked={correct.includes(r.id)} onChange={(e) =>
                                set({ correctRegionIds: e.target.checked ? [...correct, r.id] : correct.filter((id) => id !== r.id) })
                            } /> Correct
                        </label>
                    </div>
                ))}
                <button className={addButton} onClick={() => set({ regions: [...regions, { id: `r${regions.length + 1}`, label: "", x: 50, y: 50, r: 8 }] })}>
                    Add region
                </button>
            </div>
        );
    }

    if (type === "MATCHING") {
        const left = (config["left"] as string[]) ?? [];
        const right = (config["right"] as string[]) ?? [];
        const pairs = (config["pairs"] as Pair[]) ?? [];
        const lines = (v: string[]) => v.join("\n");
        const split = (v: string) => v.split("\n").filter((s) => s.length > 0);
        return (
            <div className="flex flex-col gap-4">
                <div className="flex gap-4">
                    <label className="text-sm flex-1">Left prompts (one per line)
                        <textarea value={lines(left)} onChange={(e) => set({ left: split(e.target.value) })} className={field} />
                    </label>
                    <label className="text-sm flex-1">Right prompts (one per line)
                        <textarea value={lines(right)} onChange={(e) => set({ right: split(e.target.value) })} className={field} />
                    </label>
                </div>
                <p className="text-sm text-[var(--oq-ink-soft)]">Correct pairs</p>
                {pairs.map((p, i) => (
                    <div key={i} className="flex gap-2 items-center">
                        <select value={p.left} onChange={(e) => set({ pairs: pairs.map((x, j) => (i === j ? { ...x, left: num(e.target.value) } : x)) })} className={field}>
                            {left.map((text, j) => <option key={j} value={j}>{text || `Left ${j + 1}`}</option>)}
                        </select>
                        <span className="text-[var(--oq-ink-soft)]">→</span>
                        <select value={p.right} onChange={(e) => set({ pairs: pairs.map((x, j) => (i === j ? { ...x, right: num(e.target.value) } : x)) })} className={field}>
                            {right.map((text, j) => <option key={j} value={j}>{text || `Right ${j + 1}`}</option>)}
                        </select>
                    </div>
                ))}
                <button className={addButton} onClick={() => set({ pairs: [...pairs, { left: 0, right: 0 }] })}>Add pair</button>
            </div>
        );
    }

    if (type === "TOLERANCE_NUMERIC") {
        return (
            <div className="flex gap-4 flex-wrap">
                <label className="text-sm">Answer<input type="number" value={num(config["answer"])} onChange={(e) => set({ answer: num(e.target.value) })} className="input-underline ml-1 w-28" /></label>
                <label className="text-sm">Tolerance<input type="number" step="any" min={0} value={num(config["tolerance"])} onChange={(e) => set({ tolerance: num(e.target.value) })} className="input-underline ml-1 w-28" /></label>
                <label className="text-sm">Mode
                    <select value={(config["toleranceMode"] as string) ?? "absolute"} onChange={(e) => set({ toleranceMode: e.target.value })} className="input-underline ml-1 w-32">
                        <option value="absolute">Absolute</option>
                        <option value="relative">Relative</option>
                    </select>
                </label>
                <label className="text-sm">Unit<input value={(config["unit"] as string) ?? ""} onChange={(e) => set({ unit: e.target.value })} className="input-underline ml-1 w-28" /></label>
            </div>
        );
    }

    const parts = (config["parts"] as Part[]) ?? [];
    const update = (i: number, patch: Partial<Part>) =>
        set({ parts: parts.map((p, j) => (i === j ? { ...p, ...patch } : p)) });
    return (
        <div className="flex flex-col gap-4">
            {parts.map((p, i) => (
                <div key={p.id} className="flex flex-col gap-2 border border-[var(--oq-border)] p-3">
                    <div className="flex gap-2 items-center">
                        <input value={p.prompt} placeholder={`Part ${i + 1} prompt`} onChange={(e) => update(i, { prompt: e.target.value })} className={field} />
                        <button className="text-[var(--oq-danger)] text-sm min-h-[44px] px-2" onClick={() => set({ parts: parts.filter((_, j) => j !== i) })}>Remove</button>
                    </div>
                    <div className="flex gap-4 flex-wrap">
                        <label className="text-sm">Kind
                            <select value={p.kind} onChange={(e) => update(i, { kind: e.target.value })} className="input-underline ml-1">
                                <option value="TEXT">Text</option><option value="NUMERIC">Numeric</option><option value="MCQ">Choice</option>
                            </select>
                        </label>
                        <label className="text-sm">Points<input type="number" min={0} value={p.points} onChange={(e) => update(i, { points: num(e.target.value) })} className="input-underline ml-1 w-20" /></label>
                        <label className="text-sm">Expected answer<input value={p.answer} onChange={(e) => update(i, { answer: e.target.value })} className="input-underline ml-1 w-40" /></label>
                    </div>
                    {p.kind === "MCQ" && (
                        <label className="text-sm">Options (comma separated)
                            <input value={(p.options ?? []).join(",")} onChange={(e) => update(i, { options: e.target.value.split(",").map((s) => s.trim()) })} className={field} />
                        </label>
                    )}
                </div>
            ))}
            <button className={addButton} onClick={() => set({ parts: [...parts, { id: `p${parts.length + 1}`, prompt: "", kind: "TEXT", options: [], answer: "", points: 1 }] })}>
                Add part
            </button>
        </div>
    );
}

interface PreviewProps {
    type: ExtraQuestionType;
    config: Record<string, unknown>;
}

export function ExtraTypePreview({ type, config }: PreviewProps) {
    if (type === "HOT_SPOT") {
        const regions = (config["regions"] as Region[]) ?? [];
        const correct = (config["correctRegionIds"] as string[]) ?? [];
        const imageUrl = (config["imageUrl"] as string) ?? "";
        return (
            <div className="relative w-full h-64 border border-[var(--oq-border)] overflow-hidden bg-[var(--oq-surface)]">
                {imageUrl && /^https?:\/\//i.test(imageUrl) && <img src={imageUrl} alt="Hot-spot target" className="absolute inset-0 w-full h-full object-contain" />}
                {regions.map((r, i) => (
                    <div key={i} className="absolute rounded-full border-2 flex items-center justify-center text-xs"
                        style={{ left: `${r.x - r.r}%`, top: `${r.y - r.r}%`, width: `${r.r * 2}%`, height: `${r.r * 2}%`, borderColor: correct.includes(r.id) ? "var(--oq-accent)" : "var(--oq-border)" }}>
                        {r.label}
                    </div>
                ))}
            </div>
        );
    }
    if (type === "MATCHING") {
        const left = (config["left"] as string[]) ?? [];
        const right = (config["right"] as string[]) ?? [];
        const pairs = (config["pairs"] as Pair[]) ?? [];
        return (
            <div className="grid grid-cols-2 gap-4 text-sm">
                <ul className="flex flex-col gap-2 m-0 list-none p-0">
                    {left.map((s, i) => <li key={i}>{s || `Left ${i + 1}`}</li>)}
                </ul>
                <ul className="flex flex-col gap-2 m-0 list-none p-0">
                    {right.map((s, i) => <li key={i}>{s || `Right ${i + 1}`}</li>)}
                </ul>
                <p className="col-span-2 text-[var(--oq-ink-soft)] text-xs">
                    {pairs.map((p) => `${left[p.left] ?? p.left} → ${right[p.right] ?? p.right}`).join("; ") || "No pairs set"}
                </p>
            </div>
        );
    }
    if (type === "TOLERANCE_NUMERIC") {
        const tolerance = num(config["tolerance"]);
        return (
            <p className="text-sm">
                Expected: {num(config["answer"])} ± {tolerance}
                {(config["toleranceMode"] as string) === "relative" ? "%" : ""}{" "}
                {String(config["unit"] ?? "")}
            </p>
        );
    }
    const parts = (config["parts"] as Part[]) ?? [];
    return (
        <ol className="flex flex-col gap-2 text-sm m-0 pl-5">
            {parts.map((p, i) => (
                <li key={p.id}>{p.prompt || `Part ${i + 1}`} - {p.kind}, {p.points} pt(s), answer: {p.answer || "(unset)"}</li>
            ))}
        </ol>
    );
}
