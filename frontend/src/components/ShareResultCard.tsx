import { useI18n } from "../i18n";
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
    s.replace(
        /[<>&"']/g,
        (c) =>
            (
                { "<": "&lt;", ">": "&gt;", "&": "&amp;", '"': "&quot;", "'": "&apos;" } as Record<
                    string,
                    string
                >
            )[c]!,
    );

/** Compact, dependency-free result card: copy a summary or save an SVG. */
export function ShareResultCard({
    title,
    top,
    players,
    questions,
    accuracy,
}: ShareResultCardProps) {
    const { t } = useI18n();
    const [copied, setCopied] = useState(false);
    const cardTitle = title ?? t("ui.results");

    const summary = [
        cardTitle,
        ...top
            .slice(0, 3)
            .map((p) => t("ui.rankSummary", { rank: p.rank, name: p.name, score: p.score })),
        [
            players != null ? t("ui.playersCount", { n: players }) : null,
            questions != null ? t("ui.questionsCount", { n: questions }) : null,
            accuracy != null ? t("ui.accuracyPct", { n: accuracy }) : null,
        ]
            .filter(Boolean)
            .join(" | "),
    ]
        .filter(Boolean)
        .join("\n");

    const copy = async () => {
        const done = (ok: boolean) => {
            setCopied(ok);
            if (ok) window.setTimeout(() => setCopied(false), 2000);
        };
        try {
            const clip = navigator.clipboard;
            if (clip?.writeText) {
                await clip.writeText(summary);
                done(true);
                return;
            }
            // The Clipboard API is undefined on insecure origins.
            const ta = document.createElement("textarea");
            ta.value = summary;
            document.body.appendChild(ta);
            ta.select();
            done(document.execCommand("copy"));
            ta.remove();
        } catch {
            done(false);
        }
    };

    const download = () => {
        const rows = top
            .slice(0, 3)
            .map(
                (p, i) =>
                    `<text x="48" y="${150 + i * 56}" fill="rgb(242,237,227)" font-family="ui-monospace,monospace" font-size="26">${escapeXml(String(p.rank))}. ${escapeXml(p.name)}</text>` +
                    `<text x="552" y="${150 + i * 56}" fill="rgb(201,168,106)" font-family="ui-monospace,monospace" font-size="26" text-anchor="end">${p.score} pts</text>`,
            )
            .join("");
        const meta = [
            players != null ? t("ui.playersCount", { n: players }) : null,
            questions != null ? t("ui.questionsCount", { n: questions }) : null,
            accuracy != null ? t("ui.accuracyPct", { n: accuracy }) : null,
        ]
            .filter(Boolean)
            .join("  |  ");
        const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="600" height="360" viewBox="0 0 600 360"><rect width="600" height="360" fill="#151619"/><rect x="0" y="0" width="600" height="10" fill="#c9a86a"/><text x="48" y="76" fill="#f2ede3" font-family="sans-serif" font-size="42" font-weight="800">${escapeXml(cardTitle)}</text><text x="48" y="110" fill="#b9b0a0" font-family="sans-serif" font-size="20">${t("ui.finalStandings")}</text>${rows}<text x="48" y="328" fill="#b9b0a0" font-family="sans-serif" font-size="17">${escapeXml(meta)}</text></svg>`;
        const url = URL.createObjectURL(new Blob([svg], { type: "image/svg+xml" }));
        const a = document.createElement("a");
        a.href = url;
        a.download = "results-card.svg";
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 0);
    };

    if (top.length === 0) return null;

    return (
        <div className="flex flex-wrap items-center justify-between gap-4 border border-[var(--oq-border)] bg-[var(--oq-row-alt)] p-4">
            <div className="min-w-0">
                <p className="label-caps mb-2">{t("ui.shareableCard")}</p>
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
                    {copied ? t("ui.copied") : t("ui.copySummary")}
                </Button>
                <Button variant="secondary" size="sm" onClick={download}>
                    {t("ui.downloadCard")}
                </Button>
            </div>
        </div>
    );
}
