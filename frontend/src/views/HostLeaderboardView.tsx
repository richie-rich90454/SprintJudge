import { motion as fm } from "framer-motion";
import { Card, CardContent } from "@heroui/react";
import { useGameStore } from "../stores/useGameStore";
import { useStaggerIn } from "../hooks/useMotion";
import { useVirtualWindow } from "../hooks/useVirtualWindow";

const ROW_H = 44;

export function HostLeaderboardView() {
    const leaderboard = useGameStore((s) => s.leaderboard);
    const room = useGameStore((s) => s.room);
    const wsError = useGameStore((s) => s.error);

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
        <Card className="bg-[var(--oq-surface)] overflow-hidden">
            <CardContent className="p-0">
                <div className="sticky top-0 z-10 bg-[var(--oq-surface)] flex items-center justify-between mb-3 px-6 pt-6 pb-6 border-b border-[var(--oq-border)]">
                    <h3 className="header-double !mb-0">Leaderboard</h3>
                    <span className="label-caps">{rows.length} players</span>
                </div>
                {wsError && (
                    <p role="alert" className="text-[var(--oq-danger)] text-sm px-6 pb-2">
                        Connection error: {wsError}
                    </p>
                )}

                <div ref={listRef} className="px-0 relative">
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
                                            className={`lb-row flex items-center gap-4 px-3 border-b border-dotted border-[var(--oq-border)] ${podium ? "bg-[var(--oq-row-alt)]" : ""}`}
                                            style={{ height: ROW_H, minHeight: 44 }}
                                        >
                                            <span
                                                className="mono font-bold min-w-8 text-right tabular-nums"
                                                style={
                                                    podium
                                                        ? {
                                                              color: "var(--oq-accent)",
                                                              fontSize: 18,
                                                          }
                                                        : { color: "var(--oq-border-strong)" }
                                                }
                                            >
                                                {r.rank}
                                            </span>
                                            <span
                                                className="truncate font-semibold flex-1"
                                                title={r.name}
                                            >
                                                {r.name}
                                            </span>
                                            <span
                                                className="mono font-bold tabular-nums"
                                                style={{ fontSize: 17 }}
                                            >
                                                {(r.score ?? 0).toLocaleString()}
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
                            <p className="text-[var(--oq-ink-soft)] text-sm p-6">
                                No players yet — share the PIN.
                            </p>
                        )}
                    </div>
                    {rows.length * ROW_H > 460 && (
                        <div
                            className="pointer-events-none absolute bottom-0 inset-x-0 h-8"
                            style={{
                                background:
                                    "linear-gradient(to bottom, transparent, var(--oq-surface))",
                            }}
                            aria-hidden="true"
                        />
                    )}
                </div>
            </CardContent>
        </Card>
    );
}
