import { OjBase } from "./OjBase";

export class OjFullRenderer extends OjBase {
  mount(): void {
    const starter = (this.config["starter"] as string) ?? "";
    this.language = (this.config["defaultLanguage"] as string) ?? "python";
    this.mountEditor(starter);
  }

  getResponse(): unknown {
    return { source: this.source, language: this.language };
  }
}
