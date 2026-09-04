import { Link, useParams } from "@tanstack/react-router";
import { Shell } from "../components/Shell";
import { Card } from "../components/ui/Card";
import { Chip } from "../components/ui/Primitives";
import { JoinForm } from "../components/JoinForm";

export function JoinView() {
    const params = useParams({ strict: false }) as { pin?: string };
    const invitedPin = params.pin ? params.pin.replace(/\D/g, "").slice(0, 6) : "";

    return (
        <Shell>
            <div className="flex-1 flex items-center justify-center p-6">
                <div className="w-full max-w-lg flex flex-col gap-4">
                    {invitedPin && (
                        <div className="text-center">
                            <Chip tone="accent">
                                You&apos;ve been invited to game {invitedPin}
                            </Chip>
                        </div>
                    )}
                    <Card className="p-6">
                        <JoinForm
                            key={invitedPin}
                            initialPin={invitedPin}
                            heading="Join a live game"
                        />
                    </Card>
                    <p className="text-center text-sm text-[var(--oq-ink-soft)]">
                        <Link
                            to="/"
                            className="font-bold text-[var(--oq-accent-dark)] underline underline-offset-4"
                        >
                            Back
                        </Link>
                    </p>
                </div>
            </div>
        </Shell>
    );
}
