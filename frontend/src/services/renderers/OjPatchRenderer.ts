import { OjBase } from "./OjBase";

export class OjPatchRenderer extends OjBase {
  mount(): void {
    const buggy = (this.config["buggyFunction"] as string) ?? "";
    this.language = (this.config["defaultLanguage"] as string) ?? "python";
    const allowed = (this.config["languagesAllowed"] as string[]) ?? null;
    const note = document.createElement("p");
    note.className = "text-sm text-muted mb-2";
    note.textContent = "Edit only the highlighted lines to fix the bug, then run.";
    this.container.append(note);
    this.mountEditor(buggy, allowed);
  }

  getResponse(): unknown {
    return { source: this.source, language: this.language };
  }
}
