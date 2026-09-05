import { Link } from "@tanstack/react-router";
import { Card } from "../components/ui/Card";
import { JoinForm } from "../components/JoinForm";
import { SoundToggle } from "../components/SoundToggle";
import { MotionToggle } from "../components/MotionToggle";

/**
 * Join-only landing: one wordmark, one card, the PIN field as the loudest
 * thing on screen. No imagery, no extra sections — players arrive with
 * one job.
 */
export function LandingView() {
    return (
        <div className="pattern-exam min-h-[100dvh] flex flex-col">
            <header className="w-full max-w-3xl mx-auto px-6 pt-4 flex items-center justify-end gap-1">
                <SoundToggle />
                <MotionToggle />
            </header>

            <main className="flex-1 flex items-center justify-center px-6 py-10">
                <div className="w-full max-w-md flex flex-col gap-6">
                    <p className="text-center font-black tracking-tight leading-none">
                        <span style={{ fontSize: "clamp(44px, 9vw, 72px)" }}>
                            Sprint<span style={{ color: "var(--oq-accent)" }}>Judge</span>
                        </span>
                    </p>
                    <Card className="card-accent p-6 md:p-8">
                        <JoinForm heading="Join a live game" submitClassName="btn-kahoot" />
                    </Card>
                    <p className="text-center text-sm font-bold text-[var(--oq-ink-soft)]">
                        <Link to="/solo" className="underline underline-offset-4">
                            Practice solo
                        </Link>
                        <span aria-hidden="true" className="mx-3 opacity-60">
                            |
                        </span>
                        <Link to="/explore" className="underline underline-offset-4">
                            Browse quizzes
                        </Link>
                    </p>
                </div>
            </main>
        </div>
    );
}
