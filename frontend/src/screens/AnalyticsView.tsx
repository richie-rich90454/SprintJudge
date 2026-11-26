import { useI18n } from "../i18n";
import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Shell } from "../components/Shell";
import { Card } from "../ui/Card";
import { Button } from "../ui/Button";
import { TextInput } from "../ui/TextInput";
import { Chip, EmptyState, Field, Skeleton } from "../ui/Primitives";
import { getBankVolume } from "../services/BankApiService";
import {
    buildAdaptiveSet,
    getClassReport,
    getItemReports,
    getStudentReports,
    type AdaptiveMode,
    type AdaptiveSetResult,
} from "../services/AnalyticsApiService";

/** Small CSS bar: no chart dependency in this project. */
function Bar({ value, tone = "accent" }: { value: number; tone?: "accent" | "neutral" }) {
    const width = Math.round(Math.max(0, Math.min(1, value)) * 100);
    return (
        <div className="h-2 w-full rounded-full bg-[var(--oq-surface)]" aria-hidden="true">
            <div
                className={`h-2 rounded-full ${tone === "accent" ? "bg-[var(--oq-accent)]" : "bg-[var(--oq-ink-soft)]"}`}
                style={{ width: `${width}%` }}
            />
        </div>
    );
}

function pct(value: number): string {
    return `${Math.round(value * 100)}%`;
}

