import { useI18n } from "../i18n";
import { Link, useParams } from "@tanstack/react-router";
import { Shell } from "../components/Shell";
import { Card } from "../ui/Card";
import { Chip } from "../ui/Primitives";
import { JoinForm } from "../components/JoinForm";

export function JoinView() {
    const { t } = useI18n();
    const params = useParams({ strict: false }) as { pin?: string };
    const invitedPin = params.pin ? params.pin.replace(/\D/g, "").slice(0, 6) : "";

    return (
        <Shell>
            <div className="flex-1 flex items-center justify-center p-6">
                <div className="w-full max-w-lg flex flex-col gap-4">
                    {invitedPin && (
                        <div className="text-center">
                            <Chip tone="accent">{t("ui.invited", { pin: invitedPin })}</Chip>
                        </div>
                    )}
                    <Card className="p-6">
                        <JoinForm
                            key={invitedPin}
                            initialPin={invitedPin}
                            heading={t("ui.joinALiveGame")}
                        />
                    </Card>
                    <p className="text-center text-sm text-[var(--oq-ink-soft)]">
                        <Link
                            to="/"
                            className="font-bold text-[var(--oq-accent-dark)] underline underline-offset-4"
                        >
                            {t("ui.back")}
                        </Link>
                    </p>
                </div>
            </div>
        </Shell>
    );
}
