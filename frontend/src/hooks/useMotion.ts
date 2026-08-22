import { useEffect, useRef } from "react";
import { motion, MotionPreset } from "../services/MotionService";

/** Entrance animation for a container element; tweens are killed on unmount. */
export function useEnter<T extends HTMLElement>(preset: MotionPreset, deps: unknown[] = []) {
  const ref = useRef<T>(null);
  useEffect(() => {
    motion.enter(ref.current, preset);
    const el = ref.current;
    return () => motion.killFor(el);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return ref;
}

/** Staggered entrance for children matching `selector`; killed on unmount/deps. */
export function useStaggerIn<T extends HTMLElement>(selector: string, deps: unknown[] = [], offset = 0.05) {
  const ref = useRef<T>(null);
  useEffect(() => {
    motion.staggerIn(ref.current, selector, offset);
    const el = ref.current;
    return () => motion.killFor(el);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return ref;
}