export function AnalyticsView() {
    const { t } = useI18n();
    const [sessionId, setSessionId] = useState("");
    const [appliedSessionId, setAppliedSessionId] = useState("");
    const [subject, setSubject] = useState("");
    const [size, setSize] = useState(12);
    const [mode, setMode] = useState<AdaptiveMode>("balanced");
    const [target, setTarget] = useState(0.5);
    const [title, setTitle] = useState("");

    const volume = useQuery({ queryKey: ["bank-volume"], queryFn: getBankVolume });
    const subjects = Object.keys(volume.data ?? {}).sort((a, b) => a.localeCompare(b));
    const activeSubject = subject || subjects[0] || "";

    const students = useQuery({
        queryKey: ["analytics-students", appliedSessionId],
        queryFn: () => getStudentReports(appliedSessionId),
    });
    const classes = useQuery({
        queryKey: ["analytics-classes", appliedSessionId],
        queryFn: () => getClassReport(appliedSessionId),
    });
    const items = useQuery({
        queryKey: ["analytics-items", activeSubject],
        queryFn: () => getItemReports(activeSubject),
        enabled: Boolean(activeSubject),
    });

    const build = useMutation({
        mutationFn: buildAdaptiveSet,
    });

    function createSet() {
        if (!activeSubject) return;
        build.mutate({
            subject: activeSubject,
            size,
            mode,
            targetDifficulty: target,
            title: title.trim() || undefined,
        });
    }

    const result: AdaptiveSetResult | undefined = build.data;

    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6">
                <div className="max-w-2xl">
                    <p className="label-caps mb-3">{t("ui.admin")}</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">
                        {t("ui.howIsTheClassDoing")}
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        {t(
                            "ui.accuracyComesFromSubmittedAnswersDifficultyDiscriminationAndTimingBaselinesComeFromTheQuestionBank",
                        )}
                    </p>
                </div>

                <Card className="p-5">
                    <div className="flex flex-col gap-4 md:flex-row md:items-end">
                        <div className="flex-1">
                            <Field label={t("ui.sessionIdOptional")} htmlFor="analytics-session">
                                <TextInput
                                    id="analytics-session"
                                    value={sessionId}
                                    placeholder={t("ui.leaveBlankForAllSessions")}
                                    onChange={(e) => setSessionId(e.target.value)}
                                />
                            </Field>
                        </div>
                        <Button variant="secondary" onClick={() => setAppliedSessionId(sessionId)}>
                            {t("ui.applyFilter")}
                        </Button>
                    </div>
                </Card>

                <section className="flex flex-col gap-3" aria-label={t("ui.classSummary")}>
                    <h2 className="text-xl font-extrabold">{t("ui.classSummary")}</h2>
                    {classes.isPending && <Skeleton className="h-32" />}
                    {classes.isError && (
                        <Card className="p-4">
                            <EmptyState
                                title={t("ui.couldNotLoadTheClassSummary")}
                                hint={t("ui.checkYourConnectionAndTryAgain")}
                                action={
                                    <Button variant="secondary" onClick={() => classes.refetch()}>
                                        {t("ui.retry")}
                                    </Button>
                                }
                            />
                        </Card>
                    )}
                    {classes.data && (
                        <Card className="p-5 flex flex-col gap-4">
                            <div className="flex flex-wrap gap-3">
                                <Chip tone="accent">{classes.data.players} players</Chip>
                                <Chip>{classes.data.sessionCount} sessions</Chip>
                                <Chip>{classes.data.attempts} attempts</Chip>
                                <Chip>{classes.data.questions} questions</Chip>
                                <Chip tone="success">{pct(classes.data.accuracy)} correct</Chip>
                            </div>
                            <div className="grid gap-4 md:grid-cols-2">
                                <div>
                                    <p className="label-caps mb-2">{t("ui.overallAccuracy")}</p>
                                    <Bar value={classes.data.accuracy} />
                                </div>
                                <div>
                                    <p className="label-caps mb-2">{t("ui.averageScore")}</p>
                                    <p className="font-bold">{Math.round(classes.data.avgScore)}</p>
                                </div>
                            </div>
                            {(classes.data.sessionStats?.length ?? 0) > 0 && (
                                <div className="overflow-x-auto">
                                    <table className="table-dotted w-full text-sm">
                                        <thead>
                                            <tr>
                                                <th scope="col" className="p-3 text-left">
                                                    {t("ui.session")}
                                                </th>
                                                <th scope="col" className="p-3 text-left">
                                                    {t("ui.players")}
                                                </th>
                                                <th scope="col" className="p-3 text-left">
                                                    {t("ui.accuracy")}
                                                </th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {classes.data.sessionStats!.map((row) => (
                                                <tr key={row.sessionId}>
                                                    <td className="p-3 font-mono text-xs">
                                                        {row.sessionId}
                                                    </td>
                                                    <td className="p-3">{row.players}</td>
                                                    <td className="p-3">
                                                        <div className="flex items-center gap-2">
                                                            <Bar value={row.accuracy} />
                                                            <span className="w-10 text-right">
                                                                {pct(row.accuracy)}
                                                            </span>
                                                        </div>
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </Card>
                    )}
                </section>

                <section className="flex flex-col gap-3" aria-label={t("ui.studentReport")}>
                    <h2 className="text-xl font-extrabold">{t("ui.students")}</h2>
                    {students.isPending && <Skeleton className="h-32" />}
                    {students.isError && (
                        <Card className="p-4">
                            <EmptyState
                                title={t("ui.couldNotLoadTheStudentReport")}
                                hint={t("ui.checkYourConnectionAndTryAgain")}
                                action={
                                    <Button variant="secondary" onClick={() => students.refetch()}>
                                        {t("ui.retry")}
                                    </Button>
                                }
                            />
                        </Card>
                    )}
                    {students.data && students.data.length === 0 && (
                        <Card className="p-4">
                            <EmptyState
                                title={t("ui.noSubmissionsYet")}
                                hint={t("ui.reportsAppearAfterStudentsAnswer")}
                            />
                        </Card>
                    )}
                    {students.data && students.data.length > 0 && (
                        <Card className="p-0 overflow-x-auto">
                            <table className="table-dotted w-full text-sm">
                                <thead>
                                    <tr>
                                        <th scope="col" className="p-4 text-left">
                                            {t("ui.student")}
                                        </th>
                                        <th scope="col" className="p-4 text-left">
                                            {t("ui.attempts")}
                                        </th>
                                        <th scope="col" className="p-4 text-left">
                                            {t("ui.accuracy")}
                                        </th>
                                        <th scope="col" className="p-4 text-left">
                                            {t("ui.avgScore")}
                                        </th>
                                        <th scope="col" className="p-4 text-left">
                                            {t("ui.triesPerItem")}
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {students.data.map((row) => (
                                        <tr key={row.playerUuid}>
                                            <td className="p-4 font-bold">
                                                {row.playerName || row.playerUuid}
                                            </td>
                                            <td className="p-4">{row.attempts}</td>
                                            <td className="p-4">
                                                <div className="flex items-center gap-2">
                                                    <Bar value={row.accuracy} />
                                                    <span className="w-10 text-right">
                                                        {pct(row.accuracy)}
                                                    </span>
                                                </div>
                                            </td>
                                            <td className="p-4">{Math.round(row.avgScore)}</td>
                                            <td className="p-4">
                                                {row.avgAttemptsPerItem?.toFixed(1) ?? "-"}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </Card>
                    )}
                </section>

                <section className="flex flex-col gap-3" aria-label={t("ui.itemReport")}>
                    <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
                        <h2 className="text-xl font-extrabold">{t("ui.items")}</h2>
                        <div className="w-full md:w-64">
                            <Field label={t("ui.subject")} htmlFor="analytics-subject">
                                <select
                                    id="analytics-subject"
                                    className="input-underline"
                                    value={activeSubject}
                                    onChange={(e) => setSubject(e.target.value)}
                                >
                                    {subjects.map((code) => (
                                        <option key={code} value={code}>
                                            {code}
                                        </option>
                                    ))}
                                </select>
                            </Field>
                        </div>
                    </div>
                    {items.isPending && <Skeleton className="h-32" />}
                    {items.isError && (
                        <Card className="p-4">
                            <EmptyState
                                title={t("ui.couldNotLoadItemData")}
                                hint={t("ui.checkYourConnectionAndTryAgain")}
                                action={
                                    <Button variant="secondary" onClick={() => items.refetch()}>
                                        {t("ui.retry")}
                                    </Button>
                                }
                            />
                        </Card>
                    )}
                    {items.data && items.data.length === 0 && (
                        <Card className="p-4">
                            <EmptyState
                                title={t("ui.noItemDataYet")}
                                hint={t("ui.attemptedBankItemsAppearHere")}
                            />
                        </Card>
                    )}
                    {items.data && items.data.length > 0 && (
                        <Card className="p-0 overflow-x-auto">
                            <table className="table-dotted w-full text-sm">
                                <thead>
                                    <tr>
                                        <th scope="col" className="p-4 text-left">
                                            {t("ui.item")}
                                        </th>
                                        <th scope="col" className="p-4 text-left">
                                            {t("ui.attempts")}
                                        </th>
                                        <th scope="col" className="p-4 text-left">
                                            {t("ui.observedAuthoredDifficulty")}
                                        </th>
                                        <th scope="col" className="p-4 text-left">
                                            {t("ui.discrimination")}
                                        </th>
                                        <th scope="col" className="p-4 text-left">
                                            {t("ui.baselineS")}
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.data.slice(0, 60).map((row) => (
                                        <tr key={row.bankId}>
                                            <td className="p-4 font-bold leading-snug">
                                                {row.title}
                                                <span className="block text-xs text-[var(--oq-ink-soft)]">
                                                    {row.unit} / {row.bankId}
                                                </span>
                                            </td>
                                            <td className="p-4">{row.attempts}</td>
                                            <td className="p-4">
                                                <div className="flex items-center gap-2">
                                                    <Bar value={row.pValue} tone="neutral" />
                                                    <span className="w-16 text-right">
                                                        {pct(row.pValue)} /{" "}
                                                        {row.bankDifficulty === null
                                                            ? "-"
                                                            : pct(row.bankDifficulty)}
                                                    </span>
                                                </div>
                                            </td>
                                            <td className="p-4">
                                                {row.discrimination?.toFixed(2) ?? "-"}
                                            </td>
                                            <td className="p-4">
                                                {row.medianSec == null
                                                    ? "-"
                                                    : Math.round(row.medianSec)}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </Card>
                    )}
                </section>

                <section className="flex flex-col gap-3" aria-label={t("ui.setBuilder")}>
                    <h2 className="text-xl font-extrabold">{t("ui.buildAPracticeSet")}</h2>
                    <Card className="p-5 flex flex-col gap-4">
                        <div className="grid gap-4 md:grid-cols-4">
                            <Field label={t("ui.mode")} htmlFor="analytics-mode">
                                <select
                                    id="analytics-mode"
                                    className="input-underline"
                                    value={mode}
                                    onChange={(e) => setMode(e.target.value as AdaptiveMode)}
                                >
                                    <option value="balanced">{t("ui.balanced")}</option>
                                    <option value="adaptive">{t("ui.adaptive")}</option>
                                </select>
                            </Field>
                            <Field label={t("ui.questions")} htmlFor="analytics-size">
                                <TextInput
                                    id="analytics-size"
                                    type="number"
                                    min={1}
                                    max={30}
                                    value={size}
                                    onChange={(e) => setSize(Number(e.target.value))}
                                />
                            </Field>
                            <Field label={t("ui.targetDifficulty")} htmlFor="analytics-target">
                                <TextInput
                                    id="analytics-target"
                                    type="number"
                                    min={0}
                                    max={1}
                                    step={0.05}
                                    value={target}
                                    disabled={mode !== "adaptive"}
                                    onChange={(e) => setTarget(Number(e.target.value))}
                                />
                            </Field>
                            <Field label={t("ui.titleOptional")} htmlFor="analytics-title">
                                <TextInput
                                    id="analytics-title"
                                    value={title}
                                    onChange={(e) => setTitle(e.target.value)}
                                    placeholder={t("ui.practiceSet")}
                                />
                            </Field>
                        </div>
                        <div>
                            <Button
                                onClick={createSet}
                                disabled={!activeSubject || build.isPending}
                            >
                                {build.isPending ? t("ui.buildingDots") : t("ui.buildPlayableQuiz")}
                            </Button>
                        </div>
                        {build.isError && (
                            <p className="text-[var(--oq-danger)] text-sm" role="alert">
                                {build.error instanceof Error
                                    ? build.error.message
                                    : t("ui.buildFailed")}
                            </p>
                        )}
                        {result && (
                            <div className="flex flex-col gap-2" aria-live="polite">
                                <Chip tone="success">{result.questionCount} questions ready</Chip>
                                <p className="text-sm">
                                    {t("ui.quizIdLabel")}{" "}
                                    <span className="font-mono">{result.quizId}</span>
                                </p>
                                <p className="text-sm text-[var(--oq-ink-soft)]">
                                    Average difficulty {pct(result.avgDifficulty)}, discrimination{" "}
                                    {result.avgDiscrimination?.toFixed(2) ?? "-"}, {result.skipped}{" "}
                                    skipped.
                                </p>
                                <div className="flex flex-wrap gap-2">
                                    {Object.entries(result.difficultyBands ?? {}).map(
                                        ([band, count]) => (
                                            <Chip key={band}>
                                                {band}: {count}
                                            </Chip>
                                        ),
                                    )}
                                </div>
                            </div>
                        )}
                    </Card>
                </section>
            </div>
        </Shell>
    );
}
