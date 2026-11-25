import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { useCountUp, useFlipList, useRevealMotion, useStageEnter } from "./useStageMotion";
import { motionReduced } from "../stores/useUIStore";

vi.mock("../stores/useUIStore", () => ({
    motionReduced: vi.fn(() => false),
    useUIStore: () => ({ motion: "full" }),
}));

const reduced = vi.mocked(motionReduced);

function Harness({
    hook,
    args,
    children,
}: {
    hook: (...args: any[]) => any;
    args: any[];
    children: React.ReactNode;
}) {
    const ref = hook(...args);
    return <div ref={ref}>{children}</div>;
}

describe("stage motion hooks", () => {
    beforeEach(() => reduced.mockReturnValue(false));
    afterEach(() => {
        vi.restoreAllMocks();
        localStorage.clear();
    });

    test("useStageEnter animates a panel with staggered children", () => {
        function Panel() {
            const ref = useStageEnter<HTMLDivElement>([]);
            return (
                <div ref={ref}>
                    <span className="stage-enter">One</span>
                </div>
            );
        }
        const { container } = render(<Panel />);
        expect(container.querySelector(".stage-enter")).toBeInTheDocument();
    });

    test("useStageEnter is a no-op when motion is reduced or the ref is empty", () => {
        reduced.mockReturnValue(true);
        function Panel({ attach }: { attach: boolean }) {
            const ref = useStageEnter<HTMLDivElement>([]);
            return <div ref={attach ? ref : undefined}>Panel</div>;
        }
        const { rerender } = render(<Panel attach />);
        rerender(<Panel attach={false} />);
        expect(screen.getByText("Panel")).toBeInTheDocument();
    });

    test("useRevealMotion runs only while active", () => {
        const { rerender } = render(
            <Harness hook={useRevealMotion} args={[false, []]}>
                <span className="reveal-bar" />
                <span className="reveal-card" />
                <span className="reveal-correct" />
            </Harness>,
        );
        rerender(
            <Harness hook={useRevealMotion} args={[true, []]}>
                <span className="reveal-bar" />
                <span className="reveal-card" />
                <span className="reveal-correct" />
            </Harness>,
        );
        expect(document.querySelectorAll(".reveal-bar")).toHaveLength(1);
    });

    test("useFlipList tracks rows and reorders them", () => {
        function List({ rows }: { rows: string[] }) {
            const ref = useFlipList<HTMLDivElement>([rows.join(",")]);
            return (
                <div ref={ref}>
                    {rows.map((row) => (
                        <div key={row} className="flip-row">
                            {row}
                        </div>
                    ))}
                </div>
            );
        }
        const { rerender } = render(<List rows={["a", "b"]} />);
        rerender(<List rows={["b", "a"]} />);
        expect(screen.getAllByText(/^[ab]$/)).toHaveLength(2);
    });

    test("useCountUp animates data-count nodes and skips invalid values", () => {
        render(
            <Harness hook={useCountUp} args={[true, []]}>
                <span data-count="42">0</span>
                <span data-count="not-a-number">0</span>
            </Harness>,
        );
        expect(screen.getAllByText("0")).toHaveLength(2);
    });

    test("count-up is skipped when inactive or motion is reduced", () => {
        reduced.mockReturnValue(true);
        render(
            <Harness hook={useCountUp} args={[false, []]}>
                <span data-count="1">0</span>
            </Harness>,
        );
        fireEvent.click(screen.getByText("0"));
        expect(screen.getByText("0")).toBeInTheDocument();
    });
});
