import { useI18n } from "../i18n";
import { Link } from "@tanstack/react-router";
import { Shell } from "../components/Shell";
import { Card } from "../ui/Card";
import { JoinForm } from "../components/JoinForm";

export function SoloView() {
    const { t } = useI18n();
    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6 max-w-xl">
                <div>
                    <p className="label-caps mb-3">{t("ui.soloPractice")}</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">
                        {t("ui.drillAtYourOwnPace")}
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        {t(
                            "ui.soloRunsOnAPRACTICEmodeRoomPINUntimedWithInstantFeedbackAfterEveryAnswerAndAutomaticAdvanceToTheNextQuestionNoHostNoWaiting",
                        )}
                    </p>
                </div>
                <Card className="p-6">
                    <JoinForm heading={t("ui.enterYourPracticePIN")} />
                </Card>
                <p className="text-sm text-[var(--oq-ink-soft)]">
                    {t("ui.orSkipPin")}{" "}
                    <Link
                        to="/practice"
                        className="font-bold text-[var(--oq-accent-dark)] underline underline-offset-4"
                    >
                        {t("ui.startAPracticeSet")}
                    </Link>
                    .
                </p>
            </div>
        </Shell>
    );
}
