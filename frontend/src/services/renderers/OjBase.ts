import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";
import { createCodeEditor, CodeEditorHandle } from "../CodeEditor";

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
 */
export abstract class OjBase extends BaseQuestionRenderer {
  protected language = "python";
  protected source = "";
  private editor: CodeEditorHandle | null = null;
  private editorHost: HTMLElement | null = null;
  private destroyed = false;

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
      } catch { /* ignore */ }
    }
    this.source = start;

    this.editorHost = el("div", { class: "rounded-lg overflow-hidden border border-border" });
    this.container.append(this.editorHost);

    const monacoLang = LANGUAGES.find((l) => l.id === this.language)?.monaco ?? "plaintext";
    void createCodeEditor(this.editorHost, {
      value: start,
      language: monacoLang,
      onChange: (v) => {
        this.source = v;
        if (this.questionId) {
          try { localStorage.setItem(`sprintjudge_code_${this.questionId}`, v); } catch { /* ignore */ }
        }
        this.emitResponse();
      },
    }).then((handle) => {
      if (this.destroyed) { handle.destroy(); return; }
      this.editor = handle;
    });
  }

  protected emitResponse(): void {
    this.emit({ source: this.source, language: this.language });
  }

  destroy(): void {
    this.destroyed = true;
    this.editor?.destroy();
    this.editor = null;
    super.destroy();
  }
}
