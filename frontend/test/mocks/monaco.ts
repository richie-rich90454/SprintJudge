/** Test double for monaco-editor (unit tests only, never bundled). */
export const behavior = {
    failCreate: false,
    partialMount: false,
    nullModel: false,
};

export interface FakeModel {
    lang: string;
}

export interface FakeEditor {
    host: unknown;
    options: Record<string, unknown>;
    value: string;
    disposed: boolean;
    listener: (() => void) | null;
    getValue(): string;
    getModel(): FakeModel | null;
    onDidChangeModelContent(fn: () => void): { dispose(): void };
    dispose(): void;
}

function makeEditor(host: unknown, options: Record<string, unknown>): FakeEditor {
    if (behavior.partialMount) {
        (host as HTMLElement).appendChild(document.createElement("div"));
    }
    if (behavior.failCreate || behavior.partialMount) {
        throw new Error("Could not create web worker");
    }
    const ed: FakeEditor = {
        host,
        options,
        value: String(options["value"] ?? ""),
        disposed: false,
        listener: null,
        getValue() {
            return ed.value;
        },
        getModel() {
            if (behavior.nullModel) return null;
            return {
                get lang() {
                    return String(ed.options["language"] ?? "");
                },
                set lang(v: string) {
                    ed.options["language"] = v;
                },
            };
        },
        onDidChangeModelContent(fn: () => void) {
            ed.listener = fn;
            return {
                dispose() {
                    ed.listener = null;
                },
            };
        },
        dispose() {
            ed.disposed = true;
            ed.listener = null;
        },
    };
    return ed;
}

export const editor = {
    create(host: unknown, options: Record<string, unknown>): FakeEditor {
        const ed = makeEditor(host, options);
        (host as HTMLElement).appendChild(document.createElement("div"));
        created.push(ed);
        return ed;
    },
    setModelLanguage(model: FakeModel, lang: string) {
        model.lang = lang;
    },
};

export function __setEditorListener(ed: FakeEditor, fn: () => void) {
    ed.listener = fn;
}

/** Every editor created through the stub, in order (reset between tests). */
export const created: FakeEditor[] = [];

/** Simulate typing: set value and fire the change listener. */
export function __typeText(ed: FakeEditor, text: string) {
    ed.value = text;
    ed.listener?.();
}

/** Stand-in for the ?worker module default export. */
export default class FakeWorker {
    terminate() {}
}
