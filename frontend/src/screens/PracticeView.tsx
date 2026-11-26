import { useI18n } from "../i18n";
import { useState, type FormEvent } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { Shell } from "../components/Shell";
import { Card } from "../ui/Card";
import { Button } from "../ui/Button";
import { TextInput } from "../ui/TextInput";
import { Field, EmptyState, Skeleton } from "../ui/Primitives";
import { useGameStore } from "../stores/useGameStore";
import { useUIStore } from "../stores/useUIStore";
import { audio } from "../services/AudioEngine";
import { adminApi } from "../services/AdminApiService";

/**
 * Solo practice: pick a question bank, type a name, drill. The server
 * spins up a PRACTICE-mode room on the chosen bank — no PIN to hunt down,
 * no admin area involved.
 */
export function PracticeView() {
    const { t } = useI18n();
    const [name, setName] = useState("");
    // An Explore card can preselect its bank via ?bank=<id>.
    const [bankId, setBankId] = useState<string | null>(() =>
        new URLSearchParams(window.location.search).get("bank"),
    );
    const [query, setQuery] = useState("");
    const [limit, setLimit] = useState(20);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const connect = useGameStore((s) => s.connect);
    const join = useGameStore((s) => s.join);
    const setPin = useUIStore((s) => s.setPin);
    const navigate = useNavigate();
    const {
        data: banks,
        isPending,
        isError,
        refetch,
    } = useQuery({
        queryKey: ["banks"],
        queryFn: () => adminApi.listBanks(),
    });

    const canSubmit = name.trim().length > 0 && bankId !== null && !busy;
    const needle = query.trim().toLowerCase();
    const filteredBanks = (banks ?? []).filter(
        (bank) =>
            !needle ||
            bank.title.toLowerCase().includes(needle) ||
            (bank.description ?? "").toLowerCase().includes(needle),
    );

    const onSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (!canSubmit) return;
        setBusy(true);
        setError(null);
        try {
            const { pinCode } = await adminApi.startPractice(bankId);
            const proto = location.protocol === "https:" ? "wss" : "ws";
            connect(`${proto}://${location.host}/ws`);
            join(pinCode, name.trim());
            setPin(pinCode);
            audio.play("start");
            navigate({ to: "/play" });
        } catch {
            setError(t("ui.couldNotStartAPracticeSetTryAgain"));
            setBusy(false);
        }
    };

    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6 max-w-xl">
                <div>
                    <p className="label-caps mb-3">{t("ui.soloPractice")}</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">
                        {t("ui.drillAtYourOwnPace")}
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        {t(
                            "ui.pickAQuestionBankFirstUntimedWithInstantFeedbackAfterEveryAnswerAndAutomaticAdvanceToTheNextQuestionNoHostNoWaiting",
                        )}
                    </p>
                </div>
                <Card className="p-6">
                    <form onSubmit={onSubmit} className="flex flex-col gap-5 text-left">
                        <fieldset>
                            <legend className="label-caps mb-3">{t("ui.questionBank")}</legend>
                            <TextInput
                                value={query}
                                onChange={(e) => {
                                    setQuery(e.target.value);
                                    setLimit(20);
                                }}
                                placeholder={t("ui.searchBanksEllipsis")}
                                aria-label={t("ui.searchQuestionBanks")}
                                className="mb-3 text-sm"
                            />
                            {isPending && (
                                <div
                                    className="flex flex-col gap-2"
                                    aria-label={t("ui.loadingBanks")}
                                >
                                    <Skeleton className="h-14" />
                                    <Skeleton className="h-14" />
                                </div>
                            )}
                            {isError && (
                                <EmptyState
                                    title={t("ui.couldNotLoadBanks")}
                                    hint={t("ui.checkYourConnectionAndTryAgain")}
                                    action={
                                        <Button variant="secondary" onClick={() => refetch()}>
                                            {t("ui.retry")}
                                        </Button>
                                    }
                                />
                            )}
                            {banks && banks.length === 0 && (
                                <EmptyState
                                    title={t("ui.noQuestionBanksYet")}
                                    hint={t("ui.askYourTeacherToPublishOne")}
                                />
                            )}
                            {banks && banks.length > 0 && filteredBanks.length === 0 && (
                                <p className="text-sm text-[var(--oq-ink-soft)]">
                                    {t("ui.noBanksMatchThatSearch")}
                                </p>
                            )}
                            {filteredBanks.length > 0 && (
                                <div className="flex flex-col gap-2" role="radiogroup">
                                    {filteredBanks.slice(0, limit).map((bank) => (
                                        <label
                                            key={bank.id}
                                            className={`flex items-start gap-3 p-3 border rounded-[2px] cursor-pointer ${
                                                bankId === bank.id
                                                    ? "border-[var(--oq-accent)]"
                                                    : "border-[var(--oq-border)]"
                                            }`}
                                        >
                                            <input
                                                type="radio"
                                                name="bank"
                                                value={bank.id}
                                                checked={bankId === bank.id}
                                                onChange={() => setBankId(bank.id)}
                                                className="mt-1"
                                            />
                                            <span>
                                                <span className="block font-bold">
                                                    {bank.title}
                                                </span>
                                                {bank.description && (
                                                    <span className="block text-sm text-[var(--oq-ink-soft)]">
                                                        {bank.description}
                                                    </span>
                                                )}
                                            </span>
                                        </label>
                                    ))}
                                </div>
                            )}
                            {filteredBanks.length > limit && (
                                <div className="mt-3 flex justify-center">
                                    <Button
                                        variant="secondary"
                                        size="sm"
                                        onClick={() => setLimit((n) => n + 20)}
                                    >
                                        Show more ({filteredBanks.length - limit} remaining)
                                    </Button>
                                </div>
                            )}
                        </fieldset>
                        <Field label={t("ui.yourName")} htmlFor="practice-name">
                            <TextInput
                                id="practice-name"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                placeholder={t("ui.alice")}
                                maxLength={20}
                                autoComplete="nickname"
                            />
                        </Field>
                        {error && (
                            <p role="alert" className="text-[var(--oq-danger)] text-sm">
                                {error}
                            </p>
                        )}
                        <Button
                            type="submit"
                            size="lg"
                            disabled={!canSubmit}
                            className="w-full font-bold"
                        >
                            {busy ? t("ui.startingEllipsis") : t("ui.startPracticing")}
                        </Button>
                    </form>
                </Card>
            </div>
        </Shell>
    );
}
