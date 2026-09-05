import axios from "axios";
import { useEffect, useState, useRef } from "react";
import { useNavigate } from "@tanstack/react-router";
import { Card } from "../components/ui/Card";
import { Button } from "../components/ui/Button";
import { Chip } from "../components/ui/Primitives";
import { TextInput } from "../components/ui/TextInput";
import { Tabs, TabPanel } from "../components/ui/Tabs";
import { useAdminStore } from "../stores/useAdminStore";
import { useUIStore } from "../stores/useUIStore";
import { adminApi } from "../services/AdminApiService";
import { QuestionWizard } from "./QuestionWizard";
import { useStaggerIn } from "../hooks/useMotion";
import { SoundToggle } from "../components/SoundToggle";
import { MotionToggle } from "../components/MotionToggle";
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

function SettingsTab() {
    const [settings, setSettings] = useState<Record<string, string>>({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const savedTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => {
        return () => {
            if (savedTimer.current) clearTimeout(savedTimer.current);
        };
    }, []);

    useEffect(() => {
        adminApi
            .getSettings()
            .then((s) => {
                setSettings(s);
                setLoading(false);
            })
            .catch(() => setLoading(false));
    }, []);

    const handleSave = async () => {
        setSaving(true);
        try {
            await adminApi.updateSettings(settings);
            setSaved(true);
            if (savedTimer.current) clearTimeout(savedTimer.current);
            savedTimer.current = setTimeout(() => setSaved(false), 2000);
        } finally {
            setSaving(false);
        }
    };

    if (loading)
        return (
            <div aria-label="Loading settings">
                <div className="flex flex-col gap-4 max-w-lg" aria-hidden="true">
                    {[0, 1, 2].map((i) => (
                        <div
                            key={i}
                            className="h-11 animate-pulse rounded-[6px] bg-[var(--oq-border)] opacity-40"
                        />
                    ))}
                </div>
                <p className="text-[var(--oq-ink-soft)] text-sm mt-4">Loading settings…</p>
            </div>
        );

    return (
        <motion.div
            key="settings"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.15 }}
        >
            <h2 className="text-lg font-extrabold mb-4">Settings</h2>
            {Object.keys(settings).length === 0 ? (
                <p className="text-[var(--oq-ink-soft)]">No settings configured.</p>
            ) : (
                <div className="flex flex-col gap-4 max-w-lg">
                    {Object.entries(settings).map(([key, value]) => (
                        <div key={key} className="flex flex-col gap-1">
                            <label className="label-caps">{key}</label>
                            <TextInput
                                value={value}
                                onChange={(e) => setSettings({ ...settings, [key]: e.target.value })}
                            />
                        </div>
                    ))}
                    <div className="flex gap-4 pt-2">
                        <Button variant="primary" onClick={handleSave} disabled={saving}>
                            {saving ? "Saving..." : "Save"}
                        </Button>
                        {saved && (
                            <span className="text-sm text-[var(--oq-success)] self-center">
                                Saved!
                            </span>
                        )}
                    </div>
                </div>
            )}
        </motion.div>
    );
}

