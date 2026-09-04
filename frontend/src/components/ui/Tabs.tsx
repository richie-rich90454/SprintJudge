import * as RadixTabs from "@radix-ui/react-tabs";
import { ReactNode } from "react";

interface TabsProps {
    value: string;
    onValueChange: (v: string) => void;
    tabs: { id: string; label: string }[];
    children: ReactNode;
    label: string;
}

/** Accessible tab bar (Radix) in the flat underline style. */
export function Tabs({ value, onValueChange, tabs, children, label }: TabsProps) {
    return (
        <RadixTabs.Root value={value} onValueChange={onValueChange}>
            <RadixTabs.List
                aria-label={label}
                className="flex gap-1 mb-6 overflow-x-auto"
            >
                {tabs.map((t) => (
                    <RadixTabs.Trigger
                        key={t.id}
                        value={t.id}
                        className="px-4 py-2 min-h-[44px] text-sm font-bold border-b-2 transition-colors whitespace-nowrap data-[state=active]:border-[var(--oq-accent)] data-[state=active]:text-[var(--oq-accent)] border-transparent text-[var(--oq-ink-soft)] hover:text-[var(--oq-ink)]"
                    >
                        {t.label}
                    </RadixTabs.Trigger>
                ))}
            </RadixTabs.List>
            {children}
        </RadixTabs.Root>
    );
}

export function TabPanel({ value, children }: { value: string; children: ReactNode }) {
    return (
        <RadixTabs.Content value={value} className="outline-none">
            {children}
        </RadixTabs.Content>
    );
}
