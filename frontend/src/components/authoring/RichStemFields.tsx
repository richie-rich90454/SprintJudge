import { isSafeMediaUrl, latexToText, RichStem } from "./richStem";

const fieldClass = "input-underline w-full";
const monoClass =
    "mono w-full min-h-[80px] p-3 border border-[var(--oq-border)] bg-[var(--oq-surface)] text-sm rounded-[2px]";

interface EditorProps {
    value: RichStem;
    onChange: (next: RichStem) => void;
}

export function RichStemEditor({ value, onChange }: EditorProps) {
    const set = (patch: Partial<RichStem>) => onChange({ ...value, ...patch });
    return (
        <div className="flex flex-col gap-4 border-t border-[var(--oq-border)] pt-4">
            <p className="text-sm font-bold">Rich stem media</p>
            <p className="text-[var(--oq-ink-soft)] text-sm">
                Optional. Media and markup are stored in config.rich and preview below.
            </p>
            <label className="text-sm flex flex-col gap-1">
                Image URL
                <input value={value.imageUrl ?? ""} onChange={(e) => set({ imageUrl: e.target.value })} className={fieldClass} />
            </label>
            <div className="flex gap-4 flex-wrap">
                <label className="text-sm flex-1">
                    Image alt text
                    <input value={value.imageAlt ?? ""} onChange={(e) => set({ imageAlt: e.target.value })} className={fieldClass} />
                </label>
                <label className="text-sm flex-1">
                    Image caption / source
                    <input value={value.imageCaption ?? ""} onChange={(e) => set({ imageCaption: e.target.value })} className={fieldClass} />
                </label>
            </div>
            <label className="text-sm flex flex-col gap-1">
                Audio URL
                <input value={value.audioUrl ?? ""} onChange={(e) => set({ audioUrl: e.target.value })} className={fieldClass} />
            </label>
            <label className="text-sm flex flex-col gap-1">
                Audio transcript
                <textarea value={value.audioTranscript ?? ""} onChange={(e) => set({ audioTranscript: e.target.value })} className={monoClass} />
            </label>
            <label className="text-sm flex flex-col gap-1">
                Media source / license
                <input value={value.mediaLicense ?? ""} onChange={(e) => set({ mediaLicense: e.target.value })} className={fieldClass} />
            </label>
            <label className="text-sm flex flex-col gap-1">
                LaTeX (use $...$ delimiters)
                <textarea value={value.latex ?? ""} onChange={(e) => set({ latex: e.target.value })} className={monoClass} />
            </label>
            <div className="flex gap-4 flex-wrap items-start">
                <label className="text-sm flex-1">
                    Code language
                    <input value={value.codeLanguage ?? ""} onChange={(e) => set({ codeLanguage: e.target.value })} className={fieldClass} />
                </label>
                <label className="text-sm flex-[3]">
                    Code snippet
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
    const imageOk = !!value.imageUrl && isSafeMediaUrl(value.imageUrl, "image");
    const audioOk = !!value.audioUrl && isSafeMediaUrl(value.audioUrl, "audio");
    const hasAny = imageOk || audioOk || !!value.latex || !!value.code;
    return (
        <div className="flex flex-col gap-3" aria-label="Rich stem preview">
            {description && <p className="text-sm whitespace-pre-wrap">{description}</p>}
            {imageOk && (
                <figure className="m-0">
                    <img src={value.imageUrl} alt={value.imageAlt ?? "Stem image"} className="max-w-full max-h-64 object-contain" />
                    {value.imageCaption && <figcaption className="text-[var(--oq-ink-soft)] text-xs mt-1">{value.imageCaption}</figcaption>}
                </figure>
            )}
            {value.imageUrl && !imageOk && (
                <p className="text-[var(--oq-danger)] text-xs">Image URL blocked by the preview allowlist.</p>
            )}
            {audioOk && (
                <div>
                    <audio controls src={value.audioUrl} className="w-full" />
                    {value.audioTranscript && (
                        <details className="text-sm mt-1">
                            <summary className="cursor-pointer">Transcript</summary>
                            <p className="whitespace-pre-wrap">{value.audioTranscript}</p>
                        </details>
                    )}
                </div>
            )}
            {value.audioUrl && !audioOk && (
                <p className="text-[var(--oq-danger)] text-xs">Audio URL blocked by the preview allowlist.</p>
            )}
            {value.latex && (
                <div>
                    <p className="text-[var(--oq-ink-soft)] text-xs">LaTeX (approximate preview)</p>
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
                <p className="text-[var(--oq-ink-soft)] text-sm">Add media or markup to see a preview.</p>
            )}
        </div>
    );
}
