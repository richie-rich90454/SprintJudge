import { useEffect, useRef } from "react";
import { QuestionRendererFactory } from "../services/QuestionRendererFactory";
import { BaseQuestionRenderer } from "../services/renderers/BaseQuestionRenderer";
import { QuestionDto } from "../types";

interface Props {
  question: QuestionDto;
  onResponse: (response: unknown) => void;
}

/**
 * Bridges the imperative OOP renderers into React. Mounts the correct
 * renderer for the question type into a div and forwards answers upward.
 */
export function QuestionRendererHost({ question, onResponse }: Props) {
  const hostRef = useRef<HTMLDivElement>(null);
  const rendererRef = useRef<BaseQuestionRenderer | null>(null);

  useEffect(() => {
    if (!hostRef.current) return;
    const renderer = QuestionRendererFactory.create(
      question.type,
      hostRef.current,
      question.config,
      (response) => onResponse(response),
      question.id,
      question.languagesAllowed
    );
    renderer.mount();
    rendererRef.current = renderer;
    return () => renderer.destroy();
    // Key on id too: consecutive questions of the same type must not reuse
    // the previous question's mounted DOM.
  }, [question.type, question.id]);

  return <div ref={hostRef} className="renderer-host mt-4" />;
}
