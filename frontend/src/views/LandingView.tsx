import { Link } from "@tanstack/react-router";
import { Shell } from "../components/Shell";
import { Card } from "../components/ui/Card";
import { JoinForm } from "../components/JoinForm";

const MODES = [
    {
        eyebrow: "Live",
        title: "Live game",
        text: "Join your class with a 6-digit PIN and answer in real time.",
        to: "/join" as const,
        cta: "Enter PIN",
    },
    {
        eyebrow: "Solo",
        title: "Solo practice",
        text: "Untimed drills with instant feedback. No waiting, no leaderboard pressure.",
        to: "/solo" as const,
        cta: "Practice alone",
    },
    {
        eyebrow: "Library",
        title: "Browse library",
        text: "See what quizzes exist before you ask for a PIN.",
        to: "/explore" as const,
        cta: "Explore quizzes",
    },
];

export function LandingView() {
    return (
        <Shell>
            <div className="page-shell py-10 md:py-16 flex flex-col gap-10">
                <section className="max-w-3xl">
                    <p className="label-caps mb-4">Self-hosted classroom quiz + code judge</p>
                    <h1
                        className="font-extrabold tracking-tight leading-none"
                        style={{ fontSize: "clamp(44px, 9vw, 110px)" }}
                    >
                        Sprint<span style={{ color: "var(--oq-accent)" }}>Judge</span>
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-4 text-lg leading-relaxed">
                        Live quizzes and real code judging on your own server. Pick a mode and
                        start answering.
                    </p>
                </section>
                <section
                    aria-label="Ways to play"
                    className="grid gap-4 md:grid-cols-3"
                >
                    {MODES.map((m) => (
                        <Card key={m.title} className="p-6 flex flex-col gap-2">
                            <p className="label-caps">{m.eyebrow}</p>
                            <h2 className="text-xl font-extrabold">{m.title}</h2>
                            <p className="text-sm text-[var(--oq-ink-soft)] leading-relaxed">
                                {m.text}
                            </p>
                            <Link
                                to={m.to}
                                className="btn btn-secondary mt-3 text-center font-bold"
                            >
                                {m.cta}
                            </Link>
                        </Card>
                    ))}
                </section>
                <section className="max-w-xl w-full">
                    <Card className="p-6">
                        <JoinForm heading="Join a live game" />
                    </Card>
                </section>
                <p className="text-sm text-[var(--oq-ink-soft)]">
                    Host or teach? <Link to="/admin">Open the admin dashboard</Link>.
                </p>
            </div>
        </Shell>
    );
}
