import { useI18n } from "../i18n";
import { useRef, useState, type KeyboardEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Shell } from "../components/Shell";
import { Card } from "../ui/Card";
import { Button } from "../ui/Button";
import { TextInput } from "../ui/TextInput";
import { Chip, EmptyState, Field, Skeleton } from "../ui/Primitives";
import {
    assignmentApi,
    type Assignment,
    type StudentProgress,
} from "../services/AssignmentApiService";

function errorText(error: unknown, t: (key: string) => string): string {
    return error instanceof Error ? error.message : t("ui.somethingWentWrong");
}

function toEpoch(local: string): number | null {
    if (!local.trim()) return null;
    const ms = new Date(local).getTime();
    return Number.isFinite(ms) ? Math.floor(ms / 1000) : null;
}

function formatTime(seconds: number | null): string {
    return seconds == null ? "-" : new Date(seconds * 1000).toLocaleString();
}

function tone(status: StudentProgress["status"]): "accent" | "neutral" | "success" | "danger" {
    if (status === "GRADED") return "success";
    if (status === "IN_PROGRESS") return "accent";
    if (status === "SUBMITTED") return "neutral";
    return "danger";
}

/** Admin screen: list assignments, create one, import a roster, track progress. */
export function AssignmentView() {
    const { t } = useI18n();
    const queryClient = useQueryClient();
    const listTabRef = useRef<HTMLButtonElement>(null);
    const createTabRef = useRef<HTMLButtonElement>(null);
    const [tab, setTab] = useState<"list" | "create">("list");
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [quizId, setQuizId] = useState("");
    const [title, setTitle] = useState("");
    const [description, setDescription] = useState("");
    const [release, setRelease] = useState("");
    const [due, setDue] = useState("");
    const [csv, setCsv] = useState("");
    const [grades, setGrades] = useState<Record<string, string>>({});
    const [studentId, setStudentId] = useState("");

    const list = useQuery({ queryKey: ["assignments"], queryFn: assignmentApi.list });
    const progress = useQuery({
        queryKey: ["assignment-progress", selectedId],
        queryFn: () => assignmentApi.progress(selectedId ?? ""),
        enabled: selectedId !== null,
    });
    const roster = useQuery({
        queryKey: ["assignment-roster", selectedId],
        queryFn: () => assignmentApi.roster(selectedId ?? ""),
        enabled: selectedId !== null,
    });

    const refresh = () => {
        void queryClient.invalidateQueries({ queryKey: ["assignments"] });
        void queryClient.invalidateQueries({ queryKey: ["assignment-progress", selectedId] });
        void queryClient.invalidateQueries({ queryKey: ["assignment-roster", selectedId] });
    };

    const create = useMutation({
        mutationFn: () =>
            assignmentApi.create({
                quizId: quizId.trim(),
                title: title.trim(),
                description: description.trim() || undefined,
                releaseAt: toEpoch(release),
                dueAt: toEpoch(due),
            }),
        onSuccess: (created) => {
            setTab("list");
            setSelectedId(created.id);
            setQuizId("");
            setTitle("");
            setDescription("");
            setRelease("");
            setDue("");
            refresh();
        },
    });

    const importCsv = useMutation({
        mutationFn: () => assignmentApi.importRoster(selectedId ?? "", csv),
        onSuccess: () => {
            setCsv("");
            refresh();
        },
    });

    const grade = useMutation({
        mutationFn: (input: { studentId: string; score: number; maxScore: number }) =>
            assignmentApi.recordGrade(
                selectedId ?? "",
                input.studentId,
                input.score,
                input.maxScore,
            ),
        onSuccess: refresh,
    });

    const start = useMutation({
        mutationFn: () => assignmentApi.start(selectedId ?? "", studentId.trim()),
        onSuccess: refresh,
    });

    const rows = progress.data ?? [];
    const points = rows.find((row) => row.maxScore != null)?.maxScore ?? 100;

    function onTabKeyDown(event: KeyboardEvent<HTMLDivElement>) {
        const { key } = event;
        if (!["ArrowRight", "ArrowLeft", "ArrowUp", "ArrowDown", "Home", "End"].includes(key))
            return;
        event.preventDefault();
        const next =
            key === "Home" ? "list" : key === "End" ? "create" : tab === "list" ? "create" : "list";
        setTab(next);
        (next === "list" ? listTabRef : createTabRef).current?.focus();
    }

    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6">
                <div className="max-w-2xl">
                    <p className="label-caps mb-3">{t("ui.asyncAssignments")}</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">
                        {t("ui.assignments")}
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        {t("ui.scheduleAQuizImportARosterAndFollowEachStudentsProgress")}
                    </p>
                </div>

                <div
                    role="tablist"
                    aria-label={t("ui.assignmentViews")}
                    className="flex gap-2"
                    onKeyDown={onTabKeyDown}
                >
                    <Button
                        ref={listTabRef}
                        id="assignment-list-tab"
                        role="tab"
                        aria-selected={tab === "list"}
                        aria-controls="assignment-list-panel"
                        tabIndex={tab === "list" ? 0 : -1}
                        variant={tab === "list" ? "primary" : "ghost"}
                        onClick={() => setTab("list")}
                    >
                        {t("ui.allAssignments")}
                    </Button>
                    <Button
                        ref={createTabRef}
                        id="assignment-create-tab"
                        role="tab"
                        aria-selected={tab === "create"}
                        aria-controls="assignment-create-panel"
                        tabIndex={tab === "create" ? 0 : -1}
                        variant={tab === "create" ? "primary" : "ghost"}
                        onClick={() => setTab("create")}
                    >
                        {t("ui.newAssignment")}
                    </Button>
                </div>

                {tab === "create" && (
                    <Card
                        id="assignment-create-panel"
                        role="tabpanel"
                        aria-labelledby="assignment-create-tab"
                        className="p-6 flex flex-col gap-4"
                    >
                        <Field label={t("ui.quiz")} htmlFor="assignment-quiz">
                            <TextInput
                                id="assignment-quiz"
                                value={quizId}
                                onChange={(e) => setQuizId(e.target.value)}
                                placeholder={t("ui.quizId")}
                            />
                        </Field>
                        <Field label={t("ui.title")} htmlFor="assignment-title">
                            <TextInput
                                id="assignment-title"
                                value={title}
                                onChange={(e) => setTitle(e.target.value)}
                                placeholder={t("ui.egWeek3Practice")}
                            />
                        </Field>
                        <Field label={t("ui.description")} htmlFor="assignment-description">
                            <textarea
                                id="assignment-description"
                                className="input-underline"
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                rows={3}
                            />
                        </Field>
                        <div className="grid gap-4 md:grid-cols-2">
                            <Field label={t("ui.releaseAt")} htmlFor="assignment-release">
                                <TextInput
                                    id="assignment-release"
                                    type="datetime-local"
                                    value={release}
                                    onChange={(e) => setRelease(e.target.value)}
                                />
                            </Field>
                            <Field label={t("ui.dueAt")} htmlFor="assignment-due">
                                <TextInput
                                    id="assignment-due"
                                    type="datetime-local"
                                    value={due}
                                    onChange={(e) => setDue(e.target.value)}
                                />
                            </Field>
                        </div>
                        {create.isError && (
                            <p className="text-sm text-red-600">{errorText(create.error, t)}</p>
                        )}
                        <div>
                            <Button
                                onClick={() => create.mutate()}
                                disabled={create.isPending || !quizId.trim() || !title.trim()}
                            >
                                {create.isPending
                                    ? t("ui.creatingEllipsis")
                                    : t("ui.createAssignment")}
                            </Button>
                        </div>
                    </Card>
                )}

                {tab === "list" && (
                    <div
                        id="assignment-list-panel"
                        role="tabpanel"
                        aria-labelledby="assignment-list-tab"
                    >
                        {list.isPending && (
                            <div
                                className="grid gap-4 md:grid-cols-2"
                                role="status"
                                aria-label={t("ui.loadingAssignments")}
                            >
                                <Skeleton className="h-28" />
                                <Skeleton className="h-28" />
                            </div>
                        )}
                        {list.isError && (
                            <Card className="p-4">
                                <EmptyState
                                    title={t("ui.couldNotLoadAssignments")}
                                    hint={errorText(list.error, t)}
                                    action={
                                        <Button variant="secondary" onClick={() => list.refetch()}>
                                            {t("ui.retry")}
                                        </Button>
                                    }
                                />
                            </Card>
                        )}
                        {list.data?.length === 0 && (
                            <Card className="p-4">
                                <EmptyState
                                    title={t("ui.noAssignmentsYet")}
                                    hint={t("ui.createOneFromAnExistingQuizToGetStarted")}
                                />
                            </Card>
                        )}
                        {list.data && list.data.length > 0 && (
                            <ul className="grid gap-4 md:grid-cols-2">
                                {list.data.map((item: Assignment) => (
                                    <li key={item.id}>
                                        <Card className="p-5 flex flex-col gap-2">
                                            <button
                                                type="button"
                                                className="text-left font-bold leading-snug cursor-pointer"
                                                onClick={() =>
                                                    setSelectedId(
                                                        selectedId === item.id ? null : item.id,
                                                    )
                                                }
                                                aria-expanded={selectedId === item.id}
                                            >
                                                {item.title}
                                            </button>
                                            <span className="flex flex-wrap gap-2">
                                                <Chip tone="neutral">
                                                    Quiz {item.quizId.slice(0, 8)}
                                                </Chip>
                                                <Chip tone="accent">
                                                    Releases {formatTime(item.releaseAt)}
                                                </Chip>
                                                <Chip tone="neutral">
                                                    Due {formatTime(item.dueAt)}
                                                </Chip>
                                            </span>
                                        </Card>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>
                )}

                {selectedId !== null && (
                    <div className="flex flex-col gap-4" aria-live="polite">
                        <Card className="p-6 flex flex-col gap-3">
                            <p className="label-caps">{t("ui.rosterImport")}</p>
                            <textarea
                                className="input-underline"
                                aria-label={t("ui.rosterCSV")}
                                rows={4}
                                value={csv}
                                onChange={(e) => setCsv(e.target.value)}
                                placeholder={t("ui.rosterExample")}
                            />
                            {importCsv.isError && (
                                <p className="text-sm text-red-600">
                                    {errorText(importCsv.error, t)}
                                </p>
                            )}
                            <div className="flex items-center gap-3">
                                <Button
                                    variant="secondary"
                                    disabled={importCsv.isPending || !csv.trim()}
                                    onClick={() => importCsv.mutate()}
                                >
                                    {importCsv.isPending
                                        ? t("ui.importingEllipsis")
                                        : t("ui.importCSV")}
                                </Button>
                                <span className="text-sm text-[var(--oq-ink-soft)]">
                                    {roster.isError
                                        ? t("ui.couldNotLoadRoster")
                                        : `${roster.data?.length ?? 0} students in roster`}
                                </span>
                            </div>
                        </Card>

                        <Card className="p-6 flex flex-col gap-3">
                            <p className="label-caps">{t("ui.studentProgress")}</p>
                            {progress.isPending && <Skeleton className="h-20" />}
                            {progress.isError && (
                                <EmptyState
                                    title={t("ui.couldNotLoadStudentProgress")}
                                    hint={t("ui.checkYourConnectionAndTryAgain")}
                                    action={
                                        <Button
                                            variant="secondary"
                                            onClick={() => progress.refetch()}
                                        >
                                            {t("ui.retry")}
                                        </Button>
                                    }
                                />
                            )}
                            {!progress.isPending && !progress.isError && rows.length === 0 && (
                                <p className="text-sm text-[var(--oq-ink-soft)]">
                                    {t("ui.noProgressYet")}
                                </p>
                            )}
                            {rows.map((row) => {
                                const draft = grades[row.studentId] ?? String(row.score ?? points);
                                return (
                                    <div
                                        key={row.id || row.studentId}
                                        className="flex flex-wrap items-center gap-3 border-b border-[var(--oq-line)] py-2"
                                    >
                                        <span className="font-semibold min-w-32">
                                            {row.studentId}
                                        </span>
                                        <Chip tone={tone(row.status)}>
                                            {row.status.replace(/_/g, " ")}
                                        </Chip>
                                        <TextInput
                                            aria-label={t("ui.scoreFor", {
                                                student: row.studentId,
                                            })}
                                            className="w-20"
                                            type="number"
                                            min={0}
                                            max={points}
                                            value={draft}
                                            onChange={(e) =>
                                                setGrades((current) => ({
                                                    ...current,
                                                    [row.studentId]: e.target.value,
                                                }))
                                            }
                                        />
                                        <span className="text-sm text-[var(--oq-ink-soft)]">
                                            / {row.maxScore ?? points}
                                        </span>
                                        <Button
                                            size="sm"
                                            variant="secondary"
                                            disabled={grade.isPending}
                                            onClick={() =>
                                                grade.mutate({
                                                    studentId: row.studentId,
                                                    score: Number(draft),
                                                    maxScore: row.maxScore ?? points,
                                                })
                                            }
                                        >
                                            {t("ui.saveGrade")}
                                        </Button>
                                    </div>
                                );
                            })}
                            {grade.isError && (
                                <p className="text-sm text-red-600">{errorText(grade.error, t)}</p>
                            )}
                        </Card>

                        <Card className="p-6 flex flex-col gap-3">
                            <p className="label-caps">{t("ui.studentStartCheck")}</p>
                            <div className="flex flex-wrap items-end gap-3">
                                <Field label={t("ui.studentId")} htmlFor="assignment-student">
                                    <TextInput
                                        id="assignment-student"
                                        value={studentId}
                                        onChange={(e) => setStudentId(e.target.value)}
                                        placeholder={t("ui.studentExample")}
                                    />
                                </Field>
                                <Button
                                    variant="secondary"
                                    disabled={start.isPending || !studentId.trim()}
                                    onClick={() => start.mutate()}
                                >
                                    {t("ui.startAssignment")}
                                </Button>
                            </div>
                            {start.isError && (
                                <p className="text-sm text-red-600">{errorText(start.error, t)}</p>
                            )}
                            {start.data && (
                                <p className="text-sm text-[var(--oq-ink-soft)]">
                                    {start.data.studentId}: {start.data.status}
                                </p>
                            )}
                        </Card>
                    </div>
                )}
            </div>
        </Shell>
    );
}
