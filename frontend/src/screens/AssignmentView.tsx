import { useRef, useState, type KeyboardEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Shell } from "../components/Shell";
import { Card } from "../ui/Card";
import { Button } from "../ui/Button";
import { TextInput } from "../ui/TextInput";
import { Chip, EmptyState, Field, Skeleton } from "../ui/Primitives";
import { assignmentApi, type Assignment, type StudentProgress } from "../services/AssignmentApiService";

function errorText(error: unknown): string {
    return error instanceof Error ? error.message : "Something went wrong";
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
            assignmentApi.recordGrade(selectedId ?? "", input.studentId, input.score, input.maxScore),
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
        if (!["ArrowRight", "ArrowLeft", "ArrowUp", "ArrowDown", "Home", "End"].includes(key)) return;
        event.preventDefault();
        const next = key === "Home" ? "list" : key === "End" ? "create" : tab === "list" ? "create" : "list";
        setTab(next);
        (next === "list" ? listTabRef : createTabRef).current?.focus();
    }

    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6">
                <div className="max-w-2xl">
                    <p className="label-caps mb-3">Async assignments</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">Assignments</h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        Schedule a quiz, import a roster, and follow each student&apos;s progress.
                    </p>
                </div>

                <div role="tablist" aria-label="Assignment views" className="flex gap-2" onKeyDown={onTabKeyDown}>
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
                        All assignments
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
                        New assignment
                    </Button>
                </div>

                {tab === "create" && (
                    <Card id="assignment-create-panel" role="tabpanel" aria-labelledby="assignment-create-tab"
                        className="p-6 flex flex-col gap-4">
                        <Field label="Quiz" htmlFor="assignment-quiz">
                            <TextInput
                                id="assignment-quiz"
                                value={quizId}
                                onChange={(e) => setQuizId(e.target.value)}
                                placeholder="Quiz id"
                            />
                        </Field>
                        <Field label="Title" htmlFor="assignment-title">
                            <TextInput
                                id="assignment-title"
                                value={title}
                                onChange={(e) => setTitle(e.target.value)}
                                placeholder="e.g. Week 3 practice"
                            />
                        </Field>
                        <Field label="Description" htmlFor="assignment-description">
                            <textarea
                                id="assignment-description"
                                className="input-underline"
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                rows={3}
                            />
                        </Field>
                        <div className="grid gap-4 md:grid-cols-2">
                            <Field label="Release at" htmlFor="assignment-release">
                                <TextInput
                                    id="assignment-release"
                                    type="datetime-local"
                                    value={release}
                                    onChange={(e) => setRelease(e.target.value)}
                                />
                            </Field>
                            <Field label="Due at" htmlFor="assignment-due">
                                <TextInput
                                    id="assignment-due"
                                    type="datetime-local"
                                    value={due}
                                    onChange={(e) => setDue(e.target.value)}
                                />
                            </Field>
                        </div>
                        {create.isError && <p className="text-sm text-red-600">{errorText(create.error)}</p>}
                        <div>
                            <Button
                                onClick={() => create.mutate()}
                                disabled={create.isPending || !quizId.trim() || !title.trim()}
                            >
                                {create.isPending ? "Creating…" : "Create assignment"}
                            </Button>
                        </div>
                    </Card>
                )}

                {tab === "list" && (
                    <div id="assignment-list-panel" role="tabpanel" aria-labelledby="assignment-list-tab">
                        {list.isPending && (
                            <div className="grid gap-4 md:grid-cols-2" role="status" aria-label="Loading assignments">
                                <Skeleton className="h-28" />
                                <Skeleton className="h-28" />
                            </div>
                        )}
                        {list.isError && (
                            <Card className="p-4">
                                <EmptyState
                                    title="Could not load assignments"
                                    hint={errorText(list.error)}
                                    action={<Button variant="secondary" onClick={() => list.refetch()}>Retry</Button>}
                                />
                            </Card>
                        )}
                        {list.data?.length === 0 && (
                            <Card className="p-4">
                                <EmptyState
                                    title="No assignments yet"
                                    hint="Create one from an existing quiz to get started."
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
                                                    setSelectedId(selectedId === item.id ? null : item.id)
                                                }
                                                aria-expanded={selectedId === item.id}
                                            >
                                                {item.title}
                                            </button>
                                            <span className="flex flex-wrap gap-2">
                                                <Chip tone="neutral">Quiz {item.quizId.slice(0, 8)}</Chip>
                                                <Chip tone="accent">Releases {formatTime(item.releaseAt)}</Chip>
                                                <Chip tone="neutral">Due {formatTime(item.dueAt)}</Chip>
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
                            <p className="label-caps">Roster import</p>
                            <textarea
                                className="input-underline"
                                aria-label="Roster CSV"
                                rows={4}
                                value={csv}
                                onChange={(e) => setCsv(e.target.value)}
                                placeholder="student_id,name,email&#10;s001,Ada Lovelace,ada@example.org"
                            />
                            {importCsv.isError && <p className="text-sm text-red-600">{errorText(importCsv.error)}</p>}
                            <div className="flex items-center gap-3">
                                <Button
                                    variant="secondary"
                                    disabled={importCsv.isPending || !csv.trim()}
                                    onClick={() => importCsv.mutate()}
                                >
                                    {importCsv.isPending ? "Importing…" : "Import CSV"}
                                </Button>
                                <span className="text-sm text-[var(--oq-ink-soft)]">
                                    {roster.isError
                                        ? "Could not load roster"
                                        : `${roster.data?.length ?? 0} students in roster`}
                                </span>
                            </div>
                        </Card>

                        <Card className="p-6 flex flex-col gap-3">
                            <p className="label-caps">Student progress</p>
                            {progress.isPending && <Skeleton className="h-20" />}
                            {progress.isError && (
                                <EmptyState
                                    title="Could not load student progress"
                                    hint="Check your connection and try again."
                                    action={<Button variant="secondary" onClick={() => progress.refetch()}>Retry</Button>}
                                />
                            )}
                            {!progress.isPending && !progress.isError && rows.length === 0 && (
                                <p className="text-sm text-[var(--oq-ink-soft)]">No progress yet.</p>
                            )}
                            {rows.map((row) => {
                                const draft = grades[row.studentId] ?? String(row.score ?? points);
                                return (
                                    <div key={row.id || row.studentId} className="flex flex-wrap items-center gap-3 border-b border-[var(--oq-line)] py-2">
                                        <span className="font-semibold min-w-32">{row.studentId}</span>
                                        <Chip tone={tone(row.status)}>{row.status.replace(/_/g, " ")}</Chip>
                                        <TextInput
                                            aria-label={`Score for ${row.studentId}`}
                                            className="w-20"
                                            type="number"
                                            min={0}
                                            max={points}
                                            value={draft}
                                            onChange={(e) =>
                                                setGrades((current) => ({ ...current, [row.studentId]: e.target.value }))
                                            }
                                        />
                                        <span className="text-sm text-[var(--oq-ink-soft)]">/ {row.maxScore ?? points}</span>
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
                                            Save grade
                                        </Button>
                                    </div>
                                );
                            })}
                            {grade.isError && <p className="text-sm text-red-600">{errorText(grade.error)}</p>}
                        </Card>

                        <Card className="p-6 flex flex-col gap-3">
                            <p className="label-caps">Student start check</p>
                            <div className="flex flex-wrap items-end gap-3">
                                <Field label="Student id" htmlFor="assignment-student">
                                    <TextInput
                                        id="assignment-student"
                                        value={studentId}
                                        onChange={(e) => setStudentId(e.target.value)}
                                        placeholder="s001"
                                    />
                                </Field>
                                <Button
                                    variant="secondary"
                                    disabled={start.isPending || !studentId.trim()}
                                    onClick={() => start.mutate()}
                                >
                                    Start assignment
                                </Button>
                            </div>
                            {start.isError && <p className="text-sm text-red-600">{errorText(start.error)}</p>}
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
