import { useI18n } from "../i18n";
import axios from "axios";
import { useEffect, useState, useRef } from "react";
import { useNavigate } from "@tanstack/react-router";
import { Card } from "../ui/Card";
import { Button } from "../ui/Button";
import { Chip } from "../ui/Primitives";
import { TextInput } from "../ui/TextInput";
import { Tabs, TabPanel } from "../ui/Tabs";
import { useAdminStore } from "../stores/useAdminStore";
import { useUIStore } from "../stores/useUIStore";
import { adminApi } from "../services/AdminApiService";
import { QuestionWizard } from "./QuestionWizard";
import { useStaggerIn } from "../hooks/useMotion";
import { SoundToggle } from "../components/SoundToggle";
import { MotionToggle } from "../components/MotionToggle";
import { AboutDialog } from "../components/AboutDialog";
import { LogoMark } from "../components/LogoMark";

type AdminTab = "dashboard" | "quizzes" | "questions" | "games" | "settings";
type GameMode = "STANDARD" | "AUTO_PILOT" | "PRACTICE" | "EXAM" | "TEAM" | "BATTLE";

const TABS: { id: AdminTab; label: string }[] = [
    { id: "dashboard", label: "Dashboard" },
    { id: "quizzes", label: "Quizzes" },
    { id: "questions", label: "Questions" },
    { id: "games", label: "Games" },
    { id: "settings", label: "Settings" },
];

const CSA_PRESETS = [
    {
        unit: 1,
        title: "Using Objects and Methods",
        detail: "Objects, methods, primitives, strings",
    },
    { unit: 2, title: "Selection and Iteration", detail: "Boolean logic, if statements, loops" },
    { unit: 3, title: "Class Creation", detail: "Encapsulation, constructors, invariants" },
    { unit: 4, title: "Data Collections", detail: "Arrays, lists, 2D arrays, recursion" },
    { unit: 5, title: "Mixed CSA", detail: "Four questions from each unit" },
];

function SettingsTab() {
    const { t } = useI18n();
    const [settings, setSettings] = useState<Record<string, string>>({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [saveError, setSaveError] = useState<string | null>(null);
    const [settingsError, setSettingsError] = useState(false);
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
            .catch(() => {
                setSettingsError(true);
                setLoading(false);
            });
    }, []);

    const handleSave = async () => {
        setSaving(true);
        setSaveError(null);
        try {
            await adminApi.updateSettings(settings);
            setSaved(true);
            if (savedTimer.current) clearTimeout(savedTimer.current);
            savedTimer.current = setTimeout(() => setSaved(false), 2000);
        } catch {
            setSaveError(t("ui.saveFailedAreYouStillLoggedIn"));
        } finally {
            setSaving(false);
        }
    };

    if (loading)
        return (
            <div aria-label={t("ui.loadingSettings")}>
                <div className="flex flex-col gap-4 max-w-lg" aria-hidden="true">
                    {[0, 1, 2].map((i) => (
                        <div
                            key={i}
                            className="h-11 animate-pulse rounded-[2px] bg-[var(--oq-border)] opacity-40"
                        />
                    ))}
                </div>
                <p className="text-[var(--oq-ink-soft)] text-sm mt-4">
                    {t("ui.loadingSettingsEllipsis")}
                </p>
            </div>
        );

    return (
        <div className="tab-panel" key="settings">
            <h2 className="text-lg font-extrabold mb-4">{t("ui.settings")}</h2>
            {settingsError ? (
                <div className="flex flex-col items-start gap-3">
                    <p role="alert" className="text-[var(--oq-danger)] text-sm">
                        {t("ui.couldNotLoadSettingsCheckTheServerAndTryAgain")}
                    </p>
                    <Button variant="secondary" onClick={() => window.location.reload()}>
                        {t("ui.retry")}
                    </Button>
                </div>
            ) : Object.keys(settings).length === 0 ? (
                <p className="text-[var(--oq-ink-soft)]">{t("ui.noSettingsConfigured")}</p>
            ) : (
                <Card className="max-w-lg p-6">
                    <div className="flex flex-col gap-4">
                        {Object.entries(settings).map(([key, value]) => (
                            <div key={key} className="flex flex-col gap-1">
                                <label className="label-caps">{key}</label>
                                <TextInput
                                    value={value}
                                    onChange={(e) =>
                                        setSettings({ ...settings, [key]: e.target.value })
                                    }
                                />
                            </div>
                        ))}
                        <div className="flex gap-4 pt-2">
                            <Button variant="primary" onClick={handleSave} disabled={saving}>
                                {saving ? t("ui.savingDots") : t("ui.save")}
                            </Button>
                            {saved && (
                                <span className="text-sm text-[var(--oq-success)] self-center">
                                    {t("ui.saved")}
                                </span>
                            )}
                            {saveError && (
                                <span
                                    role="alert"
                                    className="text-sm text-[var(--oq-danger)] self-center"
                                >
                                    {saveError}
                                </span>
                            )}
                        </div>
                    </div>
                </Card>
            )}
        </div>
    );
}

