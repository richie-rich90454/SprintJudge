import { useI18n } from "../i18n";
import { ReactNode } from "react";
import { Link } from "@tanstack/react-router";
import { LogoMark } from "./LogoMark";
import { SoundToggle } from "./SoundToggle";
import { MotionToggle } from "./MotionToggle";
import { AboutDialog } from "./AboutDialog";
import { webSocketService } from "../game/WebSocketService";
import { useEffect, useState } from "react";

/** Shared outer shell: header with logo, connection, sound, motion and About. */
export function Shell({ children, minimal = false }: { children: ReactNode; minimal?: boolean }) {
    const { t } = useI18n();
    const [online, setOnline] = useState(false);
    useEffect(() => {
        const sub = webSocketService.onStatus().subscribe((s) => setOnline(s === "open"));
        return () => sub.unsubscribe();
    }, []);

    return (
        <div className="pattern-exam min-h-[100dvh] flex flex-col">
            <header className="border-b border-[var(--oq-border)] bg-[var(--oq-surface)]">
                <div className="page-shell py-3 flex items-center justify-between">
                    <Link
                        to="/"
                        className="flex items-center gap-2.5"
                        aria-label={t("ui.sprintjudgeHome")}
                    >
                        <LogoMark size={28} />
                        <span className="font-extrabold tracking-tight">{t("ui.brand")}</span>
                    </Link>
                    <div className="flex items-center gap-1">
                        {!minimal && (
                            <span
                                className="chip chip-neutral mr-1"
                                title={online ? t("ui.connected") : t("ui.notConnected")}
                            >
                                <span
                                    aria-hidden="true"
                                    className="inline-block w-2 h-2 rounded-full"
                                    style={{
                                        background: online
                                            ? "var(--oq-success)"
                                            : "var(--oq-border-strong)",
                                    }}
                                />
                                {online ? t("ui.live") : t("ui.offline")}
                            </span>
                        )}
                        <SoundToggle className="header-icon" />
                        <MotionToggle className="header-icon" />
                        <AboutDialog />
                    </div>
                </div>
            </header>
            <main className="flex-1 flex flex-col w-full">{children}</main>
        </div>
    );
}
