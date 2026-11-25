import { BaseQuestionRenderer } from "./BaseQuestionRenderer";
import { el } from "./dom";

interface Line {
    id: string;
    text: string;
}

export class DragSortRenderer extends BaseQuestionRenderer {
    private order: string[] = [];

    mount(): void {
        const lines = ((this.config["lines"] as Line[]) ?? []).map((l, i) => ({
            ...l,
            id: l.id ?? String(i),
        }));
        this.order = lines.map((l) => l.id);

        const list = el("div", { class: "flex flex-col gap-2" });
        const move = (from: number, to: number) => {
            const [moved] = this.order.splice(from, 1);
            this.order.splice(to, 0, moved);
            render();
            this.emit({ order: [...this.order] });
        };
        const render = () => {
            list.innerHTML = "";
            this.order.forEach((id, idx) => {
                const line = lines.find((l) => l.id === id)!;
                const row = el("div", {
                    class: "min-h-tap flex items-center gap-3 px-4 py-3 rounded-lg border border-border bg-surface cursor-move",
                    draggable: true,
                });
                const up = el("button", {
                    type: "button",
                    class: "min-h-[44px] px-2 border border-border rounded-lg",
                });
                up.textContent = "Up";
                up.setAttribute("aria-label", `Move ${line.text} up`);
                up.disabled = idx === 0;
                up.addEventListener("click", () => move(idx, idx - 1));
                const down = el("button", {
                    type: "button",
                    class: "min-h-[44px] px-2 border border-border rounded-lg",
                });
                down.textContent = "Down";
                down.setAttribute("aria-label", `Move ${line.text} down`);
                down.disabled = idx === this.order.length - 1;
                down.addEventListener("click", () => move(idx, idx + 1));
                row.append(
                    el("span", { class: "font-mono text-muted" }, [String(idx + 1) + "."]),
                    el("span", {}, [line.text]),
                    up,
                    down,
                );
                row.addEventListener("dragstart", (e) => {
                    row.dataset.drag = String(idx);
                    e.dataTransfer?.setData("text/plain", String(idx));
                });
                row.addEventListener("dragover", (e) => e.preventDefault());
                row.addEventListener("drop", (e) => {
                    e.preventDefault();
                    const from = Number(e.dataTransfer?.getData("text/plain"));
                    const to = idx;
                    if (Number.isNaN(from) || from === to) return;
                    move(from, to);
                });
                list.append(row);
            });
        };
        render();
        this.container.append(list);
        // The untouched initial order is a meaningful answer: emit it so a
        // submit or timer-expiry never sends null.
        this.emit({ order: [...this.order] });
    }

    getResponse(): unknown {
        return { order: this.order };
    }
}
