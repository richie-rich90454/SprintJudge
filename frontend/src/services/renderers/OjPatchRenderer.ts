import { OjBase } from "./OjBase";
import { el } from "./dom";

export class OjPatchRenderer extends OjBase {
  mount(): void {
    const buggy = (this.config["buggyFunction"] as string) ?? "";
    this.language = (this.config["defaultLanguage"] as string) ?? "python";
    const note = el("p", { class: "text-sm text-muted mb-2" },
      ["Edit only the highlighted lines to fix the bug, then run."]);
    this.container.append(note);
    this.mountEditor(buggy);
  }

  getResponse(): unknown {
    return { source: this.source, language: this.language };
  }
}
