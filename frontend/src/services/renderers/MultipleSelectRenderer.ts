import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";
import { answerShape } from "../../design/kahoot";
import { motion } from "../MotionService";

export class MultipleSelectRenderer extends BaseQuestionRenderer {
    private selected: number[] = [];
    private buttons: HTMLButtonElement[] = [];

    mount(): void {
        const options = (this.config["options"] as string[]) ?? [];
        const wrap = el("div", {
            class: `kahoot-options flex flex-col gap-3 ${options.length <= 2 ? "cols-2" : ""}`,
        });
        options.forEach((opt, i) => {
            const btn = el("button", { class: "min-h-tap", type: "button" }) as HTMLButtonElement;
            btn.append(
                el("span", { class: "opt-letter" }, [
                    el("span", { class: `opt-shape shape-${answerShape(i)}` }),
                ]),
                opt,
            );
            btn.addEventListener("click", () => {
                const idx = this.selected.indexOf(i);
                if (idx >= 0) {
                    this.selected.splice(idx, 1);
                    btn.removeAttribute("data-selected");
                } else {
                    this.selected.push(i);
                    btn.setAttribute("data-selected", "true");
                }
                motion.pulse(btn);
                this.emit({ selectedIndices: [...this.selected].sort((a, b) => a - b) });
            });
            this.buttons.push(btn);
            wrap.append(btn);
        });
        this.container.append(wrap);
        const hint = el("p", { class: "text-sm text-default-500 mt-1" }, [
            "Partial scoring applies.",
        ]);
        this.container.append(hint);
    }

    getResponse(): unknown {
        return { selectedIndices: this.selected };
    }

    reveal(): void {
        const correct = this.config["correctIndices"];
        if (!Array.isArray(correct)) return;
        const correctSet = new Set(correct as number[]);
        this.buttons.forEach((b, i) => {
            b.setAttribute("disabled", "true");
            if (correctSet.has(i)) {
                b.classList.add("is-correct");
                motion.pulse(b);
            } else if (this.selected.includes(i)) {
                motion.shake(b);
            }
        });
    }
}
