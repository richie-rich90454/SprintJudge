import { OjBase } from "./OjBase";

export class OjFullRenderer extends OjBase {
  mount(): void {
    const initial = (this.config["starter"] as string) ?? "";
    this.language = (this.config["defaultLanguage"] as string) ?? "python";
    const allowed = (this.config["languagesAllowed"] as string[]) ?? null;
    this.mountEditor(initial, allowed);
  }

  getResponse(): unknown {
    return { source: this.source, language: this.language };
  }
}
