import { useEffect, useRef } from "react";

interface MonacoLazyProps {
  value: string;
  language?: string;
  height?: number;
  onChange?: (value: string) => void;
}

/**
 * Lazily loads the Monaco editor (only when an OJ question is shown) so the
 * initial bundle stays small. Falls back silently if Monaco fails to load.
 */
export function MonacoLazy({ value, language = "python", height = 320, onChange }: MonacoLazyProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<unknown>(null);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  useEffect(() => {
    let cancelled = false;
    let dispose: (() => void) | undefined;
    (async () => {
      try {
        const monaco = await import("monaco-editor");
        if (cancelled || !hostRef.current) return;
        const ed = monaco.editor.create(hostRef.current, {
          value,
          language,
          theme: "vs",
          minimap: { enabled: false },
          fontSize: 13,
          fontFamily: "Noto Sans Mono, monospace",
          automaticLayout: true,
          scrollBeyondLastLine: false,
        });
        ed.onDidChangeModelContent(() => onChangeRef.current?.(ed.getValue()));
        editorRef.current = ed;
        dispose = () => ed.dispose();
      } catch {
        const ta = document.createElement("textarea");
        ta.className = "mono w-full p-3 border border-border rounded-lg bg-surface text-sm";
        ta.style.height = `${height}px`;
        ta.value = value;
        ta.addEventListener("input", () => onChangeRef.current?.(ta.value));
        hostRef.current?.append(ta);
        dispose = () => ta.remove();
      }
    })();
    return () => {
      cancelled = true;
      dispose?.();
    };
  }, [language]);

  return <div ref={hostRef} style={{ height }} className="rounded-lg overflow-hidden border border-border" />;
}
