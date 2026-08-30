import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";
import { createCodeEditor, CodeEditorHandle } from "../CodeEditor";
import { Terminal } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import { audio } from "../AudioEngine";

const LANGUAGES = [
    { id: "c", label: "C", monaco: "c" },
    { id: "cpp", label: "C++", monaco: "cpp" },
    { id: "java", label: "Java", monaco: "java" },
    { id: "node", label: "Node.js", monaco: "javascript" },
    { id: "python", label: "Python", monaco: "python" },
];

/**
 * Shared editor surface for the two Online-Judge formats.
 *
 * Language handling is question-driven: only languages in the question's
 * `languagesAllowed` are selectable (server enforces the same rule), and the
 * default selection honors the requested default when it is permitted.
 * A real Monaco editor mounts lazily; a mono textarea is the fallback.
 *
 * A live console (xterm.js, canvas-rendered + high-DPI) sits under the editor
 * with a Run button so students can experiment before submitting — mirroring
 * JuiceMind's in-quiz runner.
 */
export abstract class OjBase extends BaseQuestionRenderer {
    protected language = "python";
    protected source = "";
    private editor: CodeEditorHandle | null = null;
    private editorHost: HTMLElement | null = null;
    private terminal: Terminal | null = null;
    private fit: FitAddon | null = null;
    private destroyed = false;
    private rafId = 0;

    /** Requested default before allowlist resolution (subclass sets pre-mount). */
    protected requestedDefault = "python";

    protected mountEditor(initial: string): void {
        const known = new Set(LANGUAGES.map((l) => l.id));
        const allowed = (this.allowedLanguages ?? []).filter((l) => known.has(l));
        const choices = allowed.length ? allowed : [...known];

        // Smart default: honor the requested language when permitted, else first allowed.
        this.language = choices.includes(this.language) ? this.language : choices[0];

        if (choices.length > 1) {
            const select = el("select", {
                class: "min-h-tap px-3 py-2 rounded-lg border border-border bg-surface font-mono text-sm mb-2",
            });
            LANGUAGES.filter((l) => choices.includes(l.id)).forEach((l) => {
                select.append(el("option", { value: l.id }, [l.label]));
            });
            select.value = this.language;
            select.addEventListener("change", () => {
                this.language = select.value;
                this.emitResponse();
            });
            this.container.append(select);
        }

        // Restore any cached draft for this question.
        let start = initial;
        if (this.questionId) {
            try {
                const cached = localStorage.getItem(`sprintjudge_code_${this.questionId}`);
                if (cached !== null) start = cached;
            } catch {
                /* ignore */
            }
        }
        this.source = start;
        // Emit the initial source/language so a submit (or timer-expiry auto-submit)
        // before any keystroke still carries the starter/cached code, not null.
        this.emitResponse();

        this.editorHost = el("div", {
            class: "rounded-lg overflow-hidden border border-border flex-1 min-h-0",
        });
        this.container.append(this.editorHost);

        const monacoLang = LANGUAGES.find((l) => l.id === this.language)?.monaco ?? "plaintext";
        void createCodeEditor(this.editorHost, {
            value: start,
            language: monacoLang,
            onChange: (v) => {
                this.source = v;
                if (this.questionId) {
                    try {
                        localStorage.setItem(`sprintjudge_code_${this.questionId}`, v);
                    } catch {
                        /* ignore */
                    }
                }
                this.emitResponse();
            },
        }).then((handle) => {
            if (this.destroyed) {
                handle.destroy();
                return;
            }
            this.editor = handle;
        });

        this.mountConsole();
    }

    private mountConsole(): void {
        const wrap = el("div", {
            class: "mt-3 rounded-lg overflow-hidden border border-border flex flex-col min-h-0",
        });
        wrap.style.flex = "1 1 0";
        const bar = el("div", {
            class: "flex items-center gap-2 px-3 py-2 bg-[var(--oq-row-alt)] border-b border-border",
        });
        const runBtn = el("button", {
            class: "btn btn-primary btn-sm",
            textContent: "▶ Run",
        });
        const hint = el("span", {
            class: "text-xs text-default-500 mono",
            textContent: "interactive console",
        });
        bar.append(runBtn, hint);

        const termHost = el("div", { class: "flex-1 min-h-0 p-2 bg-black" });
        wrap.append(bar, termHost);
        this.container.append(wrap);

        this.terminal = new Terminal({
            fontFamily: "Noto Sans Mono, monospace",
            fontSize: 12,
            cursorBlink: true,
            theme: {
                background: "#000000",
                foreground: "#e6e6e6",
                cursor: "#ff2e63",
            },
            convertEol: true,
        });
        this.fit = new FitAddon();
        this.terminal.loadAddon(this.fit);
        this.terminal.open(termHost);
        // Fit after layout settles (the host starts at 0 height before flex).
        this.rafId = requestAnimationFrame(() => {
            this.rafId = 0;
            this.fit?.fit();
        });

        let stdin = "";
        this.terminal.onData((d) => {
            stdin += d;
            this.terminal?.write(d);
        });

        runBtn.addEventListener("click", async () => {
            audio.resume();
            audio.play("click");
            runBtn.setAttribute("disabled", "true");
            this.terminal?.write("\r\n\x1b[90m$ running…\x1b[0m\r\n");
            try {
                const res = await fetch("/api/public/run", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        language: this.language,
                        sourceCode: this.source,
                        stdin,
                        timeoutSec: 10,
                    }),
                });
                if (!res.ok) {
                    this.terminal?.write(
                        `\r\n\x1b[31m[error] ${res.status === 429 ? "rate limited" : res.status === 404 ? "runner not found" : "server error"}\x1b[0m\r\n`,
                    );
                    stdin = "";
                    this.fit?.fit();
                    return;
                }
                const data = (await res.json()) as {
                    ok: boolean;
                    output: string;
                    status: string;
                };
                stdin = "";
                if (data.output) this.terminal?.write(data.output.replace(/\n/g, "\r\n"));
                if (!data.ok) {
                    this.terminal?.write(
                        `\r\n\x1b[31m[${data.status}] program exited non-zero\x1b[0m\r\n`,
                    );
                }
                this.terminal?.write("\r\n\x1b[90m$ \x1b[0m");
            } catch (e) {
                stdin = "";
                this.terminal?.write(
                    "\r\n\x1b[31m[error] runner unavailable — check that g++/gcc/python/node is installed\x1b[0m\r\n",
                );
            } finally {
                runBtn.removeAttribute("disabled");
                this.fit?.fit();
            }
        });
    }

    protected emitResponse(): void {
        this.emit({ source: this.source, language: this.language });
    }

    destroy(): void {
        this.destroyed = true;
        if (this.rafId) {
            cancelAnimationFrame(this.rafId);
            this.rafId = 0;
        }
        this.editor?.destroy();
        this.editor = null;
        this.terminal?.dispose();
        this.terminal = null;
        super.destroy();
    }
}
