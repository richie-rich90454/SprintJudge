import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";

export class CodeCompletionRenderer extends BaseQuestionRenderer {
  private value = "";

  mount(): void {
    const skeleton = (this.config["skeleton"] as string) ?? "";
    this.value = skeleton;
    const ta = el("textarea", {
      class: "mono w-full min-h-[160px] p-3 rounded-lg border border-border bg-surface text-sm resize-y",
      spellcheck: false,
    });
    ta.value = skeleton;
    ta.addEventListener("input", () => {
      this.value = ta.value;
      this.emit({ code: this.value });
    });
    this.container.append(ta);
  }

  getResponse(): unknown {
    return { code: this.value };
  }
}
