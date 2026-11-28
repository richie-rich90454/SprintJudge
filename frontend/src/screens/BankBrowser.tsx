import { useI18n } from "../i18n";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Shell } from "../components/Shell";
import { Card } from "../ui/Card";
import { Button } from "../ui/Button";
import { TextInput } from "../ui/TextInput";
import { Chip, EmptyState, Field, Skeleton } from "../ui/Primitives";
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
    "C Programming": "C",
    "C++ Programming": "CPP",
    "JavaScript Programming": "JAVASCRIPT",
    "Discrete Mathematics": "MATH",
    "African American Studies": "AFRICAN_AMERICAN_STUDIES",
    "Computing Principles": "COMPUTING_PRINCIPLES",
    "Italian Language and Culture": "ITALIAN",
    "Japanese Language and Culture": "JAPANESE",
    Latin: "LATIN",
    "Organic Chemistry I": "ORGANIC_CHEMISTRY_I",
    "Organic Chemistry II": "ORGANIC_CHEMISTRY_II",
    "Physical Chemistry": "PHYSICAL_CHEMISTRY",
    "Analytical Chemistry": "ANALYTICAL_CHEMISTRY",
    Biochemistry: "BIOCHEMISTRY",
    "Linear Algebra": "LINEAR_ALGEBRA",
    "Differential Equations": "DIFFERENTIAL_EQUATIONS",
    "Discrete Mathematics II": "DISCRETE_MATHEMATICS_II",
    "Data Structures and Algorithms": "DATA_STRUCTURES",
    "Operating Systems": "OPERATING_SYSTEMS",
    "Computer Networks": "COMPUTER_NETWORKS",
    Databases: "DATABASES",
    "Artificial Intelligence and Machine Learning": "AI_ML",
    "Modern Physics": "MODERN_PHYSICS",
    Thermodynamics: "THERMODYNAMICS",
    "Personal Finance": "PERSONAL_FINANCE",
    "Business and Entrepreneurship": "BUSINESS_ENTREPRENEURSHIP",
    Accounting: "ACCOUNTING",
    Marketing: "MARKETING",
    "Introduction to Law": "INTRO_LAW",
    "Ethics and Philosophy": "ETHICS_PHILOSOPHY",
    "World Religions": "WORLD_RELIGIONS",
    Sociology: "SOCIOLOGY",
    Anthropology: "ANTHROPOLOGY",
    Astronomy: "ASTRONOMY",
    "Earth Science": "EARTH_SCIENCE",
    "Nutrition and Health": "NUTRITION_HEALTH",
    "Film Studies": "FILM_STUDIES",
    "Graphic Design": "GRAPHIC_DESIGN",
    "Arabic Language and Culture": "ARABIC",
    "Korean Language and Culture": "KOREAN",
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
    "C Programming",
    "C++ Programming",
    "JavaScript Programming",
    "Discrete Mathematics",
    "African American Studies",
    "Computing Principles",
    "Italian Language and Culture",
    "Japanese Language and Culture",
    "Latin",
    "Organic Chemistry I",
    "Organic Chemistry II",
    "Physical Chemistry",
    "Analytical Chemistry",
    "Biochemistry",
    "Linear Algebra",
    "Differential Equations",
    "Discrete Mathematics II",
    "Data Structures and Algorithms",
    "Operating Systems",
    "Computer Networks",
    "Databases",
    "Artificial Intelligence and Machine Learning",
    "Modern Physics",
    "Thermodynamics",
    "Personal Finance",
    "Business and Entrepreneurship",
    "Accounting",
    "Marketing",
    "Introduction to Law",
    "Ethics and Philosophy",
    "World Religions",
    "Sociology",
    "Anthropology",
    "Astronomy",
    "Earth Science",
    "Nutrition and Health",
    "Film Studies",
    "Graphic Design",
    "Arabic Language and Culture",
    "Korean Language and Culture",
];

const SCOPES: { value: BankScope; label: string }[] = [
    { value: "everything", label: "Everything" },
    { value: "current-scope", label: "Current topics" },
    { value: "legacy-scope", label: "Older topics" },
    { value: "core-scope", label: "Core topics" },
];

function scopeLabel(scope: string, t: (key: string) => string): string {
    return t(SCOPES.find((s) => s.value === scope)?.label ?? scope);
}

