import { useEffect, useMemo } from "react";
import { motion } from "framer-motion";
import { ANSWER_PALETTE } from "../design/kahoot";

interface ConfettiProps {
    /** Number of pieces. */
    count?: number;
    /** Fire key — change it to replay the burst. */
    fireKey?: number | string;
}

interface Piece {
    id: number;
    x: number;
    y: number;
    rotate: number;
    color: string;
    size: number;
    delay: number;
    round: boolean;
}

/**
 * Pure framer-motion confetti burst (no extra library). Pieces launch from the
 * horizontal center, near the top, and fall outward with gravity + spin.
 */
export function Confetti({ count = 90, fireKey = 0 }: ConfettiProps) {
    const pieces = useMemo<Piece[]>(() => {
        return Array.from({ length: count }, (_, id) => {
            const angle = (Math.random() - 0.5) * Math.PI * 1.4;
            const dist = 220 + Math.random() * 420;
            return {
                id,
                x: Math.sin(angle) * dist,
                y: -Math.abs(Math.cos(angle) * dist) - 80 - Math.random() * 120,
                rotate: Math.random() * 720 - 360,
                color: ANSWER_PALETTE[id % ANSWER_PALETTE.length],
                size: 8 + Math.random() * 8,
                delay: Math.random() * 0.15,
                round: Math.random() > 0.5,
            };
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [count, fireKey]);

    useEffect(() => {
        /* re-mount pieces when fireKey changes handled by key below */
    }, [fireKey]);

    return (
        <div
            className="pointer-events-none fixed inset-0 z-[60] overflow-hidden"
            aria-hidden="true"
        >
            {pieces.map((p) => (
                <motion.span
                    key={`${fireKey}-${p.id}`}
                    initial={{ opacity: 1, x: "50vw", y: "18vh", scale: 1, rotate: 0 }}
                    animate={{
                        opacity: [1, 1, 0],
                        x: `calc(50vw + ${p.x}px)`,
                        y: `calc(18vh + ${p.y}px)`,
                        rotate: p.rotate,
                    }}
                    transition={{ duration: 1.5, delay: p.delay, ease: [0.2, 0.6, 0.3, 1] }}
                    style={{
                        position: "absolute",
                        width: p.size,
                        height: p.size * (p.round ? 1 : 0.4),
                        background: p.color,
                        borderRadius: p.round ? "50%" : 2,
                    }}
                />
            ))}
        </div>
    );
}
