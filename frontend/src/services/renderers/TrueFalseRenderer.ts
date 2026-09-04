import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";
import { motion } from "../MotionService";

export class TrueFalseRenderer extends BaseQuestionRenderer {
    private value: boolean | null = null;
    private buttons: HTMLButtonElement[] = [];

    mount(): void {
        const wrap = el("div", { class: "kahoot-options cols-2 flex gap-3" });
        const mk = (label: string, v: boolean, shape: string) => {
            const btn = el("button", { class: "min-h-tap", type: "button" }) as HTMLButtonElement;
            btn.append(el("span", { class: "opt-letter" }, [el("span", { class: `opt-shape shape-${shape}` })]), label);
            btn.addEventListener("click", () => {
                this.value = v;
                this.buttons.forEach((b) => b.removeAttribute("data-selected"));
                btn.setAttribute("data-selected", "true");
                motion.pulse(btn);
                this.emit({ value: v });
            });
            this.buttons.push(btn);
            return btn;
        };
        wrap.append(mk("True", true, "triangle"), mk("False", false, "diamond"));
        this.container.append(wrap);
    }

    getResponse(): unknown {
        return { value: this.value };
    }

    reveal(): void {
        const correct = this.config["correct"];
        if (typeof correct !== "boolean") return;
        this.buttons.forEach((b, i) => {
            b.setAttribute("disabled", "true");
            const isCorrect = i === 0 ? correct : !correct;
            if (isCorrect) {
                b.classList.add("is-correct");
                motion.pulse(b);
            } else if (this.value !== null && (i === 0) === this.value) {
                motion.shake(b);
            }
        });
    }
}
