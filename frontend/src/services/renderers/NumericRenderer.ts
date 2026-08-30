import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";

export class NumericRenderer extends BaseQuestionRenderer {
    private value: number | null = null;

    mount(): void {
        const input = el("input", {
            class: "w-full min-h-tap px-4 py-3 rounded-lg border border-border bg-surface font-mono text-lg",
            type: "number",
            placeholder: "Enter a number",
        });
        input.addEventListener("input", () => {
            const v = parseFloat(input.value);
            this.value = Number.isNaN(v) ? null : v;
            this.emit({ value: this.value });
        });
        this.container.append(input);
        if (this.config["unit"]) {
            this.container.append(
                el("p", { class: "text-sm text-muted mt-1" }, [`Unit: ${this.config["unit"]}`]),
            );
        }
    }

    getResponse(): unknown {
        return { value: this.value };
    }
}
