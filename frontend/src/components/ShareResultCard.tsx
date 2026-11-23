import { useState } from "react";
import { Button } from "../ui/Button";

interface PodiumRow {
    name: string;
    score: number;
    rank: number;
}

export interface ShareResultCardProps {
    title?: string;
    top: PodiumRow[];
    players?: number;
    questions?: number;
    accuracy?: number;
}

const escapeXml = (s: string) =>
    s.replace(/[<>&"']/g, (c) =>
        ({ "<": "&lt;", ">": "&gt;", "&": "&amp;", '"': "&quot;", "'": "&apos;" })[c] ?? c,
    );

/** Compact, dependency-free result card: copy a summary or save an SVG. */
export function ShareResultCard({ title = "Results", top, players, questions, accuracy }: ShareResultCardProps) {
    const [copied, setCopied] = useState(false);

    const summary = [
        title,
        ...top.slice(0, 3).map((p) => `${p.rank}. ${p.name} — ${p.score} pts`),
        [
            players != null ? `${players} players` : null,
            questions != null ? `${questions} questions` : null,
            accuracy != null ? `${accuracy}% correct` : null,
        ]
            .filter(Boolean)
            .join(" · "),
    ]
        .filter(Boolean)
        .join("\n");

    const copy = async () => {
        try {
            await navigator.clipboard.writeText(summary);
            setCopied(true);
            window.setTimeout(() => setCopied(false), 2000);
        } catch {
            setCopied(false);
        }
    };

    const download = () => {
        const rows = top
            .slice(0, 3)
            .map(
                (p, i) =>
                    `<text x="48" y="${150 + i * 56}" fill="#f2ede3" font-family="ui-monospace,monospace" font-size="26">${escapeXml(String(p.rank))}. ${escapeXml(p.name)}</text>` +
                    `<text x="552" y="${150 + i * 56}" fill="#c9a86a" font-family="ui-monospace,monospace" font-size="26" text-anchor="end">${p.score} pts</text>`,
            )
            .join("");
        const meta = [
            players != null ? `${players} players` : null,
            questions != null ? `${questions} questions` : null,
            accuracy != null ? `${accuracy}% correct` : null,
        ]
            .filter(Boolean)
            .join("  ·  ");
        const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="600" height="360" viewBox="0 0 600 360"><rect width="600" height="360" fill="#151619"/><rect x="0" y="0" width="600" height="10" fill="#c9a86a"/><text x="48" y="76" fill="#f2ede3" font-family="sans-serif" font-size="42" font-weight="800">${escapeXml(title)}</text><text x="48" y="110" fill="#b9b0a0" font-family="sans-serif" font-size="20">Final standings</text>${rows}<text x="48" y="328" fill="#b9b0a0" font-family="sans-serif" font-size="17">${escapeXml(meta)}</text></svg>`;
        const url = URL.createObjectURL(new Blob([svg], { type: "image/svg+xml" }));
        const a = document.createElement("a");
        a.href = url;
        a.download = "results-card.svg";
        a.click();
        URL.revokeObjectURL(url);
    };

    if (top.length === 0) return null;

    return (
        <div className="flex flex-wrap items-center justify-between gap-4 border border-[var(--oq-border)] bg-[var(--oq-row-alt)] p-4">
            <div className="min-w-0">
                <p className="label-caps mb-2">Shareable card</p>
                <div className="flex flex-wrap gap-3">
                    {top.slice(0, 3).map((p) => (
                        <span key={p.rank} className="text-sm font-bold">
                            <span className="mono text-[var(--oq-accent)]">{p.rank}.</span> {p.name}{" "}
                            <span className="mono text-[var(--oq-ink-soft)]">{p.score}</span>
                        </span>
                    ))}
                </div>
            </div>
            <div className="flex gap-2">
                <Button variant="secondary" size="sm" onClick={() => void copy()}>
                    {copied ? "Copied" : "Copy summary"}
                </Button>
                <Button variant="secondary" size="sm" onClick={download}>
                    Download card
                </Button>
            </div>
        </div>
    );
}
