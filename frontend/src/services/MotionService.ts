import gsap from "gsap";

export type MotionPreset = "card" | "page" | "modal" | "bar" | "pin";

const prefersReducedMotion = (): boolean => {
  try {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  } catch {
    return false;
  }
};

/**
 * Central OOP motion service. Every animation is transform/opacity/color only —
 * the flat blueprint aesthetic never gains glow, blur or gradients.
 * All motion is skipped entirely when the user prefers reduced motion.
 */
export class MotionService {
  private reduced: boolean;

  constructor() {
    this.reduced = prefersReducedMotion();
  }

  setReduced(value: boolean): void {
    this.reduced = value;
  }

  /** Entrance preset for a single container element. */
  enter(el: HTMLElement | null, preset: MotionPreset): void {
    if (!el || this.reduced) return;
    switch (preset) {
      case "card":
        gsap.fromTo(el,
          { y: 24, opacity: 0 },
          { y: 0, opacity: 1, duration: 0.5, ease: "power3.out", clearProps: "transform,opacity" });
        break;
      case "page":
        gsap.fromTo(el,
          { y: 12, opacity: 0 },
          { y: 0, opacity: 1, duration: 0.4, ease: "power2.out", clearProps: "transform,opacity" });
        break;
      case "modal":
        gsap.fromTo(el,
          { y: 32, opacity: 0 },
          { y: 0, opacity: 1, duration: 0.45, ease: "power4.out", clearProps: "transform,opacity" });
        break;
      case "bar":
        gsap.fromTo(el,
          { scaleX: 0 },
          { scaleX: 1, duration: 0.6, ease: "power4.inOut", transformOrigin: "left center", clearProps: "transform" });
        break;
      case "pin":
        gsap.fromTo(el.children,
          { y: -14, opacity: 0 },
          { y: 0, opacity: 1, duration: 0.5, stagger: 0.06, ease: "back.out(2)", clearProps: "transform,opacity" });
        break;
    }
  }

  /** Staggered entrance for a child selector inside a container. */
  staggerIn(container: HTMLElement | null, selector: string, offset = 0.05): void {
    if (!container || this.reduced) return;
    const targets = container.querySelectorAll(selector);
    if (!targets.length) return;
    gsap.fromTo(targets,
      { x: -14, opacity: 0 },
      { x: 0, opacity: 1, duration: 0.35, stagger: offset, ease: "power2.out", clearProps: "transform,opacity" });
  }

  /** Short emphasis pulse (timer urgency, join badge). Border-color only. */
  pulse(el: HTMLElement | null): void {
    if (!el || this.reduced) return;
    gsap.fromTo(el,
      { scale: 1 },
      { scale: 1.08, duration: 0.18, yoyo: true, repeat: 1, ease: "power2.out", clearProps: "transform" });
  }

  /** Horizontal shake for rejected input. */
  shake(el: HTMLElement | null): void {
    if (!el || this.reduced) return;
    gsap.fromTo(el,
      { x: 0 },
      { x: 8, duration: 0.07, repeat: 5, yoyo: true, ease: "power1.inOut", clearProps: "transform" });
  }

  /** Count-up on numeric elements carrying a data-score attribute. */
  countUp(container: HTMLElement | null, selector = "[data-score]"): void {
    if (!container || this.reduced) return;
    container.querySelectorAll(selector).forEach((node) => {
      const el = node as HTMLElement;
      const target = parseInt(el.dataset["score"] ?? "0", 10);
      const state = { v: 0 };
      gsap.to(state, {
        v: target,
        duration: 0.9,
        ease: "power2.out",
        onUpdate: () => { el.textContent = String(Math.round(state.v)); },
      });
    });
  }
}

export const motion = new MotionService();
