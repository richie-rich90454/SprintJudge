import axios from "axios";
import { useEffect, useState } from "react";
import { Card, CardContent, Button, Chip } from "@heroui/react";
import { useAdminStore } from "../stores/useAdminStore";
import { useUIStore } from "../stores/useUIStore";
import { adminApi } from "../services/AdminApiService";
import { QuestionWizard } from "./QuestionWizard";
import { useStaggerIn } from "../hooks/useMotion";
import { ThemeToggle } from "../components/ThemeToggle";

export function AdminDashboard() {
    const { quizzes, questions, activeQuizId, loadQuizzes, loadQuestions, openWizard, createQuiz } =
        useAdminStore();
    const wizardOpen = useAdminStore((s) => s.wizardOpen);
    const setView = useUIStore((s) => s.setView);
    const setPin = useUIStore((s) => s.setPin);
    const [title, setTitle] = useState("");
    const [desc, setDesc] = useState("");
    const [busy, setBusy] = useState(false);
    const [needsAuth, setNeedsAuth] = useState(false);
    const [showCreate, setShowCreate] = useState(false);

    const gridRef = useStaggerIn<HTMLDivElement>(".oq-quiz-card", [quizzes.length], 0.06);

    useEffect(() => {
        loadQuizzes().catch((e: unknown) => {
            if (axios.isAxiosError(e) && e.response?.status === 401) setNeedsAuth(true);
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    if (needsAuth) {
        return (
            <div className="pattern-exam min-h-screen flex items-center justify-center p-4">
                <form
                    method="POST"
                    action="/admin/login"
                    className="card text-center max-w-sm w-full bg-content1"
                >
                    <p className="label-caps mb-2">Authentication required</p>
                    <h2 className="text-2xl font-extrabold mb-6">Admin sign-in</h2>
                    <label className="label-caps block mb-1 text-left" htmlFor="un">
                        Username
                    </label>
                    <input
                        id="un"
                        name="username"
                        className="input-underline mb-4"
                        placeholder="admin"
                        autoComplete="username"
                    />
                    <label className="label-caps block mb-1 text-left" htmlFor="pw">
                        Password
                    </label>
                    <input
                        id="pw"
                        name="password"
                        type="password"
                        className="input-underline mb-5"
                        placeholder="password"
                        autoComplete="current-password"
                    />
                    <button type="submit" className="btn btn-primary w-full">
                        Sign in
                    </button>
                </form>
            </div>
        );
    }

    const host = async (quizId: string) => {
        setBusy(true);
        try {
            const game = await adminApi.createGame(quizId);
            setPin(game.pinCode);
            setView("host");
        } finally {
            setBusy(false);
        }
    };

    const doExport = async () => {
        const json = await adminApi.exportBank();
        const blob = new Blob([json], { type: "application/json" });
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = "sprintjudge-bank.json";
        a.click();
    };

    const doImport = async (file: File) => {
        const json = await file.text();
        await adminApi.importBank(json, true);
        await loadQuizzes();
    };

    return (
        <div className="pattern-exam min-h-screen pb-12">
            <header className="border-b border-default-200">
                <div className="page-shell py-4 flex flex-wrap items-center gap-3">
                    <div className="flex items-center gap-3 mr-auto">
                        <h1 className="text-xl font-extrabold tracking-tight">Admin</h1>
                        <Chip size="sm" variant="soft">
                            {quizzes.length} sets
                        </Chip>
                    </div>
                    <ThemeToggle />
                    <Button size="sm" variant="outline" onPress={() => setView("join")}>
                        Player view
                    </Button>
                    <Button size="sm" variant="outline" onPress={doExport}>
                        Export
                    </Button>
                    <label className="btn btn-secondary btn-sm cursor-pointer">
                        Import
                        <input
                            type="file"
                            accept="application/json"
                            className="hidden"
                            onChange={(e) => e.target.files?.[0] && doImport(e.target.files[0])}
                        />
                    </label>
                    <Button
                        size="sm"
                        variant="primary"
                        className="bg-[var(--oq-red)] text-white"
                        onPress={() => setShowCreate(true)}
                    >
                        New set
                    </Button>
                </div>
            </header>

            {showCreate && (
                <div className="page-shell mt-6">
                    <Card className="bg-content1">
                        <CardContent className="p-5">
                            <h3 className="header-double">Create question set</h3>
                            <div className="flex flex-col sm:flex-row gap-3">
                                <input
                                    value={title}
                                    onChange={(e) => setTitle(e.target.value)}
                                    placeholder="Title (e.g. Java Objects Foundations)"
                                    className="input-underline flex-1"
                                />
                                <input
                                    value={desc}
                                    onChange={(e) => setDesc(e.target.value)}
                                    placeholder="Description (optional)"
                                    className="input-underline flex-1"
                                />
                                <Button
                                    variant="primary"
                                    className="bg-[var(--oq-red)] text-white"
                                    onPress={async () => {
                                        if (title.trim()) {
                                            await createQuiz(title.trim(), desc.trim());
                                            setTitle("");
                                            setDesc("");
                                            setShowCreate(false);
                                        }
                                    }}
                                >
                                    Create
                                </Button>
                            </div>
                        </CardContent>
                    </Card>
                </div>
            )}

            <main className="page-shell mt-6">
                <div ref={gridRef} className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
                    {quizzes.map((q) => (
                        <Card key={q.id} className="oq-quiz-card bg-content1">
                            <CardContent className="p-5 flex flex-col gap-3">
                                <div className="flex items-start justify-between gap-2">
                                    <h3 className="font-bold text-base leading-snug">{q.title}</h3>
                                </div>
                                {q.description && (
                                    <p className="text-default-500 text-sm line-clamp-2 flex-1">
                                        {q.description}
                                    </p>
                                )}
                                <div className="flex flex-wrap gap-2 pt-2 border-t border-default-200">
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        onPress={() => loadQuestions(q.id)}
                                    >
                                        Questions
                                    </Button>
                                    <Button
                                        size="sm"
                                        variant="primary"
                                        className="bg-[var(--oq-red)] text-white"
                                        isDisabled={busy}
                                        onPress={() => host(q.id)}
                                    >
                                        Host
                                    </Button>
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        onPress={() => openWizard(q.id)}
                                    >
                                        Add
                                    </Button>
                                </div>
                                {activeQuizId === q.id && (
                                    <ul className="mt-2 flex flex-col gap-1 max-h-40 overflow-y-auto text-sm">
                                        {questions.map((qn, i) => (
                                            <li
                                                key={qn.id}
                                                className="flex justify-between items-center py-1 px-2 rounded hover:bg-default-100"
                                            >
                                                <span className="truncate">
                                                    {i + 1}. {qn.title}
                                                </span>
                                                <Chip
                                                    size="sm"
                                                    variant="soft"
                                                    className="!text-[10px] !py-0.5 !px-2"
                                                >
                                                    {qn.questionType}
                                                </Chip>
                                            </li>
                                        ))}
                                        {questions.length === 0 && (
                                            <li className="text-default-500 text-sm">
                                                No questions yet.
                                            </li>
                                        )}
                                    </ul>
                                )}
                            </CardContent>
                        </Card>
                    ))}
                    {quizzes.length === 0 && (
                        <Card className="oq-quiz-card col-span-full bg-content1">
                            <CardContent className="text-center py-16">
                                <p className="label-caps mb-2">Empty library</p>
                                <p className="text-default-500">
                                    Create your first question set above, or import a bank JSON.
                                </p>
                            </CardContent>
                        </Card>
                    )}
                </div>
            </main>

            {wizardOpen && <QuestionWizard />}
        </div>
    );
}
