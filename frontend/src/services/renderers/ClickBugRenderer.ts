import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";

export class ClickBugRenderer extends BaseQuestionRenderer {
    private selected = -1;

    mount(): void {
        const lines = (this.config["codeLines"] as string[]) ?? [];
        const wrap = el("div", {
            class: "mono text-sm bg-surface-alt border border-border rounded-lg overflow-hidden",
        });
        lines.forEach((text, i) => {
            const row = el("div", {
                class: "flex px-3 py-1 hover:bg-surface cursor-pointer border-l-2 border-transparent",
            });
            row.append(
                el("span", { class: "w-8 text-right text-muted select-none mr-3" }, [
                    String(i + 1),
                ]),
                el("span", {}, [text]),
            );
            row.addEventListener("click", () => {
                this.selected = i;
                wrap.querySelectorAll("div").forEach((d) =>
                    d.classList.remove("border-primary", "bg-surface"),
                );
                row.classList.add("border-primary", "bg-surface");
                this.emit({ line: i });
            });
            wrap.append(row);
        });
        this.container.append(wrap);
    }

    getResponse(): unknown {
        return { line: this.selected };
    }
}
