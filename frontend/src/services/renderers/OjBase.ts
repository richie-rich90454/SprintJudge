import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";

const LANGUAGES = [
  { id: "c", label: "C" },
  { id: "cpp", label: "C++" },
  { id: "java", label: "Java" },
  { id: "node", label: "Node.js" },
  { id: "python", label: "Python" },
];

/**
 * Shared editor surface for the two Online-Judge formats. Uses a mono
 * textarea (the lightweight path); the admin wizard mounts MonacoLazy.
 */
export abstract class OjBase extends BaseQuestionRenderer {
  protected language = "python";
  protected source = "";

  protected mountEditor(initial: string, allowed: string[] | null): void {
    const langs = allowed && allowed.length ? allowed : LANGUAGES.map((l) => l.id);
    const select = el("select", {
      class: "min-h-tap px-3 py-2 rounded-lg border border-border bg-surface font-mono text-sm mb-2",
    });
    LANGUAGES.filter((l) => langs.includes(l.id)).forEach((l) => {
      const o = el("option", { value: l.id }, [l.label]);
      select.append(o);
    });
    if (langs.includes(this.language)) select.value = this.language;
    select.addEventListener("change", () => {
      this.language = select.value;
      this.emitResponse();
    });

    const ta = el("textarea", {
      class: "mono w-full min-h-[260px] p-3 rounded-lg border border-border bg-surface text-sm resize-y",
      spellcheck: false,
    });
    ta.value = initial;
    // Frontend resilience: restore any cached draft for this question.
    if (this.questionId) {
      try {
        const cached = localStorage.getItem(`sprintjudge_code_${this.questionId}`);
        if (cached) {
          ta.value = cached;
          this.source = cached;
        }
      } catch { /* ignore */ }
    }
    ta.addEventListener("input", () => {
      this.source = ta.value;
      if (this.questionId) {
        try { localStorage.setItem(`sprintjudge_code_${this.questionId}`, this.source); } catch { /* ignore */ }
      }
      this.emitResponse();
    });

    this.container.append(select, ta);
  }

  protected emitResponse(): void {
    this.emit({ source: this.source, language: this.language });
  }
}
