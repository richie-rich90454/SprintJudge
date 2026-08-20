import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";

export class ComplexityRenderer extends BaseQuestionRenderer {
  private selected = -1;

  mount(): void {
    const options = (this.config["options"] as string[]) ?? [];
    const wrap = el("div", { class: "flex flex-col gap-2" });
    options.forEach((opt, i) => {
      const btn = el("button", {
        class: "min-h-tap text-left px-4 py-3 rounded-lg border border-border bg-surface hover:border-primary font-mono",
        type: "button",
      }, [opt]);
      btn.addEventListener("click", () => {
        this.selected = i;
        wrap.querySelectorAll("button").forEach((b) => b.classList.remove("border-primary", "bg-surface-alt"));
        btn.classList.add("border-primary", "bg-surface-alt");
        this.emit({ selectedIndex: i });
      });
      wrap.append(btn);
    });
    this.container.append(wrap);
  }

  getResponse(): unknown {
    return { selectedIndex: this.selected };
  }
}
