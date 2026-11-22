import { useState } from "react";
import { Info } from "@phosphor-icons/react";
import { Dialog } from "../ui/Dialog";

/** Header About control. Replaces the old always-visible license footer. */
export function AboutDialog() {
    const [open, setOpen] = useState(false);
    return (
        <>
            <button
                type="button"
                aria-label="About SprintJudge"
                title="About SprintJudge"
                className="btn btn-ghost btn-icon header-icon"
                onClick={() => setOpen(true)}
            >
                <Info size={18} weight="bold" />
            </button>
            <Dialog open={open} onOpenChange={setOpen} title="About SprintJudge">
                <div className="flex flex-col gap-3 text-sm text-[var(--oq-ink-soft)]">
                    <p className="font-bold text-[var(--oq-ink)]">SprintJudge 1.0.0</p>
                    <p>Self-hosted classroom quiz and code judge.</p>
                    <p>
                        Released under the GNU General Public License v3.0. The full license text
                        ships with the source distribution.
                    </p>
                </div>
            </Dialog>
        </>
    );
}
