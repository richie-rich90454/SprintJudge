import { Fragment } from "react";

function InlineText({ text }: { text: string }) {
    const parts = text.split("`");
    return (
        <>
            {parts.map((part, i) =>
                i % 2 === 1 ? (
                    <code
                        key={i}
                        className="rounded-[2px] border border-[var(--oq-border)] bg-[var(--oq-row-alt)] px-1.5 py-0.5 font-mono text-[0.92em]"
                    >
                        {part}
                    </code>
                ) : (
                    <Fragment key={i}>{part}</Fragment>
                ),
            )}
        </>
    );
}

function CodeBlock({ code }: { code: string }) {
    const withoutLang = code.replace(/^[a-zA-Z0-9+#-]*\r?\n/, "");
    const lines = withoutLang.replace(/\s+$/, "").split(/\r?\n/);
    return (
        <pre className="my-4 overflow-x-auto rounded-[2px] border border-[var(--stage-border,var(--oq-border))] bg-[var(--stage-surface,#151619)] p-4 text-[var(--stage-ink,#f2ede3)]">
            <code className="block font-mono text-[0.9rem] leading-6">
                {lines.map((line, i) => (
                    <span key={i} className="flex gap-4">
                        <span
                            aria-hidden="true"
                            className="w-6 shrink-0 select-none text-right text-[var(--stage-ink-soft,#b9b0a0)]"
                        >
                            {i + 1}
                        </span>
                        <span className="whitespace-pre">{line || " "}</span>
                    </span>
                ))}
            </code>
        </pre>
    );
}

/**
 * Safe, dependency-free renderer for the subset of Markdown the question
 * bank uses: fenced code blocks and inline code. No HTML is ever injected.
 */
export function FormattedText({ text, className = "" }: { text: string; className?: string }) {
    if (!text) return null;
    const parts = text.split("```");
    return (
        <div className={`whitespace-pre-wrap leading-relaxed ${className}`.trim()}>
            {parts.map((part, i) =>
                i % 2 === 1 ? <CodeBlock key={i} code={part} /> : <InlineText key={i} text={part} />,
            )}
        </div>
    );
}
