import { useI18n } from "../i18n";
import { useQuery } from "@tanstack/react-query";
import { Shell } from "../components/Shell";
import { Card } from "../ui/Card";
import { Button } from "../ui/Button";
import { Chip, EmptyState, Skeleton } from "../ui/Primitives";
import { getBankVolume } from "../services/BankApiService";

// ponytail: copied from BankBrowser so this view stays standalone.
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

const CODE_TO_NAME: Record<string, string> = Object.fromEntries(
    Object.entries(SUBJECT_CODES).map(([name, code]) => [code, name]),
);

const GOAL = 3000;

function formatCount(n: number): string {
    return n.toLocaleString("en-US");
}

interface VolumeRow {
    code: string;
    name: string;
    count: number;
}

export function VolumeReport() {
    const { t } = useI18n();
    const volume = useQuery({
        queryKey: ["bank-volume"],
        queryFn: getBankVolume,
    });

    const rows: VolumeRow[] = Object.entries(volume.data ?? {})
        .map(([code, count]) => ({
            code,
            name: CODE_TO_NAME[code] ?? code,
            count,
        }))
        .sort((a, b) => a.name.localeCompare(b.name));

    const total = rows.reduce((sum, r) => sum + r.count, 0);
    const ready = rows.filter((r) => r.count >= GOAL).length;

    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6">
                <div className="max-w-2xl">
                    <p className="label-caps mb-3">{t("ui.admin")}</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">
                        {t("ui.howManyQuestionsAreReady")}
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        {t("ui.eachSubjectNeedsAtLeast3000QuestionsBeforeItCanBeUsedInClassThisPageShowsWhereEachSubjectStands")}
                    </p>
                </div>
                {volume.isPending && (
                    <div className="flex flex-col gap-4" aria-label={t("ui.loadingQuestionCounts")}>
                        <Skeleton className="h-10" />
                        <Skeleton className="h-64" />
                    </div>
                )}
                {volume.isError && (
                    <Card className="p-4">
                        <EmptyState
                            title={t("ui.couldNotLoadQuestionCounts")}
                            hint={t("ui.checkYourConnectionAndTryAgain")}
                            action={
                                <Button variant="secondary" onClick={() => volume.refetch()}>
                                    {t("ui.retry")}
                                </Button>
                            }
                        />
                    </Card>
                )}
                {volume.data && rows.length === 0 && (
                    <Card className="p-4">
                        <EmptyState
                            title={t("ui.noSubjectsToShowYet")}
                            hint={t("ui.newSubjectsWillAppearHereOnceQuestionsAreAdded")}
                        />
                    </Card>
                )}
                {volume.data && rows.length > 0 && (
                    <>
                        <p className="label-caps" aria-live="polite">
                            {formatCount(total)} questions in total, {ready} of{" "}
                            {rows.length} subjects at 3,000
                        </p>
                        <Card className="p-0 overflow-x-auto">
                            <table className="table-dotted w-full text-sm">
                                <thead>
                                    <tr>
                                        <th scope="col" className="p-4">
                                            {t("ui.subject")}
                                        </th>
                                        <th scope="col" className="p-4">
                                            {t("ui.readyNow")}
                                        </th>
                                        <th scope="col" className="p-4">
                                            {t("ui.progressTo3000")}
                                        </th>
                                        <th scope="col" className="p-4">
                                            {t("ui.status")}
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {rows.map((row) => (
                                        <tr key={row.code}>
                                            <td className="p-4 font-bold leading-snug">
                                                {row.name}
                                            </td>
                                            <td className="p-4">{formatCount(row.count)}</td>
                                            <td className="p-4 text-[var(--oq-ink-soft)]">
                                                {formatCount(row.count)} / {formatCount(GOAL)}
                                            </td>
                                            <td className="p-4">
                                                {row.count >= GOAL ? (
                                                    <Chip tone="success">{t("ui.ready")}</Chip>
                                                ) : (
                                                    <Chip tone="neutral">{t("ui.stillAdding")}</Chip>
                                                )}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </Card>
                    </>
                )}
            </div>
        </Shell>
    );
}
