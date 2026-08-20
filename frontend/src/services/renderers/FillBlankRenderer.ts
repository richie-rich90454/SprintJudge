import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";

export class FillBlankRenderer extends BaseQuestionRenderer {
  private value = "";

  mount(): void {
    const snippet = el("pre", { class: "mono text-sm bg-surface-alt border border-border rounded-lg p-4 overflow-auto whitespace-pre-wrap" },
      [(this.config["snippet"] as string) ?? ""]);
    const input = el("input", {
      class: "w-full min-h-tap px-4 py-3 rounded-lg border border-border bg-surface font-mono",
      type: "text",
      placeholder: "Fill the blank",
    });
    input.addEventListener("input", () => {
      this.value = input.value;
      this.emit({ text: this.value });
    });
    this.container.append(snippet, input);
  }

  getResponse(): unknown {
    return { text: this.value };
  }
}
