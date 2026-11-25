import { useI18n } from "../i18n";
import { Link } from "@tanstack/react-router";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Shell } from "../components/Shell";
import { Card } from "../ui/Card";
import { Button } from "../ui/Button";
import { TextInput } from "../ui/TextInput";
import { EmptyState, Skeleton } from "../ui/Primitives";
import { adminApi } from "../services/AdminApiService";

export function ExploreView() {
    const { t } = useI18n();
    const [query, setQuery] = useState("");
    const [limit, setLimit] = useState(24);
    const { data, isPending, isError, refetch } = useQuery({
        queryKey: ["banks"],
        queryFn: () => adminApi.listBanks(),
    });

    const needle = query.trim().toLowerCase();
    const visible = (data ?? []).filter(
        (quiz) =>
            !needle ||
            quiz.title.toLowerCase().includes(needle) ||
            (quiz.description ?? "").toLowerCase().includes(needle),
    );

    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6">
                <div className="max-w-2xl">
                    <p className="label-caps mb-3">{t("ui.quizLibrary")}</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">
                        {t("ui.browseQuizzes")}
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        {t("ui.drillAnyBankSoloAtYourOwnPaceOrJoinALiveGameWithAPINFromYourHost")}
                    </p>
                </div>
                <div className="max-w-md">
                    <TextInput
                        value={query}
                        onChange={(e) => {
                            setQuery(e.target.value);
                            setLimit(24);
                        }}
                        placeholder={t("ui.searchQuizzesEllipsis")}
                        aria-label={t("ui.searchQuizzes")}
                    />
                </div>
                {isPending && (
                    <div className="grid gap-4 md:grid-cols-2" aria-label={t("ui.loadingQuizzes")}>
                        <Skeleton className="h-36" />
                        <Skeleton className="h-36" />
                        <Skeleton className="h-36" />
                    </div>
                )}
                {isError && (
                    <Card className="p-4">
                        <EmptyState
                            title={t("ui.couldNotLoadTheLibrary")}
                            hint={t("ui.checkYourConnectionAndTryAgain")}
                            action={
                                <Button variant="secondary" onClick={() => refetch()}>
                                    {t("ui.retry")}
                                </Button>
                            }
                        />
                    </Card>
                )}
                {data && data.length === 0 && (
                    <Card className="p-4">
                        <EmptyState
                            title={t("ui.noQuizzesYet")}
                            hint={t("ui.askYourTeacherToPublishOne")}
                        />
                    </Card>
                )}
                {data && data.length > 0 && visible.length === 0 && (
                    <Card className="p-4">
                        <EmptyState
                            title={t("ui.noMatches")}
                            hint={t("ui.noMatchesHint", { query: query.trim() })}
                        />
                    </Card>
                )}
                {visible.length > 0 && (
                    <div>
                        <p className="label-caps mb-3" aria-live="polite">
                            {visible.length} of {data?.length ?? 0} quizzes
                        </p>
                        <div className="grid gap-4 md:grid-cols-2">
                            {visible.slice(0, limit).map((quiz) => (
                                <Card key={quiz.id} className="p-6 flex flex-col gap-2">
                                    <h2 className="text-lg font-extrabold">{quiz.title}</h2>
                                    {quiz.description && (
                                        <p className="text-sm text-[var(--oq-ink-soft)] leading-relaxed">
                                            {quiz.description}
                                        </p>
                                    )}
                                    <div className="flex flex-wrap gap-2 mt-3">
                                        <Link
                                            to="/practice"
                                            search={{ bank: quiz.id }}
                                            className="btn btn-primary btn-sm font-bold"
                                        >
                                            {t("ui.practiceSolo")}
                                        </Link>
                                        <Link
                                            to="/join"
                                            className="btn btn-secondary btn-sm font-bold"
                                        >
                                            {t("ui.joinWithPIN")}
                                        </Link>
                                    </div>
                                </Card>
                            ))}
                        </div>
                        {visible.length > limit && (
                            <div className="mt-6 flex justify-center">
                                <Button variant="secondary" onClick={() => setLimit((n) => n + 24)}>
                                    {t("ui.showMore", { count: visible.length - limit })}
                                </Button>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </Shell>
    );
}
