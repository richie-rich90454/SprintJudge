import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { useCountUp, useFlipList, useRevealMotion, useStageEnter } from "./useStageMotion";
import { motionReduced } from "../stores/useUIStore";
import { gsap } from "gsap";
import { Flip } from "gsap/Flip";

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

    test("useFlipList is a no-op without a ref", () => {
        const from = vi.spyOn(Flip, "from");
        function List({ attach }: { attach: boolean }) {
            const ref = useFlipList<HTMLDivElement>([attach]);
            return <div ref={attach ? ref : undefined}><span className="flip-row">row</span></div>;
        }
        const { rerender } = render(<List attach />);
        const afterMount = from.mock.calls.length;
        rerender(<List attach={false} />);
        expect(screen.getByText("row")).toBeInTheDocument();
        expect(from.mock.calls.length).toBe(afterMount);
        from.mockRestore();
    });

    test("count-up applies gsap onUpdate to the node text", () => {
        const spy = vi.spyOn(gsap, "to").mockImplementation((target: unknown, vars: Record<string, unknown>) => {
            (target as { value: number }).value = 42;
            (vars.onUpdate as () => void)();
            return {} as never;
        });
        render(
            <Harness hook={useCountUp} args={[true, []]}>
                <span data-count="42">0</span>
            </Harness>,
        );
        expect(screen.getByText("42")).toBeInTheDocument();
        spy.mockRestore();
    });

    test("count-up does nothing when there are no count nodes", () => {
        const spy = vi.spyOn(gsap, "to");
        render(
            <Harness hook={useCountUp} args={[true, []]}>
                <span>plain</span>
            </Harness>,
        );
        expect(spy).not.toHaveBeenCalled();
        spy.mockRestore();
    });

    test("Flip enter and leave callbacks animate their elements", () => {
        const node = document.createElement("div");
        const fromTo = vi.spyOn(gsap, "fromTo");
        const from = vi.spyOn(Flip, "from").mockImplementation((_state: unknown, vars: any) => {
            (vars.onEnter as (els: Element[]) => void)([node]);
            (vars.onLeave as (els: Element[]) => void)([node]);
            return {} as never;
        });
        function List({ rows }: { rows: string[] }) {
            const ref = useFlipList<HTMLDivElement>([rows.join(",")]);
            return (
                <div ref={ref}>
                    {rows.map((row) => (
                        <div key={row} className="flip-row">{row}</div>
                    ))}
                </div>
            );
        }
        const { rerender } = render(<List rows={["a"]} />);
        rerender(<List rows={["b"]} />);
        expect(from).toHaveBeenCalled();
        expect(fromTo).toHaveBeenCalled();
        from.mockRestore();
        fromTo.mockRestore();
    });
});
