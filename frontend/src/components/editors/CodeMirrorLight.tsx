import { useEffect, useRef } from "react";

interface CodeMirrorLightProps {
  value: string;
  language?: string;
  onChange?: (value: string) => void;
}

/**
 * Lightweight code editor: a controlled, mono-faced textarea with line
 * gutter styling. No separate dependency — the "lightweight" alternative to
 * Monaco for fill-in-the-blank and code-completion questions.
 */
export function CodeMirrorLight({ value, onChange }: CodeMirrorLightProps) {
  const ref = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (ref.current && ref.current.value !== value) ref.current.value = value;
  }, [value]);

  return (
    <textarea
      ref={ref}
      defaultValue={value}
      spellCheck={false}
      onChange={(e) => onChange?.(e.target.value)}
      className="mono w-full min-h-[140px] p-3 rounded-lg border border-border bg-surface text-sm resize-y"
    />
  );
}
