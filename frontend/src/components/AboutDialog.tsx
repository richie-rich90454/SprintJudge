import { useI18n } from "../i18n";
import { useState } from "react";
import { Info } from "@phosphor-icons/react";
import { Dialog } from "../ui/Dialog";

/** Header About control. Replaces the old always-visible license footer. */
export function AboutDialog() {
    const { t } = useI18n();
    const [open, setOpen] = useState(false);
    return (
        <>
            <button
                type="button"
                aria-label={t("ui.aboutSprintJudge")}
                title={t("ui.aboutSprintJudge")}
                className="btn btn-ghost btn-icon header-icon"
                onClick={() => setOpen(true)}
            >
                <Info size={18} weight="bold" />
            </button>
            <Dialog open={open} onOpenChange={setOpen} title={t("ui.aboutSprintJudge")}>
                <div className="flex flex-col gap-3 text-sm text-[var(--oq-ink-soft)]">
                    <p className="font-bold text-[var(--oq-ink)]">SprintJudge 1.0.0</p>
                    <p>{t("ui.selfhostedClassroomQuizAndCodeJudge")}</p>
                    <p>
                        {t(
                            "ui.releasedUnderTheGNUGeneralPublicLicenseV30TheFullLicenseTextShipsWithTheSourceDistribution",
                        )}
                    </p>
                </div>
            </Dialog>
        </>
    );
}