export function AdminDashboard() {
    const { quizzes, questions, activeQuizId, loadQuizzes, loadQuestions, openWizard, createQuiz } =
        useAdminStore();
    const wizardOpen = useAdminStore((s) => s.wizardOpen);
    const navigate = useNavigate();
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
    const [bannerError, setBannerError] = useState<string | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);

    const gridRef = useStaggerIn<HTMLDivElement>(".oq-quiz-card", [quizzes.length], 0.06);

    useEffect(() => {
        loadQuizzes().catch((e: unknown) => {
            if (axios.isAxiosError(e) && e.response?.status === 401) setNeedsAuth(true);
            else setLoadError("Failed to load quizzes. Check your connection and retry.");
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    if (needsAuth) {
        return (
            <div className="pattern-exam min-h-[100dvh] flex items-center justify-center p-4">
                <Card className="w-full max-w-sm">
                    <div className="p-6">
                        <div className="flex items-center gap-2.5 mb-6">
                            <LogoMark size={28} />
                            <span className="font-extrabold tracking-tight">SprintJudge Admin</span>
                        </div>
                        <form method="POST" action="/admin/login" className="flex flex-col gap-4">
                            <label className="label-caps block mb-1" htmlFor="un">
                                Username
                            </label>
                            <TextInput
                                id="un"
                                name="username"
                                placeholder="admin"
                                autoComplete="username"
                                aria-required="true"
                                aria-invalid="false"
                                aria-describedby="admin-auth-error"
                            />
                            <label className="label-caps block mb-1" htmlFor="pw">
                                Password
                            </label>
                            <TextInput
                                id="pw"
                                name="password"
                                type="password"
                                placeholder="password"
                                autoComplete="current-password"
                                aria-required="true"
                                aria-invalid="false"
                                aria-describedby="admin-auth-error"
                            />
                            <p
                                id="admin-auth-error"
                                role="alert"
                                className="hidden text-[var(--oq-danger)] text-sm"
                            />
                            <Button type="submit" variant="primary" className="w-full">
                                Sign in
                            </Button>
                        </form>
                    </div>
                </Card>
            </div>
        );
    }

    const host = async (quizId: string, mode: GameMode = gameMode) => {
        setBusy(true);
        setBannerError(null);
        try {
            const game = await adminApi.createGame(quizId, mode);
            setPin(game.pinCode);
            navigate({ to: "/host", search: { pin: game.pinCode, projector: false } });
        } catch {
            setBannerError("Failed to create game - try again.");
        } finally {
            setBusy(false);
        }
    };

    const doExport = async () => {
        setBannerError(null);
        try {
            const json = await adminApi.exportBank();
            const blob = new Blob([json], { type: "application/json" });
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = "sprintjudge-bank.json";
            a.click();
            URL.revokeObjectURL(url);
        } catch {
            setBannerError("Export failed - are you logged in?");
        }
    };

    const doImport = async (file: File) => {
        setBannerError(null);
        try {
            const json = await file.text();
            await adminApi.importBank(json, true);
            await loadQuizzes();
        } catch {
            setBannerError("Import failed - check the file format.");
        }
    };

    const filteredQuestions = questions.filter((q) => {
        return !search || q.title.toLowerCase().includes(search.toLowerCase());
    });

    const filteredQuizzes = quizzes.filter((q) => {
        return (
            !quizSearch ||
            q.title.toLowerCase().includes(quizSearch.toLowerCase()) ||
            (q.description && q.description.toLowerCase().includes(quizSearch.toLowerCase()))
        );
    });

    return (
        <div className="pattern-exam min-h-[100dvh] flex flex-col">
            {/* Header */}
            <header className="border-b border-[var(--oq-border)] bg-[var(--oq-surface)]">
                <div className="page-shell py-3 flex items-center gap-4">
                    <div className="flex items-center gap-2.5">
                        <LogoMark size={24} />
                        <span className="font-extrabold tracking-tight text-lg">SprintJudge</span>
                    </div>
                    <div className="flex-1" />
                    <SoundToggle />
                    <MotionToggle />
                    <Button variant="secondary" size="sm" onClick={() => navigate({ to: "/" })}>
                        Player view
                    </Button>
                    <Button variant="secondary" size="sm" onClick={doExport}>
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

            {/* Tab content */}
            <main className="flex-1 page-shell py-6">
                {bannerError && (
                    <p role="alert" className="text-[var(--oq-danger)] text-sm mb-4">
                        {bannerError}
                    </p>
                )}
                {loadError && (
                    <div className="mb-4 flex items-center gap-4 flex-wrap">
                        <p role="alert" className="text-[var(--oq-danger)] text-sm">
                            {loadError}
                        </p>
                        <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => {
                                setLoadError(null);
                                loadQuizzes().catch((e: unknown) => {
                                    if (axios.isAxiosError(e) && e.response?.status === 401)
                                        setNeedsAuth(true);
                                    else
                                        setLoadError(
                                            "Failed to load quizzes. Check your connection and retry.",
                                        );
                                });
                            }}
                        >
                            Retry
                        </Button>
                    </div>
                )}
                <Tabs
                    value={tab}
                    onValueChange={(v) => setTab(v as AdminTab)}
                    tabs={TABS}
                    label="Admin sections"
                >
                    <TabPanel value="dashboard">
                        <AnimatePresence mode="wait">
                            <motion.div
                                key="dashboard"
                                initial={{ opacity: 0, y: 8 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0, y: -8 }}
                                transition={{ duration: 0.15 }}
                            >
                                <div className="grid sm:grid-cols-3 gap-4 mb-8">
                                    <Card className="stat-block card-accent">
                                        <p className="label-caps mb-1">Quiz sets</p>
                                        <p className="stat-value">{quizzes.length}</p>
                                    </Card>
                                    <Card className="stat-block card-accent">
                                        <p className="label-caps mb-1">Questions loaded</p>
                                        <p className="stat-value">{questions.length}</p>
                                    </Card>
                                    <Card className="stat-block card-accent">
                                        <p className="label-caps mb-1">Question types</p>
                                        <p className="stat-value">12</p>
                                    </Card>
                                </div>
                                <h3 className="header-double mb-4">Quick actions</h3>
                                <div className="flex flex-wrap gap-4 items-center">
                                    <select
                                        value={gameMode}
                                        onChange={(e) => setGameMode(e.target.value as GameMode)}
                                        className="input-underline text-sm"
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
                                        onClick={() => {
                                            setTab("quizzes");
                                            setShowCreate(true);
                                        }}
                                    >
                                        Create quiz
                                    </Button>
                                    <Button
                                        variant="secondary"
                                        onClick={() => {
                                            if (quizzes.length > 0) host(quizzes[0].id, gameMode);
                                        }}
                                        disabled={quizzes.length === 0 || busy}
                                    >
                                        {busy ? "Hosting…" : "Host first quiz"}
                                    </Button>
                                    <Button
                                        variant="secondary"
                                        onClick={() => setTab("questions")}
                                    >
                                        Browse questions
                                    </Button>
                                </div>
                            </motion.div>
                        </AnimatePresence>
                    </TabPanel>

                    <TabPanel value="quizzes">
                        <AnimatePresence mode="wait">
                            <motion.div
                                key="quizzes"
                                initial={{ opacity: 0, y: 8 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0, y: -8 }}
                                transition={{ duration: 0.15 }}
                            >
                                <div className="flex items-center justify-between mb-4 gap-4 flex-wrap">
                                    <h2 className="text-lg font-extrabold mr-auto">Quiz sets</h2>
                                    <select
                                        value={gameMode}
                                        onChange={(e) =>
                                            setGameMode(e.target.value as GameMode)
                                        }
                                        aria-label="Game mode for hosting"
                                        className="input-underline text-sm max-w-[140px]"
                                    >
                                        <option value="STANDARD">Standard</option>
                                        <option value="AUTO_PILOT">Auto-pilot</option>
                                        <option value="PRACTICE">Practice</option>
                                        <option value="EXAM">Exam</option>
                                        <option value="TEAM">Team</option>
                                        <option value="BATTLE">Battle</option>
                                    </select>
                                    <TextInput
                                        value={quizSearch}
                                        onChange={(e) => setQuizSearch(e.target.value)}
                                        placeholder="Search quizzes…"
                                        aria-label="Search quizzes"
                                        className="text-sm max-w-xs"
                                    />
                                    <Button
                                        variant="primary"
                                        size="sm"
                                        onClick={() => setShowCreate(true)}
                                    >
                                        New set
                                    </Button>
                                </div>

                                {showCreate && (
                                    <Card className="mb-4">
                                        <h3 className="header-double">Create question set</h3>
                                        <div className="flex flex-col sm:flex-row gap-4">
                                            <TextInput
                                                value={title}
                                                onChange={(e) => setTitle(e.target.value)}
                                                placeholder="Title"
                                                aria-label="Quiz title"
                                                className="flex-1"
                                            />
                                            <TextInput
                                                value={desc}
                                                onChange={(e) => setDesc(e.target.value)}
                                                placeholder="Description"
                                                aria-label="Quiz description"
                                                className="flex-1"
                                            />
                                            <Button
                                                variant="primary"
                                                onClick={async () => {
                                                    if (title.trim()) {
                                                        await createQuiz(
                                                            title.trim(),
                                                            desc.trim(),
                                                        );
                                                        setTitle("");
                                                        setDesc("");
                                                        setShowCreate(false);
                                                    }
                                                }}
                                            >
                                                Create
                                            </Button>
                                        </div>
                                    </Card>
                                )}

                                <div
                                    ref={gridRef}
                                    className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4"
                                >
                                    {filteredQuizzes.map((q) => (
                                        <Card
                                            key={q.id}
                                            className="oq-quiz-card"
                                        >
                                            <div className="flex flex-col gap-4">
                                                <div className="flex items-start justify-between gap-4">
                                                    <h3 className="font-bold text-base leading-snug">
                                                        {q.title}
                                                    </h3>
                                                </div>
                                                {q.description && (
                                                    <p className="text-[var(--oq-ink-soft)] text-sm line-clamp-2 flex-1">
                                                        {q.description}
                                                    </p>
                                                )}
                                                <div className="flex flex-wrap gap-4 pt-2 border-t border-[var(--oq-border)]">
                                                    <Button
                                                        variant="secondary"
                                                        size="sm"
                                                        onClick={() => {
                                                            loadQuestions(q.id);
                                                            setTab("questions");
                                                        }}
                                                    >
                                                        Questions
                                                    </Button>
                                                    <Button
                                                        variant="primary"
                                                        size="sm"
                                                        disabled={busy}
                                                        onClick={() => host(q.id, gameMode)}
                                                    >
                                                        {busy ? "Hosting…" : "Host"}
                                                    </Button>
                                                    <Button
                                                        variant="secondary"
                                                        size="sm"
                                                        onClick={() => openWizard(q.id)}
                                                    >
                                                        Add
                                                    </Button>
                                                </div>
                                            </div>
                                        </Card>
                                    ))}
                                    {filteredQuizzes.length === 0 && (
                                        <Card className="oq-quiz-card col-span-full">
                                            <div className="text-center py-16">
                                                <p className="label-caps mb-2">Empty library</p>
                                                <p className="text-[var(--oq-ink-soft)]">
                                                    Create your first set or import a bank JSON.
                                                </p>
                                            </div>
                                        </Card>
                                    )}
                                </div>
                            </motion.div>
                        </AnimatePresence>
                    </TabPanel>

                    <TabPanel value="questions">
                        <AnimatePresence mode="wait">
                            <motion.div
                                key="questions"
                                initial={{ opacity: 0, y: 8 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0, y: -8 }}
                                transition={{ duration: 0.15 }}
                            >
                                <div className="flex items-center gap-4 mb-4 flex-wrap">
                                    <h2 className="text-lg font-extrabold mr-auto">Questions</h2>
                                    <select
                                        value={activeQuizId ?? ""}
                                        onChange={(e) => {
                                            if (e.target.value) loadQuestions(e.target.value);
                                        }}
                                        aria-label="Select quiz set"
                                        className="input-underline text-sm max-w-xs"
                                    >
                                        <option value="">Select a quiz set…</option>
                                        {quizzes.map((q) => (
                                            <option key={q.id} value={q.id}>
                                                {q.title}
                                            </option>
                                        ))}
                                    </select>
                                    <TextInput
                                        value={search}
                                        onChange={(e) => setSearch(e.target.value)}
                                        placeholder="Search…"
                                        aria-label="Search questions"
                                        className="text-sm max-w-xs"
                                    />
                                    <Button
                                        variant="primary"
                                        size="sm"
                                        disabled={!activeQuizId}
                                        onClick={() => activeQuizId && openWizard(activeQuizId)}
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
                                                            <Chip tone="neutral">
                                                                {q.questionType.replace(/_/g, " ")}
                                                            </Chip>
                                                        </td>
                                                        <td className="mono text-sm">
                                                            {q.timeLimitSec}s
                                                        </td>
                                                        <td className="mono text-sm">
                                                            {q.pointsBase}
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                ) : (
                                    <div className="text-center py-16 text-[var(--oq-ink-soft)]">
                                        {activeQuizId
                                            ? "No questions match your search."
                                            : "Select a quiz set to view questions."}
                                    </div>
                                )}
                            </motion.div>
                        </AnimatePresence>
                    </TabPanel>

                    <TabPanel value="games">
                        <AnimatePresence mode="wait">
                            <motion.div
                                key="games"
                                initial={{ opacity: 0, y: 8 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0, y: -8 }}
                                transition={{ duration: 0.15 }}
                            >
                                <h2 className="text-lg font-extrabold mb-4">Games</h2>
                                {quizzes.length === 0 ? (
                                    <div className="text-center py-16 text-[var(--oq-ink-soft)]">
                                        <p className="label-caps mb-2">No quiz sets yet</p>
                                        <p>Create a quiz set first, then host a game from here.</p>
                                    </div>
                                ) : (
                                    <div className="flex flex-col gap-4">
                                        <div className="flex items-center gap-4 flex-wrap">
                                            <label className="label-caps" htmlFor="games-mode">
                                                Mode
                                            </label>
                                            <select
                                                id="games-mode"
                                                value={gameMode}
                                                onChange={(e) =>
                                                    setGameMode(e.target.value as GameMode)
                                                }
                                                className="input-underline text-sm max-w-[160px]"
                                            >
                                                <option value="STANDARD">Standard</option>
                                                <option value="AUTO_PILOT">Auto-pilot</option>
                                                <option value="PRACTICE">Practice</option>
                                                <option value="EXAM">Exam</option>
                                                <option value="TEAM">Team</option>
                                                <option value="BATTLE">Battle</option>
                                            </select>
                                        </div>
                                        {filteredQuizzes.map((q) => (
                                            <div
                                                key={q.id}
                                                className="flex items-center justify-between gap-4 rounded-[8px] border border-[var(--oq-border)] bg-[var(--oq-surface)] p-6"
                                            >
                                                <div className="min-w-0">
                                                    <p
                                                        className="font-bold truncate"
                                                        title={q.title}
                                                    >
                                                        {q.title}
                                                    </p>
                                                    {q.description && (
                                                        <p className="text-[var(--oq-ink-soft)] text-sm truncate">
                                                            {q.description}
                                                        </p>
                                                    )}
                                                </div>
                                                <Button
                                                    variant="primary"
                                                    size="sm"
                                                    disabled={busy}
                                                    onClick={() => host(q.id, gameMode)}
                                                    className="shrink-0"
                                                >
                                                    {busy ? "Hosting…" : "Host game"}
                                                </Button>
                                            </div>
                                        ))}
                                        {filteredQuizzes.length === 0 && (
                                            <p className="text-[var(--oq-ink-soft)] text-sm">
                                                No quiz sets match your search.
                                            </p>
                                        )}
                                    </div>
                                )}
                            </motion.div>
                        </AnimatePresence>
                    </TabPanel>

                    <TabPanel value="settings">
                        <AnimatePresence mode="wait">
                            <SettingsTab key="settings-panel" />
                        </AnimatePresence>
                    </TabPanel>
                </Tabs>
            </main>

            {wizardOpen && <QuestionWizard />}
        </div>
    );
}
