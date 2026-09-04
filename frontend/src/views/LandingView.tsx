import { Link } from "@tanstack/react-router";
import { Shell } from "../components/Shell";
import { Card } from "../components/ui/Card";
import { JoinForm } from "../components/JoinForm";

export function LandingView() {
    return (
        <Shell>
            <div className="page-shell py-10 md:py-16 flex flex-col gap-12 md:gap-16">
                <section className="grid gap-8 lg:grid-cols-2 lg:items-center">
                    <div className="max-w-xl">
                        <p className="label-caps mb-4">Self-hosted classroom quiz + code judge</p>
                        <h1
                            className="font-extrabold tracking-tight leading-none"
                            style={{ fontSize: "clamp(44px, 7vw, 88px)" }}
                        >
                            Sprint<span style={{ color: "var(--oq-accent)" }}>Judge</span>
                        </h1>
                        <p className="text-[var(--oq-ink-soft)] mt-4 text-lg leading-relaxed">
                            Live quizzes and real code judging on your own server. No accounts,
                            just a PIN.
                        </p>
                        <div className="flex flex-wrap gap-3 mt-6">
                            <a href="#join" className="btn btn-primary btn-lg font-bold">
                                Join a game
                            </a>
                            <Link
                                to="/explore"
                                className="btn btn-secondary btn-lg font-bold"
                            >
                                Explore quizzes
                            </Link>
                        </div>
                    </div>
                    <img
                        src="https://picsum.photos/seed/sprintjudge-classroom/880/660"
                        width={880}
                        height={660}
                        alt="Students answering a live classroom quiz together"
                        loading="eager"
                        className="w-full aspect-[4/3] object-cover rounded-[14px] border border-[var(--oq-border)]"
                    />
                </section>

                <section aria-label="Ways to play" className="grid gap-4 md:grid-cols-5">
                    <Card
                        className="p-6 flex flex-col gap-2 md:col-span-3"
                        style={{ background: "var(--oq-accent-tint)" }}
                    >
                        <h2 className="text-xl font-extrabold">Solo practice</h2>
                        <p className="text-sm text-[var(--oq-ink-soft)] leading-relaxed">
                            Untimed drills with instant feedback. No waiting, no leaderboard
                            pressure.
                        </p>
                        <Link to="/solo" className="btn btn-secondary mt-3 self-start font-bold">
                            Practice alone
                        </Link>
                    </Card>
                    <Card className="p-6 flex flex-col gap-2 md:col-span-2">
                        <h2 className="text-xl font-extrabold">Browse library</h2>
                        <p className="text-sm text-[var(--oq-ink-soft)] leading-relaxed">
                            See what quizzes exist before you ask for a PIN.
                        </p>
                        <Link
                            to="/explore"
                            className="btn btn-secondary mt-3 self-start font-bold"
                        >
                            Explore quizzes
                        </Link>
                    </Card>
                </section>

                <section
                    id="join"
                    aria-label="Join a live game"
                    className="grid gap-8 lg:grid-cols-2 lg:items-center scroll-mt-6"
                >
                    <Card className="p-6">
                        <JoinForm heading="Join a live game" />
                    </Card>
                    <img
                        src="https://picsum.photos/seed/sprintjudge-code-quiz/720/540"
                        width={720}
                        height={540}
                        alt="Code editor with a judged programming question"
                        loading="lazy"
                        className="w-full aspect-[4/3] object-cover rounded-[14px] border border-[var(--oq-border)]"
                    />
                </section>

                <p className="text-sm text-[var(--oq-ink-soft)]">
                    Host or teach? <Link to="/admin">Open the admin dashboard</Link>.
                </p>
            </div>
        </Shell>
    );
}
