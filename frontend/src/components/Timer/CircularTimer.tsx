import { useEffect, useRef, useState } from "react";
import { motion } from "../../services/MotionService";

interface CircularTimerProps {
    endEpochMs: number;
    totalSec: number;
    onExpire?: () => void;
}

export function CircularTimer({ endEpochMs, totalSec, onExpire }: CircularTimerProps) {
    const [remaining, setRemaining] = useState(Math.max(0, endEpochMs - Date.now()));
    const fired = useRef(false);
    const wrapRef = useRef<HTMLDivElement>(null);
    const onExpireRef = useRef(onExpire);
    onExpireRef.current = onExpire;

    useEffect(() => {
        fired.current = false;
        const tick = () => {
            const rem = Math.max(0, endEpochMs - Date.now());
            setRemaining(rem);
            if (rem <= 0 && !fired.current) {
                fired.current = true;
                onExpireRef.current?.();
            }
        };
        tick();
        const id = setInterval(tick, 250);
        return () => clearInterval(id);
    }, [endEpochMs]);

    const secs = Math.ceil(remaining / 1000);
    const wasLow = useRef(false);
    useEffect(() => {
        const low = secs <= 10 && secs > 0;
        if (low && !wasLow.current) motion.pulse(wrapRef.current);
        wasLow.current = low;
    }, [secs]);
    const frac = totalSec > 0 ? Math.min(1, remaining / (totalSec * 1000)) : 0;
    const r = 38;
    const c = 2 * Math.PI * r;
    const low = secs <= 10;

    return (
        <div ref={wrapRef} className="relative w-[96px] h-[96px]">
            <svg viewBox="0 0 96 96" className="w-full h-full -rotate-90">
                <circle
                    cx="48"
                    cy="48"
                    r={r}
                    fill="none"
                    stroke="var(--oq-border)"
                    strokeWidth="7"
                />
                <circle
                    cx="48"
                    cy="48"
                    r={r}
                    fill="none"
                    stroke={low ? "var(--oq-warning)" : "var(--oq-accent)"}
                    strokeWidth="7"
                    strokeLinecap="round"
                    strokeDasharray={c}
                    strokeDashoffset={c * (1 - frac)}
                />
            </svg>
            <div
                role="timer"
                aria-label={`${secs} seconds remaining`}
                className={`absolute inset-0 flex flex-col items-center justify-center font-mono font-semibold ${
                    low ? "text-3xl text-[var(--oq-warning)]" : "text-xl"
                }`}
            >
                {secs}
                {low && secs > 0 && (
                    <span aria-hidden="true" className="label-caps text-[8px] tracking-widest">
                        Hurry
                    </span>
                )}
            </div>
        </div>
    );
}
