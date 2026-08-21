import { useEffect, useRef } from "react";
import { motion, MotionPreset } from "../services/MotionService";

/** Entrance animation for a container element on mount (and on dep change). */
export function useEnter<T extends HTMLElement>(preset: MotionPreset, deps: unknown[] = []) {
  const ref = useRef<T>(null);
  useEffect(() => {
    motion.enter(ref.current, preset);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return ref;
}

/** Staggered entrance for children matching `selector` inside the container. */
export function useStaggerIn<T extends HTMLElement>(selector: string, deps: unknown[] = [], offset = 0.05) {
  const ref = useRef<T>(null);
  useEffect(() => {
    motion.staggerIn(ref.current, selector, offset);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return ref;
}
