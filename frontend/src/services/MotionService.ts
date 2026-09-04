import { animate, type AnimationPlaybackControls, type Easing } from "framer-motion";

export type MotionPreset = "card" | "page" | "modal" | "bar" | "pin" | "podium" | "ticker";

const prefersReducedMotion = (): boolean => {
    try {
        return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    } catch {
        return false;
    }
};

const registry = new WeakMap<Element, AnimationPlaybackControls[]>();

function track(el: Element, controls: AnimationPlaybackControls): void {
    const list = registry.get(el) ?? [];
    list.push(controls);
    registry.set(el, list);
}

function clearInline(el: Element): void {
    const node = el as HTMLElement;
    node.style.removeProperty("transform");
    node.style.removeProperty("opacity");
}

/**
 * Central motion service. All animation is transform/opacity/scale only and is
 * driven by framer-motion. Motion is skipped entirely when the user prefers
 * reduced motion. Public method signatures are unchanged so views keep working.
 */
export class MotionService {
    private reduced: boolean;

    constructor() {
        this.reduced = prefersReducedMotion();
    }

    setReduced(value: boolean): void {
        this.reduced = value;
    }

    enter(el: HTMLElement | null, preset: MotionPreset): void {
        if (!el || this.reduced) return;
        const map: Record<
            MotionPreset,
            { keyframes: Record<string, number[]>; duration: number; ease: Easing }
        > = {
            card: { keyframes: { y: [24, 0], opacity: [0, 1] }, duration: 0.5, ease: "easeOut" },
            page: { keyframes: { y: [12, 0], opacity: [0, 1] }, duration: 0.4, ease: "easeOut" },
            modal: { keyframes: { y: [32, 0], opacity: [0, 1] }, duration: 0.45, ease: "easeOut" },
            bar: { keyframes: { scaleX: [0, 1] }, duration: 0.6, ease: "easeInOut" },
            pin: { keyframes: { y: [-14, 0], opacity: [0, 1] }, duration: 0.5, ease: "backOut" },
            podium: {
                keyframes: { yPercent: [-60, 0], opacity: [0, 1] },
                duration: 0.7,
                ease: "backOut",
            },
            ticker: { keyframes: { x: [-28, 0], opacity: [0, 1] }, duration: 0.4, ease: "easeOut" },
        };
        const cfg = map[preset];
        const controls = animate(el, cfg.keyframes, {
            duration: cfg.duration,
            ease: cfg.ease,
            onComplete: () => {
                if (preset === "bar") (el as HTMLElement).style.removeProperty("opacity");
                else clearInline(el);
            },
        });
        track(el, controls);
    }

    staggerIn(container: HTMLElement | null, selector: string, offset = 0.05): void {
        if (!container || this.reduced) return;
        const targets = Array.from(container.querySelectorAll<HTMLElement>(selector));
        if (!targets.length) return;
        targets.forEach((node, i) => {
            const controls = animate(
                node,
                { x: [-14, 0], opacity: [0, 1] },
                {
                    duration: 0.35,
                    delay: i * offset,
                    ease: "easeOut",
                    onComplete: () => clearInline(node),
                },
            );
            track(node, controls);
        });
    }

    pulse(el: HTMLElement | null): void {
        if (!el || this.reduced) return;
        const controls = animate(
            el,
            { scale: [1, 1.08, 1] },
            { duration: 0.36, ease: "easeOut", onComplete: () => clearInline(el) },
        );
        track(el, controls);
    }

    shake(el: HTMLElement | null): void {
        if (!el || this.reduced) return;
        const controls = animate(
            el,
            { x: [0, 7, -7, 7, -7, 4, -4, 0] },
            { duration: 0.42, ease: "easeInOut", onComplete: () => clearInline(el) },
        );
        track(el, controls);
    }

    countUp(container: HTMLElement | null, selector = "[data-score]"): void {
        if (!container || this.reduced) return;
        container.querySelectorAll<HTMLElement>(selector).forEach((el) => {
            const target = parseInt(el.dataset["score"] ?? "0", 10);
            const controls = animate(0, target, {
                duration: 0.9,
                ease: "easeOut",
                onUpdate: (v) => {
                    el.textContent = String(Math.round(v));
                },
            });
            track(el, controls);
        });
    }

    killFor(el: HTMLElement | null): void {
        if (!el) return;
        const all = [el, ...Array.from(el.querySelectorAll<HTMLElement>("*"))];
        all.forEach((node) => {
            const list = registry.get(node);
            if (list) {
                list.forEach((c) => c.stop());
                registry.delete(node);
            }
        });
    }
}

export const motion = new MotionService();
