import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";
import { motion } from "../MotionService";

export class McqRenderer extends BaseQuestionRenderer {
    private selected = -1;
    private buttons: HTMLButtonElement[] = [];

    mount(): void {
        const options = (this.config["options"] as string[]) ?? [];
        const wrap = el("div", {
            class: `kahoot-options flex flex-col gap-3 ${options.length <= 2 ? "cols-2" : ""}`,
        });
        options.forEach((opt, i) => {
            const btn = el("button", { class: "min-h-tap", type: "button" }) as HTMLButtonElement;
            btn.append(el("span", { class: "opt-letter" }, [String.fromCharCode(65 + i)]), opt);
            btn.addEventListener("click", () => {
                this.selected = i;
                this.buttons.forEach((b) => b.removeAttribute("data-selected"));
                btn.setAttribute("data-selected", "true");
                motion.pulse(btn);
                this.emit({ selectedIndex: i });
            });
            this.buttons.push(btn);
            wrap.append(btn);
        });
        this.container.append(wrap);
    }

    getResponse(): unknown {
        return { selectedIndex: this.selected };
    }

    reveal(): void {
        const correct = this.config["correctIndex"];
        if (typeof correct !== "number") return;
        this.buttons.forEach((b, i) => {
            b.setAttribute("disabled", "true");
            if (i === correct) {
                b.classList.add("is-correct");
                motion.pulse(b);
            } else if (i === this.selected) {
                motion.shake(b);
            }
        });
    }
}
