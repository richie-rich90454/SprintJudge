import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";

export class MultipleSelectRenderer extends BaseQuestionRenderer {
  private selected: number[] = [];

  mount(): void {
    const options = (this.config["options"] as string[]) ?? [];
    const wrap = el("div", { class: "flex flex-col gap-2" });
    options.forEach((opt, i) => {
      const btn = el("button", {
        class: "min-h-tap text-left px-4 py-3 rounded-lg border border-border bg-surface hover:border-primary",
        type: "button",
      });
      btn.append(el("span", { class: "font-mono font-semibold mr-2" }, [String.fromCharCode(65 + i)]), opt);
      btn.addEventListener("click", () => {
        const idx = this.selected.indexOf(i);
        if (idx >= 0) {
          this.selected.splice(idx, 1);
          btn.classList.remove("border-primary", "bg-surface-alt");
        } else {
          this.selected.push(i);
          btn.classList.add("border-primary", "bg-surface-alt");
        }
        this.emit({ selectedIndices: [...this.selected].sort((a, b) => a - b) });
      });
      wrap.append(btn);
    });
    const hint = el("p", { class: "text-sm text-muted mt-1" }, ["Partial scoring applies."]);
    this.container.append(wrap, hint);
  }

  getResponse(): unknown {
    return { selectedIndices: this.selected };
  }
}
