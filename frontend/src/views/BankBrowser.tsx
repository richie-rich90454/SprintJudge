import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Shell } from "../components/Shell";
import { Card } from "../components/ui/Card";
import { Button } from "../components/ui/Button";
import { TextInput } from "../components/ui/TextInput";
import { Chip, EmptyState, Field, Skeleton } from "../components/ui/Primitives";
import {
    getBankCoverage,
    getBankQuestion,
    searchBank,
    type BankQuestionHeader,
    type BankScope,
} from "../services/BankApiService";

// Catalog names from docs/subject-rename-map.md, mapped to bank subject codes.
const SUBJECT_CODES: Record<string, string> = {
    "Java Programming": "JAVA_PROGRAMMING",
    "Computing Foundations": "COMPUTING_FOUNDATIONS",
    Chemistry: "CHEMISTRY",
    "Calculus I": "CALCULUS_I",
    "Calculus II": "CALCULUS_II",
    "Physics I": "PHYSICS_I",
    "Physics II": "PHYSICS_II",
    "Physics: Mechanics": "PHYSICS_MECHANICS",
    "Physics: Electricity and Magnetism": "PHYSICS_EM",
    "European History": "EUROPEAN_HISTORY",
    "United States History": "US_HISTORY",
    "World History": "WORLD_HISTORY",
    Macroeconomics: "MACROECONOMICS",
    Microeconomics: "MICROECONOMICS",
    Statistics: "STATISTICS",
    "United States Government": "US_GOVERNMENT",
    "Comparative Government": "COMPARATIVE_GOVERNMENT",
    Psychology: "PSYCHOLOGY",
    "Environmental Science": "ENVIRONMENTAL_SCIENCE",
    "Human Geography": "HUMAN_GEOGRAPHY",
    "English Language and Composition": "ENGLISH_LANGUAGE",
    "English Literature and Composition": "ENGLISH_LITERATURE",
    Biology: "BIOLOGY",
    "Spanish Language and Culture": "SPANISH",
    "French Language and Culture": "FRENCH",
    "German Language and Culture": "GERMAN",
    "Chinese Language and Culture": "CHINESE",
    "Art History": "ART_HISTORY",
    "Music Theory": "MUSIC_THEORY",
    Precalculus: "PRECALCULUS",
    "Research Methods": "RESEARCH_METHODS",
    "Interdisciplinary Seminar": "SEMINAR",
    "Algebra I": "ALGEBRA_I",
    "Algebra II": "ALGEBRA_II",
    Geometry: "GEOMETRY",
    "Integrated Math I": "INTEGRATED_1",
    "Integrated Math II": "INTEGRATED_2",
    "Integrated Math III": "INTEGRATED_3",
    "Python Programming": "PYTHON",
    "Web Development": "WEB_DEVELOPMENT",
    Cybersecurity: "CYBERSECURITY",
    "Data Science": "DATA_SCIENCE",
};

const SUBJECTS: string[] = [
    "Java Programming",
    "Computing Foundations",
    "Chemistry",
    "Calculus I",
    "Calculus II",
    "Physics I",
    "Physics II",
    "Physics: Mechanics",
    "Physics: Electricity and Magnetism",
    "European History",
    "United States History",
    "World History",
    "Macroeconomics",
    "Microeconomics",
    "Statistics",
    "United States Government",
    "Comparative Government",
    "Psychology",
    "Environmental Science",
    "Human Geography",
    "English Language and Composition",
    "English Literature and Composition",
    "Biology",
    "Spanish Language and Culture",
    "French Language and Culture",
    "German Language and Culture",
    "Chinese Language and Culture",
    "Art History",
    "Music Theory",
    "Precalculus",
    "Research Methods",
    "Interdisciplinary Seminar",
    "Algebra I",
    "Algebra II",
    "Geometry",
    "Integrated Math I",
    "Integrated Math II",
    "Integrated Math III",
    "Python Programming",
    "Web Development",
    "Cybersecurity",
    "Data Science",
];

const SCOPES: { value: BankScope; label: string }[] = [
    { value: "everything", label: "Everything" },
    { value: "current-scope", label: "Current topics" },
    { value: "legacy-scope", label: "Older topics" },
    { value: "core-scope", label: "Core topics" },
];

function scopeLabel(scope: string): string {
    return SCOPES.find((s) => s.value === scope)?.label ?? scope;
}

