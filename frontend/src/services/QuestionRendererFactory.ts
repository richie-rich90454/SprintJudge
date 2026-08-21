import { BaseQuestionRenderer, ResponseChange } from "./renderers/BaseQuestionRenderer";
import { McqRenderer } from "./renderers/McqRenderer";
import { TrueFalseRenderer } from "./renderers/TrueFalseRenderer";
import { MultipleSelectRenderer } from "./renderers/MultipleSelectRenderer";
import { NumericRenderer } from "./renderers/NumericRenderer";
import { OutputPredRenderer } from "./renderers/OutputPredRenderer";
import { FillBlankRenderer } from "./renderers/FillBlankRenderer";
import { DragSortRenderer } from "./renderers/DragSortRenderer";
import { ClickBugRenderer } from "./renderers/ClickBugRenderer";
import { CodeCompletionRenderer } from "./renderers/CodeCompletionRenderer";
import { ComplexityRenderer } from "./renderers/ComplexityRenderer";
import { OjFullRenderer } from "./renderers/OjFullRenderer";
import { OjPatchRenderer } from "./renderers/OjPatchRenderer";
import { QuestionType } from "../types";

type Ctor = new (container: HTMLElement, config: unknown, onChange: ResponseChange, questionId?: string) => BaseQuestionRenderer;

/**
 * Static factory mapping a question type to its renderer. Add a new case to
 * support a 13th format — the single expansion point for the question engine.
 */
export class QuestionRendererFactory {
  private static readonly REGISTRY: Record<QuestionType, Ctor> = {
    MCQ: McqRenderer,
    TRUE_FALSE: TrueFalseRenderer,
    MULTIPLE_SELECT: MultipleSelectRenderer,
    NUMERIC: NumericRenderer,
    OUTPUT_PRED: OutputPredRenderer,
    FILL_BLANK: FillBlankRenderer,
    DRAG_SORT: DragSortRenderer,
    CLICK_BUG: ClickBugRenderer,
    CODE_COMPLETION: CodeCompletionRenderer,
    COMPLEXITY: ComplexityRenderer,
    OJ_FULL: OjFullRenderer,
    OJ_PATCH: OjPatchRenderer,
  };

  static create(type: QuestionType, container: HTMLElement, config: unknown, onChange: ResponseChange, questionId?: string): BaseQuestionRenderer {
    const C = this.REGISTRY[type];
    if (!C) throw new Error("No renderer for type " + type);
    return new C(container, config, onChange, questionId);
  }

  static supported(): QuestionType[] {
    return Object.keys(this.REGISTRY) as QuestionType[];
  }
}
