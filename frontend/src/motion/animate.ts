import { gsap } from "gsap";

export type AnimationPlaybackControls = { stop: () => void };

const EASE_MAP: Record<string, string> = {
    easeOut: "power2.out",
    easeInOut: "power2.inOut",
    backOut: "back.out(1.7)",
};

function mapEase(ease: unknown): any {
    if (typeof ease === "string" && ease in EASE_MAP) return EASE_MAP[ease];
    return ease;
}

function clearInline(el: Element): void {
    const node = el as HTMLElement;
    node.style.removeProperty("transform");
    node.style.removeProperty("opacity");
}

export function animate(target: any, keyframes: any, opts: any = {}): AnimationPlaybackControls {
    const ease = mapEase(opts?.ease);
    const duration = opts?.duration;
    const delay = opts?.delay;
    const onCompleteUser = opts?.onComplete as (() => void) | undefined;
    const onUpdateUser = opts?.onUpdate as ((v?: any) => void) | undefined;

    if (typeof target === "number") {
        const toValue = typeof keyframes === "number" ? keyframes : 0;
        const obj = { value: target };
        const tween = gsap.to(obj, {
            value: toValue,
            duration,
            delay,
            ease,
            onUpdate: () => onUpdateUser?.(obj.value),
            onComplete: () => onCompleteUser?.(),
        });
        return { stop: () => tween.kill() };
    }

    const el = target;
    const kf = (keyframes ?? {}) as Record<string, any>;
    const arrays = Object.values(kf);
    const isMulti = arrays.some((a) => Array.isArray(a) && a.length > 2);

    const finish = () => {
        onCompleteUser?.();
        if (el) clearInline(el);
    };

    if (isMulti) {
        const len = Math.max(
            1,
            ...arrays.map((a) => (Array.isArray(a) ? a.length : 1)),
        );
        const steps: Record<string, number>[] = [];
        for (let i = 0; i < len; i++) {
            const step: Record<string, number> = {};
            for (const key of Object.keys(kf)) {
                const arr = kf[key];
                if (Array.isArray(arr)) step[key] = arr[Math.min(i, arr.length - 1)];
                else step[key] = arr;
            }
            steps.push(step);
        }
        const tween = gsap.to(el, {
            keyframes: steps,
            duration,
            delay,
            ease,
            onUpdate: onUpdateUser ? () => onUpdateUser() : undefined,
            onComplete: finish,
        });
        return { stop: () => tween.kill() };
    }

    const from: Record<string, any> = {};
    const to: Record<string, any> = {};
    for (const key of Object.keys(kf)) {
        const arr = kf[key];
        if (Array.isArray(arr)) {
            from[key] = arr[0];
            to[key] = arr.length > 1 ? arr[1] : arr[0];
        } else {
            from[key] = arr;
            to[key] = arr;
        }
    }
    to.duration = duration;
    to.delay = delay;
    to.ease = ease;
    to.onComplete = finish;
    if (onUpdateUser) to.onUpdate = () => onUpdateUser();
    const tween = gsap.fromTo(el, from, to);
    return { stop: () => tween.kill() };
}