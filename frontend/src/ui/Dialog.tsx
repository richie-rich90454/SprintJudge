import * as RadixDialog from "@radix-ui/react-dialog";
import { X } from "@phosphor-icons/react";
import { useRef, type ReactNode } from "react";
import { useI18n } from "../i18n";

interface DialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    title: string;
    children: ReactNode;
    size?: "md" | "lg";
}

/** Accessible modal (Radix) with the flat topbar treatment. */
export function Dialog({ open, onOpenChange, title, children, size = "md" }: DialogProps) {
    const { t } = useI18n();
    // No Radix Trigger is used, so restore the opener ourselves on close.
    const openerRef = useRef<HTMLElement>(document.body);
    return (
        <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
            <RadixDialog.Portal>
                <RadixDialog.Overlay className="fixed inset-0 z-50 bg-black/50" />
                <RadixDialog.Content
                    aria-describedby={undefined}
                    onOpenAutoFocus={() => {
                        openerRef.current = document.activeElement as HTMLElement;
                    }}
                    onCloseAutoFocus={(event) => {
                        event.preventDefault();
                        openerRef.current.focus();
                    }}
                    className={`modal-topbar fixed left-1/2 top-1/2 z-50 w-[calc(100vw-2rem)] ${size === "lg" ? "max-w-2xl" : "max-w-lg"} -translate-x-1/2 -translate-y-1/2 p-6 outline-none max-h-[85vh] overflow-y-auto`}
                >
                    <div className="flex items-center justify-between mb-4">
                        <RadixDialog.Title className="font-extrabold text-lg">
                            {title}
                        </RadixDialog.Title>
                        <RadixDialog.Close
                            aria-label={t("ui.closeDialog")}
                            className="btn btn-ghost btn-icon"
                        >
                            <X size={18} weight="bold" />
                        </RadixDialog.Close>
                    </div>
                    {children}
                </RadixDialog.Content>
            </RadixDialog.Portal>
        </RadixDialog.Root>
    );
}
