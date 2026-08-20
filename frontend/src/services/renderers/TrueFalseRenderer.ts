import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";

export class TrueFalseRenderer extends BaseQuestionRenderer {
  private value: boolean | null = null;

  mount(): void {
    const wrap = el("div", { class: "flex gap-3" });
    const mk = (label: string, v: boolean) => {
      const btn = el("button", {
        class: "min-h-tap flex-1 px-4 py-3 rounded-lg border border-border bg-surface hover:border-primary",
        type: "button",
      }, [label]);
      btn.addEventListener("click", () => {
        this.value = v;
        wrap.querySelectorAll("button").forEach((b) => b.classList.remove("border-primary", "bg-surface-alt"));
        btn.classList.add("border-primary", "bg-surface-alt");
        this.emit({ value: v });
      });
      return btn;
    };
    wrap.append(mk("True", true), mk("False", false));
    this.container.append(wrap);
  }

  getResponse(): unknown {
    return { value: this.value };
  }
}
