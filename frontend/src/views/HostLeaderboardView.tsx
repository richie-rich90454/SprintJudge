import { motion as fm } from "framer-motion";
import { Card, CardContent } from "@heroui/react";
import { useGameStore } from "../stores/useGameStore";
import { useStaggerIn } from "../hooks/useMotion";
import { useVirtualWindow } from "../hooks/useVirtualWindow";

const ROW_H = 44;

export function HostLeaderboardView() {
    const leaderboard = useGameStore((s) => s.leaderboard);
    const room = useGameStore((s) => s.room);

    const rows = leaderboard.length
        ? leaderboard
        : (room?.players ?? []).map((p, i) => ({
              uuid: p.uuid,
              name: p.name,
              score: p.score,
              rank: i + 1,
          }));

    const listRef = useStaggerIn<HTMLDivElement>(".lb-row", [rows.length], 0.05);
    const { ref, start, end } = useVirtualWindow(rows.length, ROW_H);
    const slice = rows.slice(start, end);

    return (
        <Card className="bg-content1 overflow-hidden">
            <CardContent className="p-0">
                <div className="flex items-center justify-between mb-3 px-5 pt-5 pb-4 border-b border-default-200">
                    <h3 className="header-double !mb-0">Leaderboard</h3>
                    <span className="label-caps">{rows.length} players</span>
                </div>

                <div ref={listRef} className="px-0">
                    <div ref={ref} className="overflow-y-auto" style={{ maxHeight: 460 }}>
                        {rows.length > 0 && (
                            <>
                                <div style={{ height: start * ROW_H }} aria-hidden="true" />
                                {slice.map((r) => {
                                    const podium = r.rank <= 3;
                                    return (
                                        <fm.div
                                            key={r.uuid}
                                            layout
                                            className={`lb-row flex items-center gap-4 px-3 border-b border-dotted border-default-200 ${podium ? "bg-default-100" : ""}`}
                                            style={{ height: ROW_H }}
                                        >
                                            <span
                                                className="mono font-bold w-8 text-right"
                                                style={
                                                    podium
                                                        ? { color: "var(--oq-red)", fontSize: 18 }
                                                        : { color: "var(--oq-border-strong)" }
                                                }
                                            >
                                                {r.rank}
                                            </span>
                                            <span className="truncate font-semibold flex-1">
                                                {r.name}
                                            </span>
                                            <span
                                                className="mono font-bold tabular-nums"
                                                style={{ fontSize: 17 }}
                                            >
                                                {r.score.toLocaleString()}
                                            </span>
                                        </fm.div>
                                    );
                                })}
                                <div
                                    style={{ height: Math.max(0, (rows.length - end) * ROW_H) }}
                                    aria-hidden="true"
                                />
                            </>
                        )}
                        {rows.length === 0 && (
                            <p className="text-default-500 text-sm p-4">
                                No players yet — share the PIN.
                            </p>
                        )}
                    </div>
                </div>
            </CardContent>
        </Card>
    );
}
