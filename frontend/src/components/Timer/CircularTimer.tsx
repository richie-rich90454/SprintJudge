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
    const prevSec = useRef(-1);
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
    if (secs <= 10 && secs > 0 && secs !== prevSec.current) {
        prevSec.current = secs;
        motion.pulse(wrapRef.current);
    }
    const frac = totalSec > 0 ? Math.min(1, remaining / (totalSec * 1000)) : 0;
    const r = 34;
    const c = 2 * Math.PI * r;
    const low = secs <= 10;

    return (
        <div ref={wrapRef} className="relative w-[84px] h-[84px]">
            <svg viewBox="0 0 84 84" className="w-full h-full -rotate-90">
                <circle cx="42" cy="42" r={r} fill="none" stroke="#dadce0" strokeWidth="6" />
                <circle
                    cx="42"
                    cy="42"
                    r={r}
                    fill="none"
                    stroke={low ? "#B3261E" : "#C8102E"}
                    strokeWidth="6"
                    strokeLinecap="round"
                    strokeDasharray={c}
                    strokeDashoffset={c * (1 - frac)}
                />
            </svg>
            <div className="absolute inset-0 flex items-center justify-center font-mono text-lg font-semibold">
                {secs}
            </div>
        </div>
    );
}
