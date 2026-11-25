import { useMemo } from "react";
import { checkItemQuality } from "./itemQuality";
import { useI18n } from "../../i18n";

interface Props {
    type: string;
    title: string;
    description: string;
    config: Record<string, unknown>;
}

export function ItemQualityPanel({ type, title, description, config }: Props) {
    const { t } = useI18n();
    const findings = useMemo(
        () => checkItemQuality({ type, title, description, config }),
        [type, title, description, config],
    );
    const errors = findings.filter((f) => f.level === "error").length;
    const advisories = findings.length - errors;
    const advisoryWord = t("ui.advisoryWord");
    const counts = errors > 0
        ? `${errors} ${t("ui.blockingWord")}, ${advisories} ${advisoryWord}`
        : `${advisories} ${advisoryWord}`;
    return (
        <section className="border-t border-[var(--oq-border)] pt-4 mt-4" aria-label={t("ui.itemQuality")}>
            <div className="flex items-center gap-3 flex-wrap">
                <h3 className="text-sm font-bold m-0">{t("ui.itemQuality")}</h3>
                <span className="text-xs text-[var(--oq-ink-soft)]">
                    {counts}
                </span>
            </div>
            {findings.length === 0 ? (
                <p className="text-sm text-[var(--oq-ink-soft)] mt-2">{t("ui.qualityNoFindings")}</p>
            ) : (
                <ul className="flex flex-col gap-1 mt-2 mb-0 pl-5 text-sm">
                    {findings.map((f, i) => (
                        <li key={i} className={f.level === "error" ? "text-[var(--oq-danger)]" : "text-[var(--oq-ink-soft)]"}>
                            {t(f.level === "error" ? "ui.blockingPrefix" : "ui.advisoryPrefix")}{t(f.key, f.vars)}
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}
