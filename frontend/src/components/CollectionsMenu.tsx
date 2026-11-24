import { useState } from "react";
import { Button } from "../ui/Button";
import { Dialog } from "../ui/Dialog";
import { TextInput } from "../ui/TextInput";

const KEY = "oq_collections";

interface Collection {
    id: string;
    name: string;
    quizIds: string[];
}

function read(): Collection[] {
    try {
        const raw = localStorage.getItem(KEY);
        const parsed = raw ? (JSON.parse(raw) as Collection[]) : [];
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

function persist(collections: Collection[]) {
    try {
        localStorage.setItem(KEY, JSON.stringify(collections));
    } catch {
        /* ignore */
    }
}

/** Local folders for grouping quizzes in the library and result flows. */
export function CollectionsMenu({ quizId, title }: { quizId: string; title: string }) {
    const [open, setOpen] = useState(false);
    const [collections, setCollections] = useState<Collection[]>(read);
    const [name, setName] = useState("");
    const count = collections.filter((c) => c.quizIds.includes(quizId)).length;

    const toggle = (id: string) => {
        const next = collections.map((c) =>
            c.id === id
                ? {
                      ...c,
                      quizIds: c.quizIds.includes(quizId)
                          ? c.quizIds.filter((q) => q !== quizId)
                          : [...c.quizIds, quizId],
                  }
                : c,
        );
        setCollections(next);
        persist(next);
    };

    const create = () => {
        const clean = name.trim();
        if (!clean) return;
        const next = [
            ...collections,
            { id: `${Date.now()}`, name: clean, quizIds: [quizId] },
        ];
        setCollections(next);
        persist(next);
        setName("");
    };

    return (
        <>
            <Button variant="secondary" size="sm" onClick={() => setOpen(true)}>
                Collections{count > 0 ? ` (${count})` : ""}
            </Button>
            <Dialog open={open} onOpenChange={setOpen} title="Save to a collection">
                <p className="text-sm text-[var(--oq-ink-soft)] mb-4">
                    {title} - folders are kept in this browser.
                </p>
                <ul className="flex flex-col gap-2 mb-4">
                    {collections.map((c) => (
                        <li key={c.id}>
                            <button
                                type="button"
                                aria-pressed={c.quizIds.includes(quizId)}
                                onClick={() => toggle(c.id)}
                                className="flex min-h-[44px] w-full items-center justify-between border border-[var(--oq-border)] px-3 text-left hover:bg-[var(--oq-row-alt)]"
                            >
                                <span className="font-semibold">{c.name}</span>
                                <span className="mono text-xs text-[var(--oq-ink-soft)]">
                                    {c.quizIds.includes(quizId)
                                        ? "Saved"
                                        : `${c.quizIds.length} quizzes`}
                                </span>
                            </button>
                        </li>
                    ))}
                    {collections.length === 0 && (
                        <li className="text-sm text-[var(--oq-ink-soft)]">No folders yet.</li>
                    )}
                </ul>
                <div className="flex gap-2">
                    <TextInput
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && create()}
                        placeholder="New folder name"
                        aria-label="New folder name"
                        className="flex-1 text-sm"
                    />
                    <Button variant="primary" size="sm" onClick={create} disabled={!name.trim()}>
                        Create
                    </Button>
                </div>
            </Dialog>
        </>
    );
}
