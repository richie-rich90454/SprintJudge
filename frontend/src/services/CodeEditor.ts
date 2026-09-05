import type * as Monaco from "monaco-editor";
import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";

// Without a worker environment, monaco.editor.create() throws ("Could not create
// web worker") and the textarea fallback is what renders. The base editor worker
// is sufficient for all languages we use (Monarch tokenization ships in-bundle).
const monacoEnv = self as unknown as { MonacoEnvironment?: Monaco.Environment };
monacoEnv.MonacoEnvironment = {
    getWorker: () => new EditorWorker(),
};

export interface CodeEditorHandle {
    getValue(): string;
    setLanguage(lang: string): void;
    destroy(): void;
}

interface CodeEditorOptions {
    value: string;
    language: string;
    height?: number;
    onChange: (value: string) => void;
}

/**
 * Lazy Monaco mount with a plain-textarea fallback. Monaco ships in its own
 * chunk and loads only when an OJ question actually renders; if it fails
 * (offline shell, legacy bundle) players still get a working mono textarea.
 */
export async function createCodeEditor(
    host: HTMLElement,
    opts: CodeEditorOptions,
): Promise<CodeEditorHandle> {
    try {
        const monaco = await import("monaco-editor");
        const ed = monaco.editor.create(host, {
            value: opts.value,
            language: opts.language,
            theme: "vs",
            minimap: { enabled: false },
            fontSize: 13,
            fontFamily: "Noto Sans",
            automaticLayout: true,
            scrollBeyondLastLine: false,
            lineNumbers: "on",
            tabSize: 4,
            renderLineHighlight: "none",
            overviewRulerLanes: 0,
            scrollbar: { verticalScrollbarSize: 10, horizontalScrollbarSize: 10 },
        });
        const sub = ed.onDidChangeModelContent(() => opts.onChange(ed.getValue()));
        return {
            getValue: () => ed.getValue(),
            setLanguage: (lang: string) => {
                const model = ed.getModel();
                if (model) monaco.editor.setModelLanguage(model, lang);
            },
            destroy: () => {
                sub.dispose();
                ed.dispose();
            },
        };
    } catch {
        // A partial Monaco mount may have left broken DOM behind: clear it so
        // the fallback does not render alongside a dead editor.
        host.replaceChildren();
        const ta = document.createElement("textarea");
        ta.className =
            "mono w-full p-3 rounded-lg border border-border bg-surface text-sm resize-y";
        ta.style.minHeight = `${opts.height ?? 260}px`;
        ta.spellcheck = false;
        ta.value = opts.value;
        const handler = () => opts.onChange(ta.value);
        ta.addEventListener("input", handler);
        host.appendChild(ta);
        return {
            getValue: () => ta.value,
            setLanguage: () => {},
            destroy: () => {
                ta.removeEventListener("input", handler);
                ta.remove();
            },
        };
    }
}
