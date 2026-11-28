import { describe, test, expect, vi, beforeEach } from "vitest";
import { render } from "@testing-library/react";
import type { QuestionDto } from "../types";
import { QuestionRendererHost } from "./QuestionRendererHost";

const factoryMock = vi.hoisted(() => ({ create: vi.fn() }));

vi.mock("../services/QuestionRendererFactory", () => ({
    QuestionRendererFactory: {
        create: (...args: unknown[]) => factoryMock.create(...args),
    },
}));

const question: QuestionDto = {
    id: "q1",
    type: "MCQ",
    title: "Q",
    description: "",
    timeLimitSec: 30,
    pointsBase: 100,
    languagesAllowed: null,
    config: { options: ["a", "b"] },
};

describe("QuestionRendererHost", () => {
    let reveal: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        reveal = vi.fn();
        factoryMock.create.mockReset();
        factoryMock.create.mockImplementation(() => ({
            mount: vi.fn(),
            reveal,
            destroy: vi.fn(),
        }));
    });

    test("an inline callback never remounts the renderer and stays fresh", () => {
        const first = vi.fn();
        const { rerender } = render(
            <QuestionRendererHost question={question} onResponse={first} />,
        );
        const emit = factoryMock.create.mock.calls[0][3] as (r: unknown) => void;
        emit({ selectedIndex: 1 });
        expect(first).toHaveBeenCalledWith({ selectedIndex: 1 });

        const second = vi.fn();
        rerender(<QuestionRendererHost question={question} onResponse={second} />);
        expect(factoryMock.create).toHaveBeenCalledTimes(1);
        emit({ selectedIndex: 2 });
        expect(second).toHaveBeenCalledWith({ selectedIndex: 2 });
        expect(first).toHaveBeenCalledTimes(1);
    });

    test("a changing reveal signal invokes reveal", () => {
        const { rerender } = render(
            <QuestionRendererHost question={question} onResponse={() => {}} revealSignal={0} />,
        );
        expect(reveal).not.toHaveBeenCalled();
        rerender(
            <QuestionRendererHost question={question} onResponse={() => {}} revealSignal={1} />,
        );
        expect(reveal).toHaveBeenCalledTimes(1);
    });
});
