import { useMemo } from "react";
import { checkItemQuality } from "./itemQuality";

interface Props {
    type: string;
    title: string;
    description: string;
    config: Record<string, unknown>;
}

export function ItemQualityPanel({ type, title, description, config }: Props) {
    const findings = useMemo(
        () => checkItemQuality({ type, title, description, config }),
        [type, title, description, config],
    );
    const errors = findings.filter((f) => f.level === "error").length;
    return (
        <section className="border-t border-[var(--oq-border)] pt-4 mt-4" aria-label="Item quality">
            <div className="flex items-center gap-3 flex-wrap">
                <h3 className="text-sm font-bold m-0">Item quality</h3>
                <span className="text-xs text-[var(--oq-ink-soft)]">
                    {errors > 0 ? `${errors} blocking, ` : ""}{findings.length - errors} advisory
                </span>
            </div>
            {findings.length === 0 ? (
                <p className="text-sm text-[var(--oq-ink-soft)] mt-2">No findings from the bank advisory rules.</p>
            ) : (
                <ul className="flex flex-col gap-1 mt-2 mb-0 pl-5 text-sm">
                    {findings.map((f, i) => (
                        <li key={i} className={f.level === "error" ? "text-[var(--oq-danger)]" : "text-[var(--oq-ink-soft)]"}>
                            {f.level === "error" ? "Blocking: " : "Advisory: "}{f.message}
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}
