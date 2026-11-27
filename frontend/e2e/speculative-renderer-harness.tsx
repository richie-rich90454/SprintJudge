import { useState } from "react";
import { createRoot } from "react-dom/client";
import { QuestionRendererHost } from "../src/components/QuestionRendererHost";
import type { QuestionDto } from "../src/types";

const question: QuestionDto = {
    id: "speculative-fill-blank",
    type: "FILL_BLANK",
    title: "Speculative",
    description: "",
    timeLimitSec: 60,
    pointsBase: 100,
    languagesAllowed: null,
    config: { snippet: "int x = __;" },
};

interface Probe {
    __oqResponse?: unknown;
    __oqGeneration?: number;
}

function Harness() {
    const [generation, setGeneration] = useState(0);
    // Closes over state, so every parent render passes a genuinely fresh
    // callback identity - the condition that used to remount the renderer.
    const onResponse = (response: unknown) => {
        const probe = window as unknown as Probe;
        probe.__oqResponse = response;
        probe.__oqGeneration = generation;
    };
    return (
        <div>
            <button
                type="button"
                data-testid="rerender"
                onClick={() => setGeneration((g) => g + 1)}
            >
                Force re-render
            </button>
            <p data-testid="generation">{generation}</p>
            <QuestionRendererHost question={question} onResponse={onResponse} />
        </div>
    );
}

createRoot(document.getElementById("root")!).render(<Harness />);
