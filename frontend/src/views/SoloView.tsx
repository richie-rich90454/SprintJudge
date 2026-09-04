import { Link } from "@tanstack/react-router";
import { Shell } from "../components/Shell";
import { Card } from "../components/ui/Card";
import { JoinForm } from "../components/JoinForm";

export function SoloView() {
    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6 max-w-xl">
                <div>
                    <p className="label-caps mb-3">Solo practice</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">
                        Drill at your own pace
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        Solo runs on a PRACTICE-mode room PIN: untimed, with instant
                        feedback after every answer and automatic advance to the next
                        question. No host, no waiting.
                    </p>
                </div>
                <Card className="p-6">
                    <JoinForm heading="Enter your practice PIN" />
                </Card>
                <p className="text-sm text-[var(--oq-ink-soft)]">
                    Ask your teacher for the practice PIN, or launch one from{" "}
                    <Link to="/admin">/admin</Link>.
                </p>
            </div>
        </Shell>
    );
}