export function BankBrowser() {
    const [subject, setSubject] = useState(SUBJECTS[0]);
    const [scope, setScope] = useState<BankScope>("everything");
    const [unit, setUnit] = useState("");
    const [selectedId, setSelectedId] = useState<string | null>(null);

    const needle = unit.trim();
    const code = SUBJECT_CODES[subject] ?? subject;
    const search = useQuery({
        queryKey: ["bank-search", code, needle, scope],
        queryFn: () => searchBank({ subject: code, unit: needle || undefined, scope }),
    });
    const coverage = useQuery({
        queryKey: ["bank-coverage", code],
        queryFn: () => getBankCoverage(code),
    });
    const detail = useQuery({
        queryKey: ["bank-question", selectedId],
        queryFn: () => getBankQuestion(selectedId ?? ""),
        enabled: selectedId !== null,
    });

    const pickSubject = (next: string) => {
        setSubject(next);
        setSelectedId(null);
    };
    const pickScope = (next: BankScope) => {
        setScope(next);
        setSelectedId(null);
    };
    const pickUnit = (next: string) => {
        setUnit(next);
        setSelectedId(null);
    };

    const results = search.data ?? [];

    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6">
                <div className="max-w-2xl">
                    <p className="label-caps mb-3">Teacher bank</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">
                        Browse the question bank
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        Pick a subject to see which questions are ready for
                        class. Titles only — answers stay hidden.
                    </p>
                </div>
                <Card className="p-6 flex flex-col gap-5 md:flex-row md:items-end">
                    <div className="flex-1">
                        <Field label="Subject" htmlFor="bank-subject">
                            <select
                                id="bank-subject"
                                className="input-underline"
                                value={subject}
                                onChange={(e) => pickSubject(e.target.value)}
                            >
                                {SUBJECTS.map((name) => (
                                    <option key={name} value={name}>
                                        {name}
                                    </option>
                                ))}
                            </select>
                        </Field>
                    </div>
                    <div className="flex-1">
                        <Field label="Which topics" htmlFor="bank-scope">
                            <select
                                id="bank-scope"
                                className="input-underline"
                                value={scope}
                                onChange={(e) => pickScope(e.target.value as BankScope)}
                            >
                                {SCOPES.map((s) => (
                                    <option key={s.value} value={s.value}>
                                        {s.label}
                                    </option>
                                ))}
                            </select>
                        </Field>
                    </div>
                    <div className="flex-1">
                        <Field label="Filter by unit" htmlFor="bank-unit">
                            <TextInput
                                id="bank-unit"
                                value={unit}
                                onChange={(e) => pickUnit(e.target.value)}
                                placeholder="e.g. Cells"
                            />
                        </Field>
                    </div>
                    <Button variant="ghost" onClick={() => pickUnit("")}>
                        Clear
                    </Button>
                </Card>
                {coverage.data && (
                    <p className="label-caps" aria-live="polite">
                        {coverage.data.subject}: {coverage.data.total} questions
                        across {coverage.data.units.length} units
                    </p>
                )}
                {coverage.isError && (
                    <p className="text-sm text-[var(--oq-ink-soft)]">
                        Coverage is unavailable right now.
                    </p>
                )}
                {search.isPending && (
                    <div className="grid gap-4 md:grid-cols-2" aria-label="Loading questions">
                        <Skeleton className="h-36" />
                        <Skeleton className="h-36" />
                        <Skeleton className="h-36" />
                    </div>
                )}
                {search.isError && (
                    <Card className="p-4">
                        <EmptyState
                            title="Could not load questions"
                            hint="Check your connection and try again."
                            action={
                                <Button variant="secondary" onClick={() => search.refetch()}>
                                    Retry
                                </Button>
                            }
                        />
                    </Card>
                )}
                {search.data && results.length === 0 && (
                    <Card className="p-4">
                        <EmptyState
                            title="No questions here yet"
                            hint="Try a different subject, or clear the unit filter."
                        />
                    </Card>
                )}
                {results.length > 0 && (
                    <div>
                        <p className="label-caps mb-3" aria-live="polite">
                            {results.length} question{results.length === 1 ? "" : "s"}
                        </p>
                        <ul className="grid gap-4 md:grid-cols-2">
                            {results.map((q: BankQuestionHeader) => (
                                <li key={q.id}>
                                    <Card className="p-6 flex flex-col gap-2 h-full">
                                        <button
                                            type="button"
                                            className="text-left flex flex-col gap-2 cursor-pointer"
                                            onClick={() =>
                                                setSelectedId(selectedId === q.id ? null : q.id)
                                            }
                                            aria-expanded={selectedId === q.id}
                                        >
                                            <span className="font-bold leading-snug">
                                                {q.stem}
                                            </span>
                                            <span className="flex flex-wrap gap-2">
                                                <Chip tone="neutral">{q.unit}</Chip>
                                                <Chip tone="accent">
                                                    {q.format.replace(/_/g, " ")}
                                                </Chip>
                                                <Chip tone="neutral">
                                                    {scopeLabel(q.scope)}
                                                </Chip>
                                            </span>
                                            <span className="text-sm text-[var(--oq-ink-soft)]">
                                                {selectedId === q.id ? "Hide details" : "Show details"}
                                            </span>
                                        </button>
                                    </Card>
                                </li>
                            ))}
                        </ul>
                    </div>
                )}
                {selectedId !== null && (
                    <Card className="p-6 flex flex-col gap-2" aria-live="polite">
                        {detail.isPending && <Skeleton className="h-20" />}
                        {detail.isError && (
                            <EmptyState
                                title="Could not load that question"
                                hint="Check your connection and try again."
                                action={
                                    <Button variant="secondary" onClick={() => detail.refetch()}>
                                        Retry
                                    </Button>
                                }
                            />
                        )}
                        {detail.data && (
                            <div className="flex flex-col gap-2">
                                <p className="label-caps">Question details</p>
                                <p className="font-bold leading-snug">{detail.data.stem}</p>
                                <p className="text-sm text-[var(--oq-ink-soft)]">
                                    {detail.data.subject} · {detail.data.unit} ·{" "}
                                    {detail.data.topic} · {scopeLabel(detail.data.scope)}
                                </p>
                                <p className="text-sm text-[var(--oq-ink-soft)]">
                                    Reference: {detail.data.id}
                                </p>
                            </div>
                        )}
                    </Card>
                )}
            </div>
        </Shell>
    );
}
