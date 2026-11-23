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
        onSuccess: (report) => queryClient.setQueryData(["player-profile", report.profile.id], report),
    });

    const link = useMutation({
        mutationFn: () => updateProfile(identity!, { playerUuid: playerUuid!, rejoinToken: rejoinToken! }),
        onSuccess: (report) => queryClient.setQueryData(["player-profile", report.profile.id], report),
    });

    const discard = () => {
        forgetIdentity();
        setIdentity(null);
    };

    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6">
                <div className="max-w-2xl">
                    <p className="label-caps mb-3">Player progress</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">Your profile</h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        Joining a game stays anonymous. A profile is optional: it keeps your nickname, score history,
                        achievements, and the quizzes you have finished.
                    </p>
                </div>

                {!identity && (
                    <Card className="p-6 max-w-lg flex flex-col gap-4">
                        <h2 className="text-xl font-extrabold">Create a profile</h2>
                        <p className="text-sm text-[var(--oq-ink-soft)]">
                            Choose a nickname. If you have just played, this device can also link that game identity so
                            your existing results appear here.
                        </p>
                        <Field label="Nickname" htmlFor="profile-nickname">
                            <TextInput
                                id="profile-nickname"
                                value={newNickname}
                                maxLength={20}
                                autoComplete="nickname"
                                onChange={(e) => setNewNickname(e.target.value)}
                                placeholder="Your nickname"
                            />
                        </Field>
                        {create.isError && (
                            <p className="text-sm text-[var(--oq-danger)]" role="alert">
                                Could not create a profile: {String(create.error.message)}. Check the nickname and try
                                again.
                            </p>
                        )}
                        <div>
                            <Button disabled={create.isPending || !newNickname.trim()} onClick={() => create.mutate()}>
                                {create.isPending ? "Creating…" : "Create profile"}
                            </Button>
                        </div>
                    </Card>
                )}

                {identity && isPending && (
                    <div className="grid gap-4 md:grid-cols-2" aria-label="Loading profile">
                        <Skeleton className="h-28" />
                        <Skeleton className="h-28" />
                        <Skeleton className="h-56" />
                        <Skeleton className="h-56" />
                    </div>
                )}

                {identity && isError && (
                    <Card className="p-4 max-w-lg">
                        <EmptyState
                            title="Could not load your profile"
                            hint="The saved link may have expired. Retry, or forget it on this device."
                            action={
                                <div className="flex flex-wrap justify-center gap-2">
                                    <Button variant="secondary" onClick={() => refetch()}>
                                        Retry
                                    </Button>
                                    <Button variant="ghost" onClick={discard}>
                                        Forget this device
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
                                <p className="label-caps">Nickname</p>
                                <h2 className="text-2xl font-extrabold">{data.profile.nickname}</h2>
                                <p className="text-sm text-[var(--oq-ink-soft)] mt-1">
                                    {data.stats.sessions} quiz{data.stats.sessions === 1 ? "" : "zes"} ·{" "}
                                    {data.stats.totalScore} points · {data.stats.correct}/{data.stats.answers} correct
                                </p>
                            </div>
                            <div className="flex flex-wrap gap-2">
                                {playerUuid && rejoinToken && data.profile.playerUuid !== playerUuid && (
                                    <Button variant="secondary" size="sm" disabled={link.isPending}
                                        onClick={() => link.mutate()}>
                                        Link this game identity
                                    </Button>
                                )}
                                <Button variant="ghost" size="sm" onClick={discard}>
                                    Forget this device
                                </Button>
                            </div>
                        </Card>

                        {(rename.isError || link.isError) && (
                            <p className="text-sm text-[var(--oq-danger)]" role="alert">
                                Could not save that change. The game identity may no longer be active.
                            </p>
                        )}

                        <section className="flex flex-col gap-3">
                            <h2 className="text-xl font-extrabold">Achievements</h2>
                            <div className="grid gap-3 md:grid-cols-2">
                                {data.achievements.map((achievement) => (
                                    <Card key={achievement.id} className="p-4 flex flex-col gap-2">
                                        <div className="flex items-center justify-between gap-2">
                                            <h3 className="font-extrabold">{achievement.title}</h3>
                                            <Chip tone={achievement.unlocked ? "success" : "neutral"}>
                                                {achievement.unlocked ? "Unlocked" : "Locked"}
                                            </Chip>
                                        </div>
                                        <p className="text-sm text-[var(--oq-ink-soft)]">{achievement.description}</p>
                                    </Card>
                                ))}
                            </div>
                        </section>

                        <div className="grid gap-6 lg:grid-cols-2">
                            <section className="flex flex-col gap-3">
                                <h2 className="text-xl font-extrabold">Score history</h2>
                                {data.history.length === 0 ? (
                                    <Card className="p-4">
                                        <EmptyState
                                            title="No results linked yet"
                                            hint="Link the game identity from your last session to bring in past scores."
                                        />
                                    </Card>
                                ) : (
                                    <Card className="divide-y divide-[var(--oq-line)]">
                                        {data.history.map((entry) => (
                                            <div key={entry.sessionId} className="p-4 flex justify-between gap-3">
                                                <div>
                                                    <p className="font-bold">{entry.quizTitle ?? "Quiz"}</p>
                                                    <p className="text-xs text-[var(--oq-ink-soft)]">
                                                        {new Date(entry.playedAt).toLocaleDateString()} ·{" "}
                                                        {entry.correct}/{entry.answers} correct
                                                    </p>
                                                </div>
                                                <p className="font-extrabold">{entry.score} pts</p>
                                            </div>
                                        ))}
                                    </Card>
                                )}
                            </section>

                            <section className="flex flex-col gap-3">
                                <h2 className="text-xl font-extrabold">Quiz portfolio</h2>
                                {data.portfolio.length === 0 ? (
                                    <Card className="p-4">
                                        <EmptyState title="No quizzes yet" hint="Finish a quiz to start your portfolio." />
                                    </Card>
                                ) : (
                                    <Card className="divide-y divide-[var(--oq-line)]">
                                        {data.portfolio.map((entry) => (
                                            <div key={entry.quizId} className="p-4 flex justify-between gap-3">
                                                <div>
                                                    <p className="font-bold">{entry.quizTitle ?? "Quiz"}</p>
                                                    <p className="text-xs text-[var(--oq-ink-soft)]">
                                                        {entry.sessions} attempt{entry.sessions === 1 ? "" : "s"} · best{" "}
                                                        {entry.bestScore} pts
                                                    </p>
                                                </div>
                                                <p className="text-xs text-[var(--oq-ink-soft)]">
                                                    {new Date(entry.lastPlayedAt).toLocaleDateString()}
                                                </p>
                                            </div>
                                        ))}
                                    </Card>
                                )}
                            </section>
                        </div>

                        <Card className="p-6 max-w-lg flex flex-col gap-3">
                            <h2 className="font-extrabold">Change nickname</h2>
                            <Field label="Nickname" htmlFor="profile-rename">
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
                                    Save
                                </Button>
                            </div>
                        </Card>
                    </>
                )}
            </div>
        </Shell>
    );
}
