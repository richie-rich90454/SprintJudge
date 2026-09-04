import { Link } from "@tanstack/react-router";
import { Card } from "../components/ui/Card";
import { JoinForm } from "../components/JoinForm";
import { ThemeToggle } from "../components/ThemeToggle";
import { SoundToggle } from "../components/SoundToggle";
import { MotionToggle } from "../components/MotionToggle";

/**
 * Join-only landing in the Kahoot key: flat purple stage, one white card,
 * the PIN field as the loudest thing on screen. No imagery, no extra
 * sections — players arrive with one job.
 */
export function LandingView() {
    return (
        <div
            className="on-brand min-h-[100dvh] flex flex-col"
            style={{ background: "#46178f", color: "#fff" }}
        >
            <header className="w-full max-w-3xl mx-auto px-6 pt-4 flex items-center justify-end gap-1 text-white">
                <SoundToggle />
                <MotionToggle />
                <ThemeToggle />
            </header>

            <main className="flex-1 flex items-center justify-center px-6 py-10">
                <div className="w-full max-w-md flex flex-col gap-6">
                    <div className="text-center">
                        <p className="font-black tracking-tight leading-none text-white">
                            <span style={{ fontSize: "clamp(44px, 9vw, 72px)" }}>
                                SprintJudge
                            </span>
                        </p>
                    </div>
                    <Card className="force-light p-6 md:p-8">
                        <JoinForm heading="Join a live game" submitClassName="btn-kahoot" />
                    </Card>
                    <p className="text-center text-sm font-bold">
                        <Link to="/solo" className="text-white underline underline-offset-4">
                            Practice solo
                        </Link>
                        <span aria-hidden="true" className="mx-3 opacity-60">
                            |
                        </span>
                        <Link to="/explore" className="text-white underline underline-offset-4">
                            Browse quizzes
                        </Link>
                    </p>
                </div>
            </main>
        </div>
    );
}
