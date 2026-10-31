import { useEffect, useLayoutEffect, useRef } from "react";
import { gsap } from "gsap";
import { Flip } from "gsap/Flip";
import { motionReduced } from "../stores/useUIStore";

gsap.registerPlugin(Flip);

const EASE_OUT = "power3.out";
const EASE_IN_OUT = "power3.inOut";

/** Stage entrance: panel lift plus a short stagger of marked children. */
export function useStageEnter<T extends HTMLElement>(deps: unknown[] = []) {
    const ref = useRef<T>(null);
    useEffect(() => {
        const el = ref.current;
        if (!el || motionReduced()) return;
        const ctx = gsap.context(() => {
            gsap.fromTo(
                el,
                { y: 18, opacity: 0 },
                { y: 0, opacity: 1, duration: 0.46, ease: EASE_OUT, clearProps: "transform,opacity" },
            );
            const children = el.querySelectorAll<HTMLElement>(".stage-enter");
            if (children.length) {
                gsap.fromTo(
                    children,
                    { y: 12, opacity: 0 },
                    {
                        y: 0,
                        opacity: 1,
                        duration: 0.4,
                        stagger: 0.06,
                        delay: 0.08,
                        ease: EASE_OUT,
                        clearProps: "transform,opacity",
                    },
                );
            }
        }, el);
        return () => ctx.revert();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, deps);
    return ref;
}

/** Reveal: distribution bars grow, correct plaque settles, explanation fades. */
export function useRevealMotion<T extends HTMLElement>(active: boolean, deps: unknown[] = []) {
    const ref = useRef<T>(null);
    useEffect(() => {
        const el = ref.current;
        if (!el || !active || motionReduced()) return;
        const ctx = gsap.context(() => {
            gsap.fromTo(
                ".reveal-bar",
                { scaleX: 0 },
                {
                    scaleX: 1,
                    transformOrigin: "left center",
                    duration: 0.62,
                    stagger: 0.07,
                    ease: EASE_OUT,
                },
            );
            gsap.fromTo(
                ".reveal-card",
                { y: 14, opacity: 0 },
                {
                    y: 0,
                    opacity: 1,
                    duration: 0.52,
                    stagger: 0.06,
                    ease: EASE_OUT,
                    clearProps: "transform,opacity",
                },
            );
            gsap.fromTo(
                ".reveal-correct",
                { scale: 0.985 },
                { scale: 1, duration: 0.5, ease: EASE_IN_OUT, clearProps: "transform" },
            );
        }, el);
        return () => ctx.revert();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [active, ...deps]);
    return ref;
}

/** Leaderboard reorder using GSAP Flip. */
export function useFlipList<T extends HTMLElement>(deps: unknown[] = []) {
    const ref = useRef<T>(null);
    const stateRef = useRef<ReturnType<typeof Flip.getState> | null>(null);

    useLayoutEffect(() => {
        const el = ref.current;
        if (!el) return;
        stateRef.current = Flip.getState(el.querySelectorAll(".flip-row"));
    });

    useEffect(() => {
        const el = ref.current;
        const state = stateRef.current;
        if (!el || !state || motionReduced()) return;
        const ctx = gsap.context(() => {
            Flip.from(state, {
                duration: 0.5,
                ease: EASE_OUT,
                absolute: true,
                stagger: 0.015,
                onEnter: (els) =>
                    gsap.fromTo(
                        els,
                        { opacity: 0, y: 8 },
                        { opacity: 1, y: 0, duration: 0.32, ease: EASE_OUT },
                    ),
                onLeave: (els) =>
                    gsap.to(els, { opacity: 0, scale: 0.98, duration: 0.24, ease: EASE_OUT }),
            });
        }, el);
        return () => ctx.revert();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, deps);
    return ref;
}

/** Count numeric score nodes up to their data-count value. */
export function useCountUp<T extends HTMLElement>(active = true, deps: unknown[] = []) {
    const ref = useRef<T>(null);
    useEffect(() => {
        const el = ref.current;
        if (!el || !active || motionReduced()) return;
        const nodes = Array.from(el.querySelectorAll<HTMLElement>("[data-count]"));
        if (!nodes.length) return;
        const ctx = gsap.context(() => {
            nodes.forEach((node) => {
                const target = Number(node.dataset.count ?? "0");
                if (!Number.isFinite(target)) return;
                const obj = { value: 0 };
                gsap.to(obj, {
                    value: target,
                    duration: 0.8,
                    ease: EASE_OUT,
                    onUpdate: () => {
                        node.textContent = Math.round(obj.value).toString();
                    },
                });
            });
        }, el);
        return () => ctx.revert();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [active, ...deps]);
    return ref;
}
