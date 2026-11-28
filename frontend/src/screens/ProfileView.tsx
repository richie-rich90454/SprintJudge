import { useI18n } from "../i18n";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Shell } from "../components/Shell";
import { Card } from "../ui/Card";
import { Button } from "../ui/Button";
import { TextInput } from "../ui/TextInput";
import { Chip, EmptyState, Field, Skeleton } from "../ui/Primitives";
import { useGameStore } from "../stores/useGameStore";
import {
    createProfile,
    fetchProfile,
    forgetIdentity,
    storedIdentity,
    storeIdentity,
    updateProfile,
    type ProfileReport,
} from "../services/ProfileApiService";

export function ProfileView() {
    const { t } = useI18n();
    const queryClient = useQueryClient();
    const [identity, setIdentity] = useState(storedIdentity);
    const gameName = useGameStore((s) => s.playerName);
    const playerUuid = useGameStore((s) => s.playerUuid);
    const rejoinToken = useGameStore((s) => s.rejoinToken);
    const [newNickname, setNewNickname] = useState(gameName ?? "");
    const [renameText, setRenameText] = useState("");

    const { data, isPending, isError, refetch } = useQuery({
        queryKey: ["player-profile", identity?.id],
        queryFn: () => fetchProfile(identity!),
        enabled: Boolean(identity),
    });

    const remember = (report: ProfileReport) => {
        const next = { id: report.profile.id, token: report.profile.linkToken };
        storeIdentity(next);
        setIdentity(next);
        queryClient.setQueryData(["player-profile", next.id], report);
    };

    const create = useMutation({
        mutationFn: () =>
            createProfile(newNickname, playerUuid ?? undefined, rejoinToken ?? undefined),
        onSuccess: remember,
    });

    const rename = useMutation({
        mutationFn: (nickname: string) => updateProfile(identity!, { nickname }),
        onSuccess: (report) =>
            queryClient.setQueryData(["player-profile", report.profile.id], report),
    });

    const link = useMutation({
        mutationFn: () =>
            updateProfile(identity!, { playerUuid: playerUuid!, rejoinToken: rejoinToken! }),
        onSuccess: (report) =>
            queryClient.setQueryData(["player-profile", report.profile.id], report),
    });

    const discard = () => {
        forgetIdentity();
        setIdentity(null);
    };

    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6">
                <div className="max-w-2xl">
                    <p className="label-caps mb-3">{t("ui.playerProgress")}</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">
                        {t("ui.yourProfile")}
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        {t(
                            "ui.joiningAGameStaysAnonymousAProfileIsOptionalItKeepsYourNicknameScoreHistoryAchievementsAndTheQuizzesYouHaveFinished",
                        )}
                    </p>
                </div>

                {!identity && (
                    <Card className="p-6 max-w-lg flex flex-col gap-4">
                        <h2 className="text-xl font-extrabold">{t("ui.createAProfile")}</h2>
                        <p className="text-sm text-[var(--oq-ink-soft)]">
                            {t(
                                "ui.chooseANicknameIfYouHaveJustPlayedThisDeviceCanAlsoLinkThatGameIdentitySoYourExistingResultsAppearHere",
                            )}
                        </p>
                        <Field label={t("ui.nickname")} htmlFor="profile-nickname">
                            <TextInput
                                id="profile-nickname"
                                value={newNickname}
                                maxLength={20}
                                autoComplete="nickname"
                                onChange={(e) => setNewNickname(e.target.value)}
                                placeholder={t("ui.yourNickname")}
                            />
                        </Field>
                        {create.isError && (
                            <p className="text-sm text-[var(--oq-danger)]" role="alert">
                                Could not create a profile:{" "}
                                {create.error instanceof Error
                                    ? create.error.message
                                    : t("ui.unknownError")}
                                . Check the nickname and try again.
                            </p>
                        )}
                        <div>
                            <Button
                                disabled={create.isPending || !newNickname.trim()}
                                onClick={() => create.mutate()}
                            >
                                {create.isPending
                                    ? t("ui.creatingEllipsis")
                                    : t("ui.createProfile")}
                            </Button>
                        </div>
                    </Card>
                )}

                {identity && isPending && (
                    <div className="grid gap-4 md:grid-cols-2" aria-label={t("ui.loadingProfile")}>
                        <Skeleton className="h-28" />
                        <Skeleton className="h-28" />
                        <Skeleton className="h-56" />
                        <Skeleton className="h-56" />
                    </div>
                )}

                {identity && isError && (
                    <Card className="p-4 max-w-lg">
                        <EmptyState
                            title={t("ui.couldNotLoadYourProfile")}
                            hint={t("ui.theSavedLinkMayHaveExpiredRetryOrForgetItOnThisDevice")}
                            action={
                                <div className="flex flex-wrap justify-center gap-2">
                                    <Button variant="secondary" onClick={() => refetch()}>
                                        {t("ui.retry")}
                                    </Button>
                                    <Button variant="ghost" onClick={discard}>
                                        {t("ui.forgetThisDevice")}
                                    </Button>
                                </div>
                            }
                        />
                    </Card>
                )}

                {identity && data && (
                    <>
                        <Card className="p-6 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                            <div>
                                <p className="label-caps">{t("ui.nickname")}</p>
                                <h2 className="text-2xl font-extrabold">{data.profile.nickname}</h2>
                                <p className="text-sm text-[var(--oq-ink-soft)] mt-1">
                                    {data.stats.sessions} quiz
                                    {data.stats.sessions === 1 ? "" : "zes"} |{" "}
                                    {data.stats.totalScore} points | {data.stats.correct}/
                                    {data.stats.answers} correct
                                </p>
                            </div>
                            <div className="flex flex-wrap gap-2">
                                {playerUuid &&
                                    rejoinToken &&
                                    data.profile.playerUuid !== playerUuid && (
                                        <Button
                                            variant="secondary"
                                            size="sm"
                                            disabled={link.isPending}
                                            onClick={() => link.mutate()}
                                        >
                                            {t("ui.linkThisGameIdentity")}
                                        </Button>
                                    )}
                                <Button variant="ghost" size="sm" onClick={discard}>
                                    {t("ui.forgetThisDevice")}
                                </Button>
                            </div>
                        </Card>

                        {(rename.isError || link.isError) && (
                            <p className="text-sm text-[var(--oq-danger)]" role="alert">
                                {t("ui.couldNotSaveThatChangeTheGameIdentityMayNoLongerBeActive")}
                            </p>
                        )}

                        <section className="flex flex-col gap-3">
                            <h2 className="text-xl font-extrabold">{t("ui.achievements")}</h2>
                            <div className="grid gap-3 md:grid-cols-2">
                                {data.achievements.map((achievement) => (
                                    <Card key={achievement.id} className="p-4 flex flex-col gap-2">
                                        <div className="flex items-center justify-between gap-2">
                                            <h3 className="font-extrabold">{achievement.title}</h3>
                                            <Chip
                                                tone={achievement.unlocked ? "success" : "neutral"}
                                            >
                                                {achievement.unlocked
                                                    ? t("ui.unlocked")
                                                    : t("ui.locked")}
                                            </Chip>
                                        </div>
                                        <p className="text-sm text-[var(--oq-ink-soft)]">
                                            {achievement.description}
                                        </p>
                                    </Card>
                                ))}
                            </div>
                        </section>

                        <div className="grid gap-6 lg:grid-cols-2">
                            <section className="flex flex-col gap-3">
                                <h2 className="text-xl font-extrabold">{t("ui.scoreHistory")}</h2>
                                {data.history.length === 0 ? (
                                    <Card className="p-4">
                                        <EmptyState
                                            title={t("ui.noResultsLinkedYet")}
                                            hint={t(
                                                "ui.linkTheGameIdentityFromYourLastSessionToBringInPastScores",
                                            )}
                                        />
                                    </Card>
                                ) : (
                                    <Card className="divide-y divide-[var(--oq-line)]">
                                        {data.history.map((entry) => (
                                            <div
                                                key={entry.sessionId}
                                                className="p-4 flex justify-between gap-3"
                                            >
                                                <div>
                                                    <p className="font-bold">
                                                        {entry.quizTitle ?? t("ui.quiz")}
                                                    </p>
                                                    <p className="text-xs text-[var(--oq-ink-soft)]">
                                                        {new Date(
                                                            entry.playedAt,
                                                        ).toLocaleDateString()}{" "}
                                                        | {entry.correct}/{entry.answers} correct
                                                    </p>
                                                </div>
                                                <p className="font-extrabold">{entry.score} pts</p>
                                            </div>
                                        ))}
                                    </Card>
                                )}
                            </section>

                            <section className="flex flex-col gap-3">
                                <h2 className="text-xl font-extrabold">{t("ui.quizPortfolio")}</h2>
                                {data.portfolio.length === 0 ? (
                                    <Card className="p-4">
                                        <EmptyState
                                            title={t("ui.noQuizzesYet")}
                                            hint={t("ui.finishAQuizToStartYourPortfolio")}
                                        />
                                    </Card>
                                ) : (
                                    <Card className="divide-y divide-[var(--oq-line)]">
                                        {data.portfolio.map((entry) => (
                                            <div
                                                key={entry.quizId}
                                                className="p-4 flex justify-between gap-3"
                                            >
                                                <div>
                                                    <p className="font-bold">
                                                        {entry.quizTitle ?? t("ui.quiz")}
                                                    </p>
                                                    <p className="text-xs text-[var(--oq-ink-soft)]">
                                                        {entry.sessions} attempt
                                                        {entry.sessions === 1 ? "" : "s"} | best{" "}
                                                        {entry.bestScore} pts
                                                    </p>
                                                </div>
                                                <p className="text-xs text-[var(--oq-ink-soft)]">
                                                    {new Date(
                                                        entry.lastPlayedAt,
                                                    ).toLocaleDateString()}
                                                </p>
                                            </div>
                                        ))}
                                    </Card>
                                )}
                            </section>
                        </div>

                        <Card className="p-6 max-w-lg flex flex-col gap-3">
                            <h2 className="font-extrabold">{t("ui.changeNickname")}</h2>
                            <Field label={t("ui.nickname")} htmlFor="profile-rename">
                                <TextInput
                                    id="profile-rename"
                                    value={renameText}
                                    maxLength={20}
                                    onChange={(e) => setRenameText(e.target.value)}
                                    placeholder={data.profile.nickname}
                                />
                            </Field>
                            <div>
                                <Button
                                    size="sm"
                                    variant="secondary"
                                    disabled={rename.isPending || !renameText.trim()}
                                    onClick={() => rename.mutate(renameText)}
                                >
                                    {t("ui.save")}
                                </Button>
                            </div>
                        </Card>
                    </>
                )}
            </div>
        </Shell>
    );
}
