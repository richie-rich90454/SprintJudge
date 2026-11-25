import { isSafeMediaUrl, latexToText, RichStem } from "./richStem";
import { useI18n } from "../../i18n";

const fieldClass = "input-underline w-full";
const monoClass =
    "mono w-full min-h-[80px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[2px]";

interface EditorProps {
    value: RichStem;
    onChange: (next: RichStem) => void;
}

export function RichStemEditor({ value, onChange }: EditorProps) {
    const { t } = useI18n();
    const set = (patch: Partial<RichStem>) => onChange({ ...value, ...patch });
    return (
        <div className="flex flex-col gap-4 border-t border-[var(--oq-border)] pt-4">
            <p className="text-sm font-bold">{t("ui.richStemMedia")}</p>
            <p className="text-[var(--oq-ink-soft)] text-sm">
                {t("ui.richStemHelp")}
            </p>
            <label className="text-sm flex flex-col gap-1">
                {t("ui.imageUrl")}
                <input value={value.imageUrl ?? ""} onChange={(e) => set({ imageUrl: e.target.value })} className={fieldClass} />
            </label>
            <div className="flex gap-4 flex-wrap">
                <label className="text-sm flex-1">
                    {t("ui.imageAlt")}
                    <input value={value.imageAlt ?? ""} onChange={(e) => set({ imageAlt: e.target.value })} className={fieldClass} />
                </label>
                <label className="text-sm flex-1">
                    {t("ui.imageCaption")}
                    <input value={value.imageCaption ?? ""} onChange={(e) => set({ imageCaption: e.target.value })} className={fieldClass} />
                </label>
            </div>
            <label className="text-sm flex flex-col gap-1">
                {t("ui.audioUrl")}
                <input value={value.audioUrl ?? ""} onChange={(e) => set({ audioUrl: e.target.value })} className={fieldClass} />
            </label>
            <label className="text-sm flex flex-col gap-1">
                {t("ui.audioTranscript")}
                <textarea value={value.audioTranscript ?? ""} onChange={(e) => set({ audioTranscript: e.target.value })} className={monoClass} />
            </label>
            <label className="text-sm flex flex-col gap-1">
                {t("ui.mediaLicense")}
                <input value={value.mediaLicense ?? ""} onChange={(e) => set({ mediaLicense: e.target.value })} className={fieldClass} />
            </label>
            <label className="text-sm flex flex-col gap-1">
                {t("ui.latexHint")}
                <textarea value={value.latex ?? ""} onChange={(e) => set({ latex: e.target.value })} className={monoClass} />
            </label>
            <div className="flex gap-4 flex-wrap items-start">
                <label className="text-sm flex-1">
                    {t("ui.codeLanguage")}
                    <input value={value.codeLanguage ?? ""} onChange={(e) => set({ codeLanguage: e.target.value })} className={fieldClass} />
                </label>
                <label className="text-sm flex-[3]">
                    {t("ui.codeSnippet")}
                    <textarea value={value.code ?? ""} onChange={(e) => set({ code: e.target.value })} className={monoClass} />
                </label>
            </div>
        </div>
    );
}

interface PreviewProps {
    value: RichStem;
    description?: string;
}

export function RichStemPreview({ value, description }: PreviewProps) {
    const { t } = useI18n();
    const imageOk = !!value.imageUrl && isSafeMediaUrl(value.imageUrl, "image");
    const audioOk = !!value.audioUrl && isSafeMediaUrl(value.audioUrl, "audio");
    const hasAny = imageOk || audioOk || !!value.latex || !!value.code;
    return (
        <div className="flex flex-col gap-3" aria-label={t("ui.richStemPreview")}>
            {description && <p className="text-sm whitespace-pre-wrap">{description}</p>}
            {imageOk && (
                <figure className="m-0">
                    <img src={value.imageUrl} alt={value.imageAlt ?? t("ui.stemImage")} className="max-w-full max-h-64 object-contain" />
                    {value.imageCaption && <figcaption className="text-[var(--oq-ink-soft)] text-xs mt-1">{value.imageCaption}</figcaption>}
                </figure>
            )}
            {value.imageUrl && !imageOk && (
                <p className="text-[var(--oq-danger)] text-xs">{t("ui.imageBlocked")}</p>
            )}
            {audioOk && (
                <div>
                    <audio controls src={value.audioUrl} className="w-full" />
                    {value.audioTranscript && (
                        <details className="text-sm mt-1">
                            <summary className="cursor-pointer">{t("ui.transcript")}</summary>
                            <p className="whitespace-pre-wrap">{value.audioTranscript}</p>
                        </details>
                    )}
                </div>
            )}
            {value.audioUrl && !audioOk && (
                <p className="text-[var(--oq-danger)] text-xs">{t("ui.audioBlocked")}</p>
            )}
            {value.latex && (
                <div>
                    <p className="text-[var(--oq-ink-soft)] text-xs">{t("ui.latexApprox")}</p>
                    <p className="text-lg">{latexToText(value.latex)}</p>
                    <code className="mono text-xs text-[var(--oq-ink-soft)]">{value.latex}</code>
                </div>
            )}
            {value.code && (
                <pre className="mono text-xs overflow-x-auto p-3 bg-[var(--oq-surface)] border border-[var(--oq-border)] rounded-[2px]">
                    <code>{value.codeLanguage ? `${value.codeLanguage}: ` : ""}{value.code}</code>
                </pre>
            )}
            {!hasAny && (
                <p className="text-[var(--oq-ink-soft)] text-sm">{t("ui.addMediaHint")}</p>
            )}
        </div>
    );
}
