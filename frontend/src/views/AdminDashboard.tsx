import axios from "axios";
import { useEffect, useState } from "react";
import { Card, CardContent, Button, Chip } from "@heroui/react";
import { useAdminStore } from "../stores/useAdminStore";
import { useUIStore } from "../stores/useUIStore";
import { adminApi } from "../services/AdminApiService";
import { QuestionWizard } from "./QuestionWizard";
import { useStaggerIn } from "../hooks/useMotion";
import { ThemeToggle } from "../components/ThemeToggle";
import { LogoMark } from "../components/LogoMark";
import { motion, AnimatePresence } from "framer-motion";

type AdminTab = "dashboard" | "quizzes" | "questions" | "games" | "settings";
type GameMode = "STANDARD" | "AUTO_PILOT" | "PRACTICE" | "EXAM" | "TEAM" | "BATTLE";

const TABS: { id: AdminTab; label: string }[] = [
    { id: "dashboard", label: "Dashboard" },
    { id: "quizzes", label: "Quizzes" },
    { id: "questions", label: "Questions" },
    { id: "games", label: "Games" },
    { id: "settings", label: "Settings" },
];

export function AdminDashboard() {
    const { quizzes, questions, activeQuizId, loadQuizzes, loadQuestions, openWizard, createQuiz } =
        useAdminStore();
    const wizardOpen = useAdminStore((s) => s.wizardOpen);
    const setView = useUIStore((s) => s.setView);
    const setPin = useUIStore((s) => s.setPin);
    const [tab, setTab] = useState<AdminTab>("dashboard");
    const [gameMode, setGameMode] = useState<GameMode>("STANDARD");
    const [title, setTitle] = useState("");
    const [desc, setDesc] = useState("");
    const [busy, setBusy] = useState(false);
    const [needsAuth, setNeedsAuth] = useState(false);
    const [showCreate, setShowCreate] = useState(false);
    const [search, setSearch] = useState("");
    const [quizSearch, setQuizSearch] = useState("");

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
                <Card className="bg-content1 w-full max-w-sm">
                    <CardContent className="p-6">
                        <div className="flex items-center gap-2.5 mb-6">
                            <LogoMark size={28} />
                            <span className="font-extrabold tracking-tight">SprintJudge Admin</span>
                        </div>
                        <form
                            method="POST"
                            action="/admin/login"
                            className="flex flex-col gap-4"
                        >
                            <label className="label-caps block mb-1" htmlFor="un">
                                Username
                            </label>
                            <input
                                id="un"
                                name="username"
                                className="input-underline"
                                placeholder="admin"
                                autoComplete="username"
                            />
                            <label className="label-caps block mb-1" htmlFor="pw">
                                Password
                            </label>
                            <input
                                id="pw"
                                name="password"
                                type="password"
                                className="input-underline"
                                placeholder="password"
                                autoComplete="current-password"
                            />
                            <Button
                                type="submit"
                                variant="primary"
                                className="w-full bg-[var(--oq-red)] text-white"
                            >
                                Sign in
                            </Button>
                        </form>
                    </CardContent>
                </Card>
            </div>
        );
    }

    const host = async (quizId: string, mode: GameMode = gameMode) => {
        setBusy(true);
        try {
            const game = await adminApi.createGame(quizId, mode);
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

    const filteredQuestions = questions.filter((q) => {
        return !search || q.title.toLowerCase().includes(search.toLowerCase());
    });

    const filteredQuizzes = quizzes.filter((q) => {
        return !quizSearch || q.title.toLowerCase().includes(quizSearch.toLowerCase())
            || (q.description && q.description.toLowerCase().includes(quizSearch.toLowerCase()));
    });

    return (
        <div className="pattern-exam min-h-screen flex flex-col">
            {/* Header */}
            <header className="border-b border-default-200 bg-surface">
                <div className="page-shell py-3 flex items-center gap-3">
                    <div className="flex items-center gap-2.5">
                        <LogoMark size={24} />
                        <span className="font-extrabold tracking-tight text-lg">SprintJudge</span>
                    </div>
                    <div className="flex-1" />
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
                </div>
            </header>

            {/* Tab bar */}
            <nav className="border-b border-default-200 bg-surface">
                <div className="page-shell flex gap-1 overflow-x-auto">
                    {TABS.map((t) => (
                        <button
                            key={t.id}
                            onClick={() => setTab(t.id)}
                            className={
                                "px-4 py-3 text-sm font-bold border-b-2 transition-colors whitespace-nowrap " +
                                (tab === t.id
                                    ? "border-[var(--oq-red)] text-[var(--oq-red)]"
                                    : "border-transparent text-default-500 hover:text-default-700")
                            }
                        >
                            {t.label}
                        </button>
                    ))}
                </div>
            </nav>

            {/* Tab content */}
            <main className="flex-1 page-shell py-6">
                <AnimatePresence mode="wait">
                    {tab === "dashboard" && (
                        <motion.div
                            key="dashboard"
                            initial={{ opacity: 0, y: 8 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -8 }}
                            transition={{ duration: 0.15 }}
                        >
                            <div className="grid sm:grid-cols-3 gap-4 mb-8">
                                <Card className="bg-content1">
                                    <CardContent className="p-5">
                                        <p className="label-caps mb-1">Quiz sets</p>
                                        <p className="stat-value">{quizzes.length}</p>
                                    </CardContent>
                                </Card>
                                <Card className="bg-content1">
                                    <CardContent className="p-5">
                                        <p className="label-caps mb-1">Questions loaded</p>
                                        <p className="stat-value">{questions.length}</p>
                                    </CardContent>
                                </Card>
                                <Card className="bg-content1">
                                    <CardContent className="p-5">
                                        <p className="label-caps mb-1">Question types</p>
                                        <p className="stat-value">12</p>
                                    </CardContent>
                                </Card>
                            </div>
                            <h3 className="header-double mb-4">Quick actions</h3>
                            <div className="flex flex-wrap gap-3 items-center">
                                <select
                                    value={gameMode}
                                    onChange={(e) => setGameMode(e.target.value as GameMode)}
                                    className="input-underline min-h-[36px] text-sm"
                                >
                                    <option value="STANDARD">Standard</option>
                                    <option value="AUTO_PILOT">Auto-pilot</option>
                                    <option value="PRACTICE">Practice</option>
                                    <option value="EXAM">Exam</option>
                                    <option value="TEAM">Team</option>
                                    <option value="BATTLE">Battle</option>
                                </select>
                                <Button
                                    variant="primary"
                                    className="bg-[var(--oq-red)] text-white"
                                    onPress={() => {
                                        setTab("quizzes");
                                        setShowCreate(true);
                                    }}
                                >
                                    Create quiz
                                </Button>
                                <Button
                                    variant="outline"
                                    onPress={() => {
                                        if (quizzes.length > 0) host(quizzes[0].id, gameMode);
                                    }}
                                    isDisabled={quizzes.length === 0 || busy}
                                >
                                    Host first quiz
                                </Button>
                                <Button variant="outline" onPress={() => setTab("questions")}>
                                    Browse questions
                                </Button>
                            </div>
                        </motion.div>
                    )}

                    {tab === "quizzes" && (
                        <motion.div
                            key="quizzes"
                            initial={{ opacity: 0, y: 8 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -8 }}
                            transition={{ duration: 0.15 }}
                        >
                            <div className="flex items-center justify-between mb-4 gap-3 flex-wrap">
                                <h2 className="text-lg font-extrabold mr-auto">Quiz sets</h2>
                                <input
                                    value={quizSearch}
                                    onChange={(e) => setQuizSearch(e.target.value)}
                                    placeholder="Search quizzes…"
                                    className="input-underline min-h-[36px] text-sm max-w-xs"
                                />
                                <Button
                                    size="sm"
                                    variant="primary"
                                    className="bg-[var(--oq-red)] text-white"
                                    onPress={() => setShowCreate(true)}
                                >
                                    New set
                                </Button>
                            </div>

                            {showCreate && (
                                <Card className="bg-content1 mb-4">
                                    <CardContent className="p-5">
                                        <h3 className="header-double">Create question set</h3>
                                        <div className="flex flex-col sm:flex-row gap-3">
                                            <input
                                                value={title}
                                                onChange={(e) => setTitle(e.target.value)}
                                                placeholder="Title"
                                                className="input-underline flex-1"
                                            />
                                            <input
                                                value={desc}
                                                onChange={(e) => setDesc(e.target.value)}
                                                placeholder="Description"
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
                            )}

                            <div ref={gridRef} className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
                                {filteredQuizzes.map((q) => (
                                    <Card key={q.id} className="oq-quiz-card bg-content1">
                                        <CardContent className="p-5 flex flex-col gap-3">
                                            <div className="flex items-start justify-between gap-2">
                                                <h3 className="font-bold text-base leading-snug">
                                                    {q.title}
                                                </h3>
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
                                                    onPress={() => {
                                                        loadQuestions(q.id);
                                                        setTab("questions");
                                                    }}
                                                >
                                                    Questions
                                                </Button>
                                                <select
                                                    value={gameMode}
                                                    onChange={(e) => setGameMode(e.target.value as GameMode)}
                                                    className="input-underline min-h-[30px] text-xs max-w-[100px]"
                                                >
                                                    <option value="STANDARD">Standard</option>
                                                    <option value="AUTO_PILOT">Auto</option>
                                                    <option value="PRACTICE">Practice</option>
                                                    <option value="EXAM">Exam</option>
                                                    <option value="TEAM">Team</option>
                                                    <option value="BATTLE">Battle</option>
                                                </select>
                                                <Button
                                                    size="sm"
                                                    variant="primary"
                                                    className="bg-[var(--oq-red)] text-white"
                                                    isDisabled={busy}
                                                    onPress={() => host(q.id, gameMode)}
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
                                        </CardContent>
                                    </Card>
                                ))}
                                {filteredQuizzes.length === 0 && (
                                    <Card className="oq-quiz-card col-span-full bg-content1">
                                        <CardContent className="text-center py-16">
                                            <p className="label-caps mb-2">Empty library</p>
                                            <p className="text-default-500">
                                                Create your first set or import a bank JSON.
                                            </p>
                                        </CardContent>
                                    </Card>
                                )}
                            </div>
                        </motion.div>
                    )}

                    {tab === "questions" && (
                        <motion.div
                            key="questions"
                            initial={{ opacity: 0, y: 8 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -8 }}
                            transition={{ duration: 0.15 }}
                        >
                            <div className="flex items-center gap-3 mb-4 flex-wrap">
                                <h2 className="text-lg font-extrabold mr-auto">Questions</h2>
                                <select
                                    value={activeQuizId ?? ""}
                                    onChange={(e) => {
                                        if (e.target.value) loadQuestions(e.target.value);
                                    }}
                                    className="input-underline min-h-[36px] text-sm max-w-xs"
                                >
                                    <option value="">Select a quiz set…</option>
                                    {quizzes.map((q) => (
                                        <option key={q.id} value={q.id}>
                                            {q.title}
                                        </option>
                                    ))}
                                </select>
                                <input
                                    value={search}
                                    onChange={(e) => setSearch(e.target.value)}
                                    placeholder="Search…"
                                    className="input-underline min-h-[36px] text-sm max-w-xs"
                                />
                                <Button
                                    size="sm"
                                    variant="primary"
                                    className="bg-[var(--oq-red)] text-white"
                                    isDisabled={!activeQuizId}
                                    onPress={() => activeQuizId && openWizard(activeQuizId)}
                                >
                                    Add question
                                </Button>
                            </div>

                            {filteredQuestions.length > 0 ? (
                                <div className="overflow-x-auto">
                                    <table className="table-dotted w-full">
                                        <thead>
                                            <tr>
                                                <th className="w-12">#</th>
                                                <th>Title</th>
                                                <th>Type</th>
                                                <th className="w-20">Time</th>
                                                <th className="w-20">Points</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {filteredQuestions.map((q, i) => (
                                                <tr key={q.id}>
                                                    <td className="mono text-sm">{i + 1}</td>
                                                    <td className="font-medium">{q.title}</td>
                                                    <td>
                                                        <Chip size="sm" variant="soft">
                                                            {q.questionType.replace(/_/g, " ")}
                                                        </Chip>
                                                    </td>
                                                    <td className="mono text-sm">{q.timeLimitSec}s</td>
                                                    <td className="mono text-sm">{q.pointsBase}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            ) : (
                                <div className="text-center py-16 text-default-500">
                                    {activeQuizId
                                        ? "No questions match your search."
                                        : "Select a quiz set to view questions."}
                                </div>
                            )}
                        </motion.div>
                    )}

                    {tab === "games" && (
                        <motion.div
                            key="games"
                            initial={{ opacity: 0, y: 8 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -8 }}
                            transition={{ duration: 0.15 }}
                        >
                            <h2 className="text-lg font-extrabold mb-4">Game history</h2>
                            <div className="text-center py-16 text-default-500">
                                <p className="label-caps mb-2">Coming soon</p>
                                <p>Game history with list + calendar views will appear here.</p>
                            </div>
                        </motion.div>
                    )}

                    {tab === "settings" && (
                        <motion.div
                            key="settings"
                            initial={{ opacity: 0, y: 8 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -8 }}
                            transition={{ duration: 0.15 }}
                        >
                            <h2 className="text-lg font-extrabold mb-4">Settings</h2>
                            <div className="text-center py-16 text-default-500">
                                <p className="label-caps mb-2">Coming soon</p>
                                <p>General, Security, AI, Executor, and Display settings.</p>
                            </div>
                        </motion.div>
                    )}
                </AnimatePresence>
            </main>

            {wizardOpen && <QuestionWizard />}
        </div>
    );
}