export function BankBrowser() {
    const { t } = useI18n();
    const [subject, setSubject] = useState(SUBJECTS[0]);
    const [scope, setScope] = useState<BankScope>("everything");
    const [unit, setUnit] = useState("");
    const [limit, setLimit] = useState(24);
    const [selectedId, setSelectedId] = useState<string | null>(null);

    const needle = unit.trim();
    const code = SUBJECT_CODES[subject];
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
        setLimit(24);
        setSelectedId(null);
    };
    const pickScope = (next: BankScope) => {
        setScope(next);
        setLimit(24);
        setSelectedId(null);
    };
    const pickUnit = (next: string) => {
        setUnit(next);
        setLimit(24);
        setSelectedId(null);
    };

    const results = search.data ?? [];

    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6">
                <div className="max-w-2xl">
                    <p className="label-caps mb-3">{t("ui.teacherBank")}</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">
                        {t("ui.browseTheQuestionBank")}
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        {t(
                            "ui.pickASubjectToSeeWhichQuestionsAreReadyForClassTitlesOnlyAnswersStayHidden",
                        )}
                    </p>
                </div>
                <Card className="p-6 flex flex-col gap-5 md:flex-row md:items-end">
                    <div className="flex-1">
                        <Field label={t("ui.subject")} htmlFor="bank-subject">
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
                        <Field label={t("ui.whichTopics")} htmlFor="bank-scope">
                            <select
                                id="bank-scope"
                                className="input-underline"
                                value={scope}
                                onChange={(e) => pickScope(e.target.value as BankScope)}
                            >
                                {SCOPES.map((s) => (
                                    <option key={s.value} value={s.value}>
                                        {t(s.label)}
                                    </option>
                                ))}
                            </select>
                        </Field>
                    </div>
                    <div className="flex-1">
                        <Field label={t("ui.filterByUnit")} htmlFor="bank-unit">
                            <TextInput
                                id="bank-unit"
                                value={unit}
                                onChange={(e) => pickUnit(e.target.value)}
                                placeholder={t("ui.egCells")}
                            />
                        </Field>
                    </div>
                    <Button variant="ghost" onClick={() => pickUnit("")}>
                        {t("ui.clear")}
                    </Button>
                </Card>
                {coverage.data && (
                    <p className="label-caps" aria-live="polite">
                        {coverage.data.subject}: {coverage.data.total} questions across{" "}
                        {coverage.data.units.length} units
                    </p>
                )}
                {coverage.isError && (
                    <p className="text-sm text-[var(--oq-ink-soft)]">
                        {t("ui.coverageIsUnavailableRightNow")}
                    </p>
                )}
                {search.isPending && (
                    <div
                        className="grid gap-4 md:grid-cols-2"
                        role="status"
                        aria-label={t("ui.loadingQuestions")}
                    >
                        <Skeleton className="h-36" />
                        <Skeleton className="h-36" />
                        <Skeleton className="h-36" />
                    </div>
                )}
                {search.isError && (
                    <Card className="p-4">
                        <EmptyState
                            title={t("ui.couldNotLoadQuestions")}
                            hint={t("ui.checkYourConnectionAndTryAgain")}
                            action={
                                <Button variant="secondary" onClick={() => search.refetch()}>
                                    {t("ui.retry")}
                                </Button>
                            }
                        />
                    </Card>
                )}
                {search.data && results.length === 0 && (
                    <Card className="p-4">
                        <EmptyState
                            title={t("ui.noQuestionsHereYet")}
                            hint={t("ui.tryADifferentSubjectOrClearTheUnitFilter")}
                        />
                    </Card>
                )}
                {results.length > 0 && (
                    <div>
                        <p className="label-caps mb-3" aria-live="polite">
                            {results.length} question{results.length === 1 ? "" : "s"}
                        </p>
                        <ul className="grid gap-4 md:grid-cols-2">
                            {results.slice(0, limit).map((q: BankQuestionHeader) => (
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
                                            <span className="font-bold leading-snug">{q.stem}</span>
                                            <span className="flex flex-wrap gap-2">
                                                <Chip tone="neutral">{q.unit}</Chip>
                                                <Chip tone="accent">
                                                    {q.format.replace(/_/g, " ")}
                                                </Chip>
                                                <Chip tone="neutral">{scopeLabel(q.scope, t)}</Chip>
                                            </span>
                                            <span className="text-sm text-[var(--oq-ink-soft)]">
                                                {selectedId === q.id
                                                    ? t("ui.hideDetails")
                                                    : t("ui.showDetails")}
                                            </span>
                                        </button>
                                    </Card>
                                </li>
                            ))}
                        </ul>
                        {results.length > limit && (
                            <div className="mt-4 flex justify-center">
                                <Button variant="secondary" onClick={() => setLimit((n) => n + 24)}>
                                    Show more ({results.length - limit} remaining)
                                </Button>
                            </div>
                        )}
                    </div>
                )}
                {selectedId !== null && (
                    <Card className="p-6 flex flex-col gap-2" aria-live="polite">
                        {detail.isPending && <Skeleton className="h-20" />}
                        {detail.isError && (
                            <EmptyState
                                title={t("ui.couldNotLoadThatQuestion")}
                                hint={t("ui.checkYourConnectionAndTryAgain")}
                                action={
                                    <Button variant="secondary" onClick={() => detail.refetch()}>
                                        {t("ui.retry")}
                                    </Button>
                                }
                            />
                        )}
                        {detail.data && (
                            <div className="flex flex-col gap-2">
                                <p className="label-caps">{t("ui.questionDetails")}</p>
                                <p className="font-bold leading-snug">{detail.data.stem}</p>
                                <p className="text-sm text-[var(--oq-ink-soft)]">
                                    {detail.data.subject}, {detail.data.unit}, {detail.data.topic},{" "}
                                    {scopeLabel(detail.data.scope, t)}
                                </p>
                                <p className="text-sm text-[var(--oq-ink-soft)]">
                                    Reference: <span className="mono">{detail.data.id}</span>
                                </p>
                            </div>
                        )}
                    </Card>
                )}
            </div>
        </Shell>
    );
}
