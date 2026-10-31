import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { gsap } from "gsap";
import { animate } from "./animate";

describe("animate adapter", () => {
    let fromToSpy: ReturnType<typeof vi.spyOn>;
    let toSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        fromToSpy = vi.spyOn(gsap, "fromTo").mockReturnValue({ kill: vi.fn() } as any);
        toSpy = vi.spyOn(gsap, "to").mockReturnValue({ kill: vi.fn() } as any);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    test("number target tweens an object and reports updates", () => {
        const onUpdate = vi.fn();
        const onComplete = vi.fn();
        const ctrl = animate(0, 42, { duration: 0.9, ease: "easeOut", onUpdate, onComplete });
        expect(toSpy).toHaveBeenCalled();
        const [obj, vars] = toSpy.mock.calls[0] as [any, any];
        expect(obj).toEqual({ value: 0 });
        expect(vars.value).toBe(42);
        expect(vars.ease).toBe("power2.out");
        vars.onUpdate();
        expect(onUpdate).toHaveBeenCalledWith(0);
        vars.onComplete();
        expect(onComplete).toHaveBeenCalled();
        expect(typeof ctrl.stop).toBe("function");
        ctrl.stop();
        expect((toSpy.mock.results[0].value as any).kill).toHaveBeenCalled();
    });

    test("number target with non-number keyframes defaults to 0", () => {
        animate(5, "x" as any, {});
        const [, vars] = toSpy.mock.calls[toSpy.mock.calls.length - 1] as [any, any];
        expect(vars.value).toBe(0);
    });

    test("number target passes through unknown ease", () => {
        animate(0, 1, { ease: "linear" });
        const [, vars] = toSpy.mock.calls[toSpy.mock.calls.length - 1] as [any, any];
        expect(vars.ease).toBe("linear");
    });

    test("number target passes delay", () => {
        animate(0, 1, { delay: 0.3 });
        const [, vars] = toSpy.mock.calls[toSpy.mock.calls.length - 1] as [any, any];
        expect(vars.delay).toBe(0.3);
    });

    test("element with 2-value arrays uses fromTo", () => {
        const el = document.createElement("div");
        el.style.transform = "translateY(24px)";
        el.style.opacity = "0";
        const onComplete = vi.fn();
        const ctrl = animate(el, { y: [24, 0], opacity: [0, 1] }, { duration: 0.5, ease: "easeOut", onComplete });
        expect(fromToSpy).toHaveBeenCalled();
        const [target, from, to] = fromToSpy.mock.calls[fromToSpy.mock.calls.length - 1] as [any, any, any];
        expect(target).toBe(el);
        expect(from).toEqual({ y: 24, opacity: 0 });
        expect(to.y).toBe(0);
        expect(to.opacity).toBe(1);
        expect(to.duration).toBe(0.5);
        expect(to.ease).toBe("power2.out");
        to.onComplete();
        expect(onComplete).toHaveBeenCalled();
        expect(el.style.transform).toBe("");
        expect(el.style.opacity).toBe("");
        expect(typeof ctrl.stop).toBe("function");
    });

    test("element fromTo maps easeInOut and backOut", () => {
        const el = document.createElement("div");
        animate(el, { scaleX: [0, 1] }, { ease: "easeInOut" });
        let to = fromToSpy.mock.calls[fromToSpy.mock.calls.length - 1][2] as any;
        expect(to.ease).toBe("power2.inOut");

        animate(el, { y: [-14, 0] }, { ease: "backOut" });
        to = fromToSpy.mock.calls[fromToSpy.mock.calls.length - 1][2] as any;
        expect(to.ease).toBe("back.out(1.7)");
    });

    test("element fromTo supports yPercent and single values", () => {
        const el = document.createElement("div");
        animate(el, { yPercent: [-60, 0], foo: 5 }, { duration: 0.7 });
        const [, from, to] = fromToSpy.mock.calls[fromToSpy.mock.calls.length - 1] as [any, any, any];
        expect(from.yPercent).toBe(-60);
        expect(to.yPercent).toBe(0);
        expect(from.foo).toBe(5);
        expect(to.foo).toBe(5);
    });

    test("element fromTo invokes onUpdate when provided", () => {
        const el = document.createElement("div");
        const onUpdate = vi.fn();
        animate(el, { x: [-28, 0] }, { onUpdate });
        const to = fromToSpy.mock.calls[fromToSpy.mock.calls.length - 1][2] as any;
        expect(typeof to.onUpdate).toBe("function");
        to.onUpdate();
        expect(onUpdate).toHaveBeenCalled();
    });

    test("element fromTo passes delay", () => {
        const el = document.createElement("div");
        animate(el, { y: [0, 10] }, { delay: 0.2 });
        const to = fromToSpy.mock.calls[fromToSpy.mock.calls.length - 1][2] as any;
        expect(to.delay).toBe(0.2);
    });

    test("element fromTo stop kills tween", () => {
        const el = document.createElement("div");
        const ctrl = animate(el, { y: [0, 1] }, {});
        ctrl.stop();
        expect((fromToSpy.mock.results[fromToSpy.mock.results.length - 1].value as any).kill).toHaveBeenCalled();
    });

    test("animate with null keyframes defaults to empty", () => {
        const el = document.createElement("div");
        animate(el, null, {});
        expect(fromToSpy).toHaveBeenCalled();
    });

    test("element fromTo single-element array uses same value", () => {
        const el = document.createElement("div");
        animate(el, { y: [5] }, {});
        const [, from, to] = fromToSpy.mock.calls[fromToSpy.mock.calls.length - 1] as [any, any, any];
        expect(from.y).toBe(5);
        expect(to.y).toBe(5);
    });

    test("element fromTo with null element does not clear inline", () => {
        animate(null, { y: [0, 1] }, {});
        const to = fromToSpy.mock.calls[fromToSpy.mock.calls.length - 1][2] as any;
        expect(() => to.onComplete()).not.toThrow();
    });

    test("element with >2-value arrays uses keyframes sequence", () => {
        const el = document.createElement("div");
        el.style.transform = "scale(1.08)";
        const onComplete = vi.fn();
        const ctrl = animate(el, { scale: [1, 1.08, 1] }, { duration: 0.36, ease: "easeOut", onComplete });
        expect(toSpy).toHaveBeenCalled();
        const [target, vars] = toSpy.mock.calls[toSpy.mock.calls.length - 1] as [any, any];
        expect(target).toBe(el);
        expect(vars.keyframes).toEqual([{ scale: 1 }, { scale: 1.08 }, { scale: 1 }]);
        expect(vars.duration).toBe(0.36);
        expect(vars.ease).toBe("power2.out");
        vars.onComplete();
        expect(onComplete).toHaveBeenCalled();
        expect(el.style.transform).toBe("");
        expect(typeof ctrl.stop).toBe("function");
    });

    test("shake >2 arrays produce full keyframe sequence", () => {
        const el = document.createElement("div");
        animate(el, { x: [0, 7, -7, 7, -7, 4, -4, 0] }, { duration: 0.42, ease: "easeInOut" });
        const vars = toSpy.mock.calls[toSpy.mock.calls.length - 1][1] as any;
        expect(vars.keyframes).toHaveLength(8);
        expect(vars.keyframes[0]).toEqual({ x: 0 });
        expect(vars.keyframes[7]).toEqual({ x: 0 });
        expect(vars.ease).toBe("power2.inOut");
    });

    test("multi keyframes with scalar property covers else branch", () => {
        const el = document.createElement("div");
        animate(el, { scale: [1, 1.08, 1], opacity: 0.5 }, {});
        const vars = toSpy.mock.calls[toSpy.mock.calls.length - 1][1] as any;
        expect(vars.keyframes[0].opacity).toBe(0.5);
        expect(vars.keyframes[2].opacity).toBe(0.5);
    });

    test("multi keyframes invokes onUpdate when provided", () => {
        const el = document.createElement("div");
        const onUpdate = vi.fn();
        animate(el, { scale: [1, 1.08, 1] }, { onUpdate });
        const vars = toSpy.mock.calls[toSpy.mock.calls.length - 1][1] as any;
        expect(typeof vars.onUpdate).toBe("function");
        vars.onUpdate();
        expect(onUpdate).toHaveBeenCalled();
    });

    test("multi keyframes omits onUpdate when not provided", () => {
        const el = document.createElement("div");
        animate(el, { scale: [1, 1.08, 1] }, {});
        const vars = toSpy.mock.calls[toSpy.mock.calls.length - 1][1] as any;
        expect(vars.onUpdate).toBeUndefined();
    });

    test("multi keyframes passes delay", () => {
        const el = document.createElement("div");
        animate(el, { y: [0, 1, 2] }, { delay: 0.1 });
        const vars = toSpy.mock.calls[toSpy.mock.calls.length - 1][1] as any;
        expect(vars.delay).toBe(0.1);
    });

    test("multi keyframes with null element does not clear inline", () => {
        animate(null, { y: [0, 1, 2] }, {});
        const vars = toSpy.mock.calls[toSpy.mock.calls.length - 1][1] as any;
        expect(() => vars.onComplete()).not.toThrow();
    });

    test("multi keyframes stop kills tween", () => {
        const el = document.createElement("div");
        const ctrl = animate(el, { y: [0, 1, 2] }, {});
        ctrl.stop();
        expect((toSpy.mock.results[toSpy.mock.results.length - 1].value as any).kill).toHaveBeenCalled();
    });
});
