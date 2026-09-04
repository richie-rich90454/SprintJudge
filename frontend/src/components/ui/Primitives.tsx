import { ReactNode } from "react";

/** Small status pill. Tone maps to chip-accent/neutral/success/danger. */
export function Chip({
    tone = "neutral",
    children,
}: {
    tone?: "accent" | "neutral" | "success" | "danger";
    children: ReactNode;
}) {
    return <span className={`chip chip-${tone}`}>{children}</span>;
}

/** Labeled form field wrapper. */
export function Field({
    label,
    htmlFor,
    children,
}: {
    label: string;
    htmlFor?: string;
    children: ReactNode;
}) {
    return (
        <div className="text-left">
            <label className="field-label" htmlFor={htmlFor}>
                {label}
            </label>
            {children}
        </div>
    );
}

/** Friendly empty/loading-error placeholder. */
export function EmptyState({
    title,
    hint,
    action,
}: {
    title: string;
    hint?: string;
    action?: ReactNode;
}) {
    return (
        <div className="text-center py-10 px-6">
            <p className="font-extrabold text-lg">{title}</p>
            {hint && <p className="text-[var(--oq-ink-soft)] text-sm mt-2">{hint}</p>}
            {action && <div className="mt-4 flex justify-center">{action}</div>}
        </div>
    );
}

/** Loading shimmer bar. */
export function Skeleton({ className = "" }: { className?: string }) {
    return <div aria-hidden="true" className={`skeleton ${className}`.trim()} />;
}

/** Deterministic initial-disc avatar. */
export function Avatar({ name, size = 36 }: { name: string; size?: number }) {
    const initial = (name.trim().charAt(0) || "?").toUpperCase();
    return (
        <span
            aria-hidden="true"
            className="avatar-disc"
            style={{ width: size, height: size, fontSize: size * 0.45 }}
        >
            {initial}
        </span>
    );
}
