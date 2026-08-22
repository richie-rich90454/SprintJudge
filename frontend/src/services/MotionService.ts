import gsap from "gsap";

export type MotionPreset = "card" | "page" | "modal" | "bar" | "pin" | "podium" | "ticker";

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
      case "podium":
        // Results podium: ranks drop in from above with a confident bounce.
        gsap.fromTo(el.children,
          { yPercent: -60, opacity: 0 },
          { yPercent: 0, opacity: 1, duration: 0.7, stagger: 0.12, ease: "back.out(1.5)",
            clearProps: "transform,opacity" });
        break;
      case "ticker":
        gsap.fromTo(el.children,
          { x: -28, opacity: 0 },
          { x: 0, opacity: 1, duration: 0.4, stagger: 0.06, ease: "power3.out",
            clearProps: "transform,opacity" });
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

  /**
   * Kills every tween touching an element subtree. Views call this on unmount
   * so swapped-out animations can never leak into the next screen.
   */
  killFor(el: HTMLElement | null): void {
    if (!el) return;
    gsap.killTweensOf(el);
    el.querySelectorAll<HTMLElement>("*").forEach((n) => gsap.killTweensOf(n));
  }

  /**
   * One delegated listener gives every button a genuine tactile press
   * (transform-only, respects reduced-motion). Installed once from main.tsx.
   */
  installGlobalPressFeedback(): void {
    if (this.reduced || this.pressInstalled) return;
    this.pressInstalled = true;
    document.addEventListener(
      "pointerdown",
      (e) => {
        const target = (e.target as HTMLElement | null)?.closest("button");
        if (!target) return;
        gsap.to(target, { scale: 0.965, duration: 0.08, ease: "power1.out", overwrite: "auto" });
        const release = () => {
          gsap.to(target, { scale: 1, duration: 0.14, ease: "back.out(3)", overwrite: "auto" });
          window.removeEventListener("pointerup", release);
          window.removeEventListener("pointercancel", release);
        };
        window.addEventListener("pointerup", release, { once: true });
        window.addEventListener("pointercancel", release, { once: true });
      },
      { passive: true }
    );
  }

  private pressInstalled = false;
}

export const motion = new MotionService();
