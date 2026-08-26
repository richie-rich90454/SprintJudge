export type ResponseChange = (response: unknown) => void;

/**
 * Abstract base for all 12 client-side question renderers.
 * A renderer mounts imperative DOM into a host element and reports the
 * player's answer via the onChange callback. Keeps React views thin.
 */
export abstract class BaseQuestionRenderer {
  protected container: HTMLElement;
  protected onChange: ResponseChange;
  protected config: Record<string, unknown>;
  protected questionId: string | undefined;
  /** Question-level language restriction (null/empty = all languages). */
  protected allowedLanguages: string[] | null;

  constructor(container: HTMLElement, config: unknown, onChange: ResponseChange, questionId?: string,
              allowedLanguages?: string[] | null) {
    this.container = container;
    this.config = (config as Record<string, unknown>) ?? {};
    this.onChange = onChange;
    this.questionId = questionId;
    this.allowedLanguages = allowedLanguages && allowedLanguages.length ? allowedLanguages : null;
  }

  abstract mount(): void;
  abstract getResponse(): unknown;

  /** Subclasses call this whenever the answer changes. */
  protected emit(response: unknown): void {
    this.onChange(response);
  }

  destroy(): void {
    this.container.innerHTML = "";
  }
}