export function AdminDashboard() {
    const { t } = useI18n();
    const loginFailed = new URLSearchParams(window.location.search).has("error");
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
    const [csaBusy, setCsaBusy] = useState<number | null>(null);
    const [needsAuth, setNeedsAuth] = useState(false);
    const [showCreate, setShowCreate] = useState(false);
    const [search, setSearch] = useState("");
    const [quizSearch, setQuizSearch] = useState("");
    const [quizLimit, setQuizLimit] = useState(24);
    const [gamesLimit, setGamesLimit] = useState(24);
    const [bannerError, setBannerError] = useState<string | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);

    const gridRef = useStaggerIn<HTMLDivElement>(".oq-quiz-card", [quizzes.length], 0.06);

    useEffect(() => {
        setQuizLimit(24);
    }, [quizSearch]);

    useEffect(() => {
        setGamesLimit(24);
    }, [quizSearch]);

    useEffect(() => {
        loadQuizzes().catch((e: unknown) => {
            if (axios.isAxiosError(e) && e.response?.status === 401) setNeedsAuth(true);
            else setLoadError(t("ui.failedToLoadQuizzesCheckYourConnectionAndRetry"));
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
                            <span className="font-extrabold tracking-tight">
                                {t("ui.sprintjudgeAdmin")}
                            </span>
                        </div>
                        <form method="POST" action="/admin/login" className="flex flex-col gap-4">
                            <label className="label-caps block mb-1" htmlFor="un">
                                {t("ui.username")}
                            </label>
                            <TextInput
                                id="un"
                                name="username"
                                placeholder={t("ui.adminPlaceholder")}
                                autoComplete="username"
                                required
                                aria-invalid={loginFailed}
                            />
                            <label className="label-caps block mb-1" htmlFor="pw">
                                {t("ui.password")}
                            </label>
                            <TextInput
                                id="pw"
                                name="password"
                                type="password"
                                placeholder={t("ui.passwordPlaceholder")}
                                autoComplete="current-password"
                                required
                                aria-invalid={loginFailed}
                            />
                            {loginFailed && (
                                <p
                                    id="admin-auth-error"
                                    role="alert"
                                    className="text-[var(--oq-danger)] text-sm"
                                >
                                    {t("ui.wrongUsernameOrPasswordPleaseTryAgain")}
                                </p>
                            )}
                            <Button type="submit" variant="primary" className="w-full">
                                {t("ui.signIn")}
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
            setBannerError(t("ui.failedToCreateGameTryAgain"));
        } finally {
            setBusy(false);
        }
    };

    const startCsa = async (unit: number) => {
        setCsaBusy(unit);
        setBannerError(null);
        try {
            const built = await adminApi.buildBankQuiz({
                csaUnit: unit,
                count: 16,
                includeCoding: true,
            });
            const game = await adminApi.createGame(built.quizId, "STANDARD");
            setPin(game.pinCode);
            navigate({ to: "/host", search: { pin: game.pinCode, projector: false } });
        } catch (e) {
            const status = (e as { response?: { status?: number } })?.response?.status;
            setBannerError(
                status === 403
                    ? t("ui.yourSessionExpiredSignInAgainAndRetry")
                    : t("ui.couldNotBuildTheCSAGameCheckTheBankAndRetry"),
            );
        } finally {
            setCsaBusy(null);
        }
    };

    const doExport = async () => {
        setBannerError(null);
        try {
            const exported = await adminApi.exportBank();
            // Axios may parse the response into an object depending on the
            // converter's content type; Blob needs the serialized JSON either way.
            const json = typeof exported === "string" ? exported : JSON.stringify(exported);
            const blob = new Blob([json], { type: "application/json" });
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = "sprintjudge-bank.json";
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.setTimeout(() => URL.revokeObjectURL(url), 0);
        } catch {
            setBannerError(t("ui.exportFailedAreYouLoggedIn"));
        }
    };

    const doImport = async (file: File) => {
        setBannerError(null);
        try {
            const json = await file.text();
            await adminApi.importBank(json, true);
            await loadQuizzes();
        } catch {
            setBannerError(t("ui.importFailedCheckTheFileFormat"));
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
                        <span className="font-extrabold tracking-tight text-lg">
                            {t("ui.brand")}
                        </span>
                    </div>
                    <div className="flex-1" />
                    <SoundToggle className="header-icon" />
                    <MotionToggle className="header-icon" />
                    <AboutDialog />
                    <Button variant="secondary" size="sm" onClick={() => navigate({ to: "/" })}>
                        {t("ui.playerView")}
                    </Button>
                    <Button variant="secondary" size="sm" onClick={doExport}>
                        {t("ui.export")}
                    </Button>
                    <label className="btn btn-secondary btn-sm cursor-pointer">
                        {t("ui.import")}
                        <input
                            type="file"
                            accept="application/json"
                            className="hidden"
                            // Reset so picking the same file twice still fires.
                            onClick={(e) => {
                                (e.target as HTMLInputElement).value = "";
                            }}
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
                                            t("ui.failedToLoadQuizzesCheckYourConnectionAndRetry"),
                                        );
                                });
                            }}
                        >
                            {t("ui.retry")}
                        </Button>
                    </div>
                )}
                <Tabs
                    value={tab}
                    onValueChange={(v) => setTab(v as AdminTab)}
                    tabs={TABS.map((tab) => ({ ...tab, label: t(tab.label) }))}
                    label={t("ui.adminSections")}
                >
                    <TabPanel value="dashboard">
                        <div className="tab-panel" key="dashboard">
                            <div className="grid sm:grid-cols-3 gap-4 mb-8">
                                <Card className="stat-block card-accent">
                                    <p className="label-caps mb-1">{t("ui.quizSets")}</p>
                                    <p className="stat-value">{quizzes.length}</p>
                                </Card>
                                <Card className="stat-block card-accent">
                                    <p className="label-caps mb-1">{t("ui.csaUnits")}</p>
                                    <p className="stat-value">4</p>
                                </Card>
                                <Card className="stat-block card-accent">
                                    <p className="label-caps mb-1">{t("ui.questionTypes")}</p>
                                    <p className="stat-value">12</p>
                                </Card>
                            </div>
                            <div className="mb-8">
                                <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
                                    <div>
                                        <h3 className="text-xl font-extrabold tracking-[-0.02em]">
                                            {t("ui.startACSAClass")}
                                        </h3>
                                        <p className="mt-1 text-sm text-[var(--oq-ink-soft)]">
                                            {t(
                                                "ui.sixteenQuestionsFromTheBankWithOneLiveJavaProblem",
                                            )}
                                        </p>
                                    </div>
                                    {csaBusy !== null && (
                                        <p className="label-caps">{t("ui.buildingTheSet")}</p>
                                    )}
                                </div>
                                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
                                    {CSA_PRESETS.map((preset) => (
                                        <button
                                            key={preset.unit}
                                            type="button"
                                            onClick={() => startCsa(preset.unit)}
                                            disabled={csaBusy !== null}
                                            className="card min-h-[128px] p-4 text-left transition-colors hover:border-[var(--oq-accent)] disabled:opacity-50"
                                        >
                                            <p className="label-caps">
                                                {preset.unit === 5 ? "Mixed" : `CSA ${preset.unit}`}
                                            </p>
                                            <p className="mt-2 font-bold leading-snug">
                                                {preset.title}
                                            </p>
                                            <p className="mt-1 text-xs leading-relaxed text-[var(--oq-ink-soft)]">
                                                {preset.detail}
                                            </p>
                                        </button>
                                    ))}
                                </div>
                            </div>

                            <h3 className="header-double mb-4">{t("ui.quickActions")}</h3>
                            <div className="flex flex-wrap gap-4 items-center">
                                <select
                                    value={gameMode}
                                    onChange={(e) => setGameMode(e.target.value as GameMode)}
                                    className="input-underline text-sm"
                                >
                                    <option value="STANDARD">{t("ui.standard")}</option>
                                    <option value="AUTO_PILOT">{t("ui.autopilot")}</option>
                                    <option value="PRACTICE">{t("ui.practice")}</option>
                                    <option value="EXAM">{t("ui.exam")}</option>
                                    <option value="TEAM">{t("ui.team")}</option>
                                    <option value="BATTLE">{t("ui.battle")}</option>
                                </select>
                                <Button
                                    variant="primary"
                                    onClick={() => {
                                        setTab("quizzes");
                                        setShowCreate(true);
                                    }}
                                >
                                    {t("ui.createQuiz")}
                                </Button>
                                <Button
                                    variant="secondary"
                                    onClick={() => {
                                        if (quizzes.length > 0) host(quizzes[0].id, gameMode);
                                    }}
                                    disabled={quizzes.length === 0 || busy}
                                >
                                    {busy ? t("ui.hostingEllipsis") : t("ui.hostFirstQuiz")}
                                </Button>
                                <Button variant="secondary" onClick={() => setTab("questions")}>
                                    {t("ui.browseQuestions")}
                                </Button>
                            </div>
                            <p className="mt-4 max-w-xl text-xs leading-relaxed text-[var(--oq-ink-soft)]">
                                {t(
                                    "ui.teamModeScoresEveryMembersAnswersIntoASharedTeamTotalBattleModeRuns1v1RoundsWithByesAFullBracketAndAChampionBothAreVerifiedEndToEnd",
                                )}
                            </p>
                        </div>
                    </TabPanel>

                    <TabPanel value="quizzes">
                        <div className="tab-panel" key="quizzes">
                            <div className="flex items-center justify-between mb-4 gap-4 flex-wrap">
                                <h2 className="text-lg font-extrabold mr-auto">
                                    {t("ui.quizSets")}
                                </h2>
                                <select
                                    value={gameMode}
                                    onChange={(e) => setGameMode(e.target.value as GameMode)}
                                    aria-label={t("ui.gameModeForHosting")}
                                    className="input-underline text-sm max-w-[140px]"
                                >
                                    <option value="STANDARD">{t("ui.standard")}</option>
                                    <option value="AUTO_PILOT">{t("ui.autopilot")}</option>
                                    <option value="PRACTICE">{t("ui.practice")}</option>
                                    <option value="EXAM">{t("ui.exam")}</option>
                                    <option value="TEAM">{t("ui.team")}</option>
                                    <option value="BATTLE">{t("ui.battle")}</option>
                                </select>
                                <TextInput
                                    value={quizSearch}
                                    onChange={(e) => setQuizSearch(e.target.value)}
                                    placeholder={t("ui.searchQuizzesEllipsis")}
                                    aria-label={t("ui.searchQuizzes")}
                                    className="text-sm max-w-xs"
                                />
                                <Button
                                    variant="primary"
                                    size="sm"
                                    onClick={() => setShowCreate(true)}
                                >
                                    {t("ui.newSet")}
                                </Button>
                            </div>

                            {showCreate && (
                                <Card className="mb-4">
                                    <h3 className="header-double">{t("ui.createQuestionSet")}</h3>
                                    <div className="flex flex-col sm:flex-row gap-4">
                                        <TextInput
                                            value={title}
                                            onChange={(e) => setTitle(e.target.value)}
                                            placeholder={t("ui.title")}
                                            aria-label={t("ui.quizTitle")}
                                            className="flex-1"
                                        />
                                        <TextInput
                                            value={desc}
                                            onChange={(e) => setDesc(e.target.value)}
                                            placeholder={t("ui.description")}
                                            aria-label={t("ui.quizDescription")}
                                            className="flex-1"
                                        />
                                        <Button
                                            variant="primary"
                                            onClick={async () => {
                                                if (!title.trim()) return;
                                                try {
                                                    await createQuiz(title.trim(), desc.trim());
                                                    setTitle("");
                                                    setDesc("");
                                                    setShowCreate(false);
                                                } catch (e) {
                                                    setBannerError(
                                                        `Could not create the quiz: ${(e as Error)?.message ?? t("ui.unknownError")}`,
                                                    );
                                                }
                                            }}
                                        >
                                            {t("ui.create")}
                                        </Button>
                                    </div>
                                </Card>
                            )}

                            <div ref={gridRef} className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
                                {filteredQuizzes.slice(0, quizLimit).map((q) => (
                                    <Card key={q.id} className="oq-quiz-card">
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
                                                        loadQuestions(q.id).catch(() =>
                                                            setBannerError(
                                                                t(
                                                                    "ui.couldNotLoadQuestionsTryAgain",
                                                                ),
                                                            ),
                                                        );
                                                        setTab("questions");
                                                    }}
                                                >
                                                    {t("ui.questions")}
                                                </Button>
                                                <Button
                                                    variant="primary"
                                                    size="sm"
                                                    disabled={busy}
                                                    onClick={() => host(q.id, gameMode)}
                                                >
                                                    {busy
                                                        ? t("ui.hostingEllipsis")
                                                        : t("ui.hostLabel")}
                                                </Button>
                                                <Button
                                                    variant="secondary"
                                                    size="sm"
                                                    onClick={() => openWizard(q.id)}
                                                >
                                                    {t("ui.add")}
                                                </Button>
                                            </div>
                                        </div>
                                    </Card>
                                ))}
                                {filteredQuizzes.length === 0 && (
                                    <Card className="oq-quiz-card col-span-full">
                                        <div className="text-center py-16">
                                            <p className="label-caps mb-2">
                                                {t("ui.emptyLibrary")}
                                            </p>
                                            <p className="text-[var(--oq-ink-soft)]">
                                                {t("ui.createYourFirstSetOrImportABankJSON")}
                                            </p>
                                        </div>
                                    </Card>
                                )}
                            </div>
                            {filteredQuizzes.length > quizLimit && (
                                <div className="mt-6 flex justify-center">
                                    <Button
                                        variant="secondary"
                                        onClick={() => setQuizLimit((n) => n + 24)}
                                    >
                                        Show more ({filteredQuizzes.length - quizLimit} remaining)
                                    </Button>
                                </div>
                            )}
                        </div>
                    </TabPanel>

                    <TabPanel value="questions">
                        <div className="tab-panel" key="questions">
                            <div className="flex items-center gap-4 mb-4 flex-wrap">
                                <h2 className="text-lg font-extrabold mr-auto">
                                    {t("ui.questions")}
                                </h2>
                                <select
                                    value={activeQuizId ?? ""}
                                    onChange={(e) => {
                                        if (e.target.value)
                                            loadQuestions(e.target.value).catch(() =>
                                                setBannerError(
                                                    t("ui.couldNotLoadQuestionsTryAgain"),
                                                ),
                                            );
                                    }}
                                    aria-label={t("ui.selectQuizSet")}
                                    className="input-underline text-sm max-w-xs"
                                >
                                    <option value="">{t("ui.selectAQuizSet")}</option>
                                    {quizzes.map((q) => (
                                        <option key={q.id} value={q.id}>
                                            {q.title}
                                        </option>
                                    ))}
                                </select>
                                <TextInput
                                    value={search}
                                    onChange={(e) => setSearch(e.target.value)}
                                    placeholder={t("ui.searchEllipsis")}
                                    aria-label={t("ui.searchQuestions")}
                                    className="text-sm max-w-xs"
                                />
                                <Button
                                    variant="primary"
                                    size="sm"
                                    disabled={!activeQuizId}
                                    onClick={() => activeQuizId && openWizard(activeQuizId)}
                                >
                                    {t("ui.addQuestion")}
                                </Button>
                            </div>

                            {filteredQuestions.length > 0 ? (
                                <div className="overflow-x-auto">
                                    <table className="table-dotted w-full">
                                        <thead>
                                            <tr>
                                                <th className="w-12">#</th>
                                                <th>{t("ui.title")}</th>
                                                <th>{t("ui.type")}</th>
                                                <th className="w-20">{t("ui.time")}</th>
                                                <th className="w-20">{t("ui.points")}</th>
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
                                                    <td className="mono text-sm">{q.pointsBase}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            ) : (
                                <div className="text-center py-16 text-[var(--oq-ink-soft)]">
                                    {activeQuizId
                                        ? t("ui.noQuestionsMatchYourSearch")
                                        : t("ui.selectAQuizSetToViewQuestions")}
                                </div>
                            )}
                        </div>
                    </TabPanel>

                    <TabPanel value="games">
                        <div className="tab-panel" key="games">
                            <h2 className="text-lg font-extrabold mb-4">{t("ui.games")}</h2>
                            {quizzes.length === 0 ? (
                                <div className="text-center py-16 text-[var(--oq-ink-soft)]">
                                    <p className="label-caps mb-2">{t("ui.noQuizSetsYet")}</p>
                                    <p>{t("ui.createAQuizSetFirstThenHostAGameFromHere")}</p>
                                </div>
                            ) : (
                                <div className="flex flex-col gap-4">
                                    <div className="flex items-center gap-4 flex-wrap">
                                        <label className="label-caps" htmlFor="games-mode">
                                            {t("ui.mode")}
                                        </label>
                                        <select
                                            id="games-mode"
                                            value={gameMode}
                                            onChange={(e) =>
                                                setGameMode(e.target.value as GameMode)
                                            }
                                            className="input-underline text-sm max-w-[160px]"
                                        >
                                            <option value="STANDARD">{t("ui.standard")}</option>
                                            <option value="AUTO_PILOT">{t("ui.autopilot")}</option>
                                            <option value="PRACTICE">{t("ui.practice")}</option>
                                            <option value="EXAM">{t("ui.exam")}</option>
                                        </select>
                                    </div>
                                    {filteredQuizzes.slice(0, gamesLimit).map((q) => (
                                        <div
                                            key={q.id}
                                            className="flex items-center justify-between gap-4 rounded-[2px] border border-[var(--oq-border)] bg-[var(--oq-surface)] p-6"
                                        >
                                            <div className="min-w-0">
                                                <p className="font-bold truncate" title={q.title}>
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
                                                {busy ? t("ui.hostingEllipsis") : t("ui.hostGame")}
                                            </Button>
                                        </div>
                                    ))}
                                    {filteredQuizzes.length === 0 && (
                                        <p className="text-[var(--oq-ink-soft)] text-sm">
                                            {t("ui.noQuizSetsMatchYourSearch")}
                                        </p>
                                    )}
                                    {filteredQuizzes.length > gamesLimit && (
                                        <div className="flex justify-center pt-2">
                                            <Button
                                                variant="secondary"
                                                onClick={() => setGamesLimit((n) => n + 24)}
                                            >
                                                Show more ({filteredQuizzes.length - gamesLimit}{" "}
                                                remaining)
                                            </Button>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    </TabPanel>

                    <TabPanel value="settings">
                        <SettingsTab key="settings-panel" />
                    </TabPanel>
                </Tabs>
            </main>

            {wizardOpen && <QuestionWizard />}
        </div>
    );
}
