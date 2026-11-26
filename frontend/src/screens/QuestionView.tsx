import { useI18n } from "../i18n";
import { useEffect, useRef, useState } from "react";
import { Link, Navigate } from "@tanstack/react-router";
import { useGameStore } from "../stores/useGameStore";
import { useTimerStore } from "../stores/useTimerStore";
import { QuestionRendererHost } from "../components/QuestionRendererHost";
import { CircularTimer } from "../components/Timer/CircularTimer";
import { isCoding } from "../game/ScoringService";
import { audio } from "../services/AudioEngine";
import { ErrorBoundary } from "../components/ErrorBoundary";
import { Button } from "../ui/Button";
import { Card } from "../ui/Card";
import { Chip } from "../ui/Primitives";
import { AboutDialog } from "../components/AboutDialog";
import { QuestionPreview } from "../components/host/QuestionPreview";
import { FormattedText } from "../components/host/FormattedText";
import { useStageEnter } from "../motion/useStageMotion";
import type { RoundResult, TeamInfo } from "../types";

export function QuestionView() {
    const { t } = useI18n();
    const q = useGameStore((s) => s.currentQuestion);
    const status = useGameStore((s) => s.status);
    const gameMode = useGameStore((s) => s.gameMode);
    const roomMode = useGameStore((s) => s.room?.gameMode);
    const joined = useGameStore((s) => s.playerUuid !== null);
    const hasPin = useGameStore((s) => s.pin !== null);
    const resume = useGameStore((s) => s.resume);
    const submit = useGameStore((s) => s.submit);
    const wsError = useGameStore((s) => s.error);
    const lastResult = useGameStore((s) => s.lastResult);
    const round =
        lastResult && "scores" in lastResult ? (lastResult as unknown as RoundResult) : null;
    const end = useTimerStore((s) => s.endEpochMs);
    const totalSec = useTimerStore((s) => s.totalSec);
    const teams = useGameStore((s) => s.teams);
    const myUuid = useGameStore((s) => s.playerUuid);
    const bracket = useGameStore((s) => s.bracket);
    const battleOpponent = useGameStore((s) => s.battleOpponent);
    const battleResult = useGameStore((s) => s.battleResult);
    const champion = useGameStore((s) => s.champion);
    const roomPlayers = useGameStore((s) => s.room?.players);
    const joinTeam = useGameStore((s) => s.joinTeam);
    const leaveTeam = useGameStore((s) => s.leaveTeam);
    const [response, setResponse] = useState<unknown>(null);
    const [submitted, setSubmitted] = useState(false);
    // Ref mirror: a click and the timer expiry in the same tick both read
    // stale state, so the ref (not the state) owns the once-only guard.
    const submittedRef = useRef(false);
    const [feedback, setFeedback] = useState<null | { ok: boolean; text: string }>(null);
    const [shake, setShake] = useState(false);
    const shakeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const resumeTried = useRef(false);
    const stageRef = useStageEnter<HTMLDivElement>([q?.id, status]);

    const revealSignal = status === "REVIEW" ? 1 : 0;

    useEffect(() => {
        if (resumeTried.current || hasPin) return;
        resumeTried.current = true;
        resume();
    }, [hasPin, resume]);

    useEffect(() => {
        setSubmitted(false);
        submittedRef.current = false;
        setResponse(null);
        setFeedback(null);
        setShake(false);
        if (shakeTimer.current) {
            clearTimeout(shakeTimer.current);
            shakeTimer.current = null;
        }
    }, [q?.id]);

    // Cleanup shake timer on unmount
    useEffect(() => {
        return () => {
            if (shakeTimer.current) clearTimeout(shakeTimer.current);
        };
    }, []);

    // React to submission result: play sfx + show feedback, no forced lock-in.
    useEffect(() => {
        if (!q) return;
        const sub = lastResult?.submission;
        if (!sub || sub.questionId !== q.id) return;
        // Only a server ack makes the draft disposable; queued/failed sends
        // must survive a refresh so the player can retry.
        try {
            localStorage.removeItem(`sprintjudge_code_${q.id}`);
        } catch {
            /* ignore */
        }
        const ok = sub.allPassed === true;
        const aiNote = sub.aiFeedback ? ` - ${sub.aiFeedback}` : "";
        setFeedback({
            ok,
            text: isCoding(q.type)
                ? `${sub.passed ?? 0}/${sub.totalTests ?? 0} tests passed, +${sub.score ?? 0}${aiNote}`
                : ok
                  ? `+${sub.score} pts`
                  : t("ui.notQuite"),
        });
        if (ok) {
            audio.play("correct");
        } else {
            audio.play("wrong");
            setShake(true);
            if (shakeTimer.current) clearTimeout(shakeTimer.current);
            shakeTimer.current = setTimeout(() => {
                setShake(false);
                shakeTimer.current = null;
            }, 400);
        }
    }, [lastResult, q?.id]);

    if (status === "ENDED") {
        return <Navigate to="/results" />;
    }

    const myTeam =
        teams.find((team) => (myUuid ? team.memberUuids.includes(myUuid) : false)) ?? null;
    const myBattleMatch =
        battleResult?.matches.find(
            (m) => myUuid !== null && (m.p1Uuid === myUuid || m.p2Uuid === myUuid),
        ) ?? null;
    const battleWon = myBattleMatch !== null && myBattleMatch.winnerUuid === myUuid;
    const currentBattleRound = bracket?.rounds?.[bracket.rounds.length - 1] ?? null;
    const myPair =
        currentBattleRound?.pairs.find(
            (p) => myUuid !== null && (p.p1Uuid === myUuid || p.p2Uuid === myUuid),
        ) ?? null;
    const opponentName =
        battleOpponent?.opponentName ||
        (myPair && myUuid
            ? roomPlayers?.find(
                  (p) => p.uuid === (myPair.p1Uuid === myUuid ? myPair.p2Uuid : myPair.p1Uuid),
              )?.name
            : null) ||
        null;

    if (status === "LOBBY" && gameMode === "TEAM") {
        return (
            <div className="pattern-exam min-h-[100dvh] flex flex-col">
                <header className="border-b border-[var(--oq-border)] bg-[var(--oq-surface)]">
                    <div className="page-shell flex items-center justify-between gap-4 py-3">
                        <p className="label-caps">{t("ui.teamLobby")}</p>
                        <span className="mono text-sm text-[var(--oq-ink-soft)]">
                            {teams.length}{" "}
                            {teams.length === 1 ? t("ui.teamSingular") : t("ui.teamsPlural")}
                        </span>
                    </div>
                </header>
                <main className="page-shell w-full max-w-3xl flex-1 py-8">
                    <Card className="p-6">
                        <h1 className="text-2xl font-black tracking-[-0.02em]">
                            {t("ui.pickYourTeam")}
                        </h1>
                        <p className="mt-2 text-sm text-[var(--oq-ink-soft)]">
                            {t("ui.scoresEarnedByEveryMemberAddToTheTeamTotal")}
                        </p>
                        {teams.length === 0 ? (
                            <p className="mt-6 text-[var(--oq-ink-soft)]">
                                {t("ui.theHostHasNotCreatedTeamsYet")}
                            </p>
                        ) : (
                            <ul className="mt-6 flex flex-col gap-2">
                                {teams.map((team: TeamInfo) => {
                                    const mine = myTeam?.id === team.id;
                                    return (
                                        <li
                                            key={team.id}
                                            className={`flex min-h-[56px] items-center justify-between gap-3 border px-4 py-2 ${
                                                mine
                                                    ? "border-[var(--oq-accent)] bg-[var(--oq-row-alt)]"
                                                    : "border-[var(--oq-border)]"
                                            }`}
                                        >
                                            <span className="min-w-0">
                                                <span className="block truncate font-semibold">
                                                    {team.name}
                                                </span>
                                                <span className="mono text-xs text-[var(--oq-ink-soft)]">
                                                    {team.memberUuids.length}{" "}
                                                    {team.memberUuids.length === 1
                                                        ? t("ui.memberSingular")
                                                        : t("ui.membersPlural")}
                                                </span>
                                            </span>
                                            <Button
                                                variant={mine ? "secondary" : "primary"}
                                                size="sm"
                                                onClick={() =>
                                                    mine ? leaveTeam() : joinTeam(team.id)
                                                }
                                            >
                                                {mine ? t("ui.leave") : t("ui.join")}
                                            </Button>
                                        </li>
                                    );
                                })}
                            </ul>
                        )}
                        {wsError && (
                            <p role="alert" className="mt-4 text-sm text-[var(--oq-danger)]">
                                {wsError}
                            </p>
                        )}
                        <p className="mt-6 border-t border-[var(--oq-border)] pt-4 text-sm text-[var(--oq-ink-soft)]">
                            {myTeam
                                ? t("ui.teamWaiting", { team: myTeam.name })
                                : t("ui.joinATeamBeforeTheHostStartsTheGame")}
                        </p>
                    </Card>
                </main>
            </div>
        );
    }

    if (status === "LOBBY" && gameMode === "BATTLE") {
        return (
            <div className="pattern-exam min-h-[100dvh] flex flex-col">
                <header className="border-b border-[var(--oq-border)] bg-[var(--oq-surface)]">
                    <div className="page-shell flex items-center justify-between gap-4 py-3">
                        <p className="label-caps">{t("ui.battleLobby")}</p>
                        <span className="mono text-sm text-[var(--oq-ink-soft)]">
                            {currentBattleRound
                                ? t("ui.roundNumber", { n: currentBattleRound.number })
                                : t("ui.notStarted")}
                        </span>
                    </div>
                </header>
                <main className="page-shell w-full max-w-3xl flex-1 py-8">
                    <Card className="p-6">
                        <h1 className="text-2xl font-black tracking-[-0.02em]">
                            {myPair?.bye
                                ? t("ui.youHaveABye")
                                : myPair
                                  ? `You face ${opponentName ?? t("ui.yourOpponent")}`
                                  : t("ui.waitingForPairings")}
                        </h1>
                        <p className="mt-2 text-sm text-[var(--oq-ink-soft)]">
                            {myPair?.bye
                                ? t("ui.nobodyToFightThisRoundYouAdvanceAutomatically")
                                : t("ui.winYour1v1ToMoveToTheNextRound")}
                        </p>
                        {bracket && bracket.rounds.length > 0 && (
                            <div className="mt-6 border-t border-[var(--oq-border)] pt-4">
                                <p className="label-caps mb-2">{t("ui.bracket")}</p>
                                <ol className="flex flex-col gap-2 text-sm">
                                    {bracket.rounds.map((r) => (
                                        <li key={r.number} className="flex flex-col gap-1">
                                            <span className="mono text-xs text-[var(--oq-ink-soft)]">
                                                Round {r.number}
                                            </span>
                                            {r.pairs.map((p, i) => (
                                                <span key={i}>
                                                    {roomPlayers?.find((x) => x.uuid === p.p1Uuid)
                                                        ?.name ?? t("ui.playerFallback")}{" "}
                                                    vs{" "}
                                                    {p.bye
                                                        ? t("ui.byeLabel")
                                                        : (roomPlayers?.find(
                                                              (x) => x.uuid === p.p2Uuid,
                                                          )?.name ?? t("ui.playerFallback"))}
                                                </span>
                                            ))}
                                        </li>
                                    ))}
                                </ol>
                            </div>
                        )}
                        {wsError && (
                            <p role="alert" className="mt-4 text-sm text-[var(--oq-danger)]">
                                {wsError}
                            </p>
                        )}
                        <p className="mt-6 border-t border-[var(--oq-border)] pt-4 text-sm text-[var(--oq-ink-soft)]">
                            {t("ui.waitingForTheHostToStartTheBattle")}
                        </p>
                    </Card>
                </main>
            </div>
        );
    }

    if (status === "REVIEW") {
        return (
            <div className="pattern-exam min-h-[100dvh] flex flex-col">
                <header className="border-b border-[var(--oq-border)] bg-[var(--oq-surface)]">
                    <div className="page-shell flex items-center justify-between gap-4 py-3">
                        <p className="label-caps">{t("ui.roundComplete")}</p>
                        {feedback && (
                            <p
                                aria-live="polite"
                                className={`text-sm font-bold ${feedback.ok ? "text-[var(--oq-success)]" : "text-[var(--oq-danger)]"}`}
                            >
                                {feedback.text}
                            </p>
                        )}
                    </div>
                </header>
                <main ref={stageRef} className="page-shell w-full max-w-5xl flex-1 py-8">
                    {q ? (
                        <div className="stage arch p-8 md:p-10">
                            <QuestionPreview question={q} round={round} />
                            {gameMode === "BATTLE" && myBattleMatch && (
                                <p
                                    className={`mt-6 border-l-2 pl-4 text-sm font-bold ${
                                        battleWon
                                            ? "border-[var(--oq-success)] text-[var(--oq-success)]"
                                            : "border-[var(--oq-danger)] text-[var(--oq-danger)]"
                                    }`}
                                >
                                    {myBattleMatch.bye
                                        ? t("ui.byeRoundYouAdvance")
                                        : battleWon
                                          ? `You beat ${opponentName ?? t("ui.yourOpponent")}.`
                                          : t("ui.youLostThisRound")}
                                </p>
                            )}
                            {champion && (
                                <p className="mt-4 text-sm font-bold text-[var(--oq-accent)]">
                                    Champion: {champion.championName}
                                </p>
                            )}
                            {gameMode === "TEAM" && teams.length > 0 && (
                                <div className="mt-8 border-t border-[var(--stage-border)] pt-6">
                                    <p className="label-caps mb-3 text-[var(--stage-ink-soft)]">
                                        {t("ui.teamStandings")}
                                    </p>
                                    <ol className="flex flex-col gap-2">
                                        {teams.map((team, i) => (
                                            <li
                                                key={team.id}
                                                className="flex items-center justify-between gap-4 text-sm"
                                            >
                                                <span className="min-w-0 truncate">
                                                    <span className="mono mr-3 text-[var(--stage-ink-soft)]">
                                                        {i + 1}
                                                    </span>
                                                    {team.name}
                                                </span>
                                                <span className="mono font-bold tabular-nums">
                                                    {(Number.isFinite(team.score)
                                                        ? team.score
                                                        : 0
                                                    ).toLocaleString()}
                                                </span>
                                            </li>
                                        ))}
                                    </ol>
                                </div>
                            )}
                            {round?.correctAnswer &&
                                typeof round.correctAnswer.explanation === "string" && (
                                    <div className="mt-8 border-t border-[var(--stage-border)] pt-6">
                                        <p className="label-caps mb-2 text-[var(--stage-ink-soft)]">
                                            {t("ui.why")}
                                        </p>
                                        <FormattedText
                                            text={round.correctAnswer.explanation}
                                            className="text-[var(--stage-ink-soft)]"
                                        />
                                    </div>
                                )}
                            <p className="mt-8 border-t border-[var(--stage-border)] pt-5 text-sm text-[var(--stage-ink-soft)]">
                                {roomMode === "PRACTICE" || gameMode === "PRACTICE"
                                    ? t("ui.nextQuestionComingUp")
                                    : t("ui.theHostWillBringTheNextQuestionWhenTheRoomIsReady")}
                            </p>
                        </div>
                    ) : (
                        <Card className="p-6 text-center">
                            <p className="text-[var(--oq-ink-soft)]">
                                {t("ui.theHostIsPreparingTheNextRound")}
                            </p>
                        </Card>
                    )}
                </main>
            </div>
        );
    }

    if (!q || end === null) {
        // Pin-less arrival (refresh wiped the session): infinite
        // "connecting" helps nobody — point back to an entry point.
        if (!joined && !hasPin) {
            return (
                <div className="pattern-exam min-h-[100dvh] flex items-center justify-center p-4">
                    <div className="text-center w-full max-w-sm">
                        <p className="label-caps mb-2">{t("ui.noGame")}</p>
                        <p className="text-[var(--oq-ink-soft)] mb-4">{t("ui.notJoined")}</p>
                        <p className="text-sm font-bold">
                            <Link to="/join" className="underline underline-offset-4">
                                {t("ui.joinAGame")}
                            </Link>
                            <span aria-hidden="true" className="mx-3 opacity-60">
                                |
                            </span>
                            <Link to="/practice" className="underline underline-offset-4">
                                {t("ui.practiceSolo")}
                            </Link>
                        </p>
                    </div>
                </div>
            );
        }
        return (
            <div className="pattern-exam min-h-[100dvh] flex items-center justify-center p-4">
                <div className="text-center w-full max-w-sm">
                    <p className="label-caps mb-2">{t("ui.standby")}</p>
                    <div
                        className="mx-auto mb-4 h-3 w-40 animate-pulse rounded-[6px] bg-[var(--oq-border)]"
                        aria-hidden="true"
                    />
                    <div
                        className="mx-auto mb-4 h-3 w-28 animate-pulse rounded-[6px] bg-[var(--oq-border)]"
                        aria-hidden="true"
                    />
                    <p className="text-[var(--oq-ink-soft)]">
                        {!joined
                            ? t("ui.connectingGame")
                            : roomMode === "PRACTICE" || gameMode === "PRACTICE"
                              ? t("ui.preparingQuestion")
                              : t("ui.waitingForTheHostToStartTheNextQuestion")}
                    </p>
                    {wsError && (
                        <p role="alert" className="text-[var(--oq-danger)] text-sm mt-3">
                            {wsError}
                        </p>
                    )}
                    <p className="mt-4 text-sm">
                        <Link to="/join" className="underline underline-offset-4">
                            {t("ui.changePIN")}
                        </Link>
                    </p>
                </div>
            </div>
        );
    }

    const untimed = !isFinite(end);
    const coding = isCoding(q.type);
    const description = q.description && q.description !== q.title ? q.description : null;
    const hasResponse = coding || response !== null;

    const doSubmit = () => {
        if (submittedRef.current) return;
        submittedRef.current = true;
        const lang =
            coding && typeof response === "object" && response !== null && "language" in response
                ? (response as { language?: string }).language
                : undefined;
        submit(q.id, response, lang);
        setSubmitted(true);
        audio.play("click");
    };

    return (
        <div className="pattern-exam min-h-[100dvh] flex flex-col">
            <header className="border-b border-[var(--oq-border)] bg-[var(--oq-surface)]">
                <div className="page-shell flex min-w-0 flex-wrap items-center gap-4 py-3">
                    <span className="label-caps min-w-0 truncate">{q.type.replace(/_/g, " ")}</span>
                    <span className="mono text-sm text-[var(--oq-ink-soft)]">
                        {q.pointsBase} points
                    </span>
                    <div className="flex-1" />
                    <AboutDialog />
                    {gameMode === "BATTLE" && myPair && (
                        <Chip tone="accent">
                            {myPair.bye
                                ? t("ui.byeRound")
                                : `Round ${currentBattleRound!.number}: vs ${
                                      opponentName ?? "opponent"
                                  }`}
                        </Chip>
                    )}
                    {gameMode === "TEAM" && myTeam && <Chip tone="neutral">{myTeam.name}</Chip>}
                    {untimed ? (
                        <span className="chip chip-neutral">{t("ui.untimed")}</span>
                    ) : (
                        <CircularTimer
                            endEpochMs={end}
                            totalSec={totalSec}
                            onExpire={() => doSubmit()}
                        />
                    )}
                </div>
            </header>

            <main
                ref={stageRef}
                className={`page-shell w-full flex-1 py-6 md:py-10 ${
                    coding
                        ? "grid min-h-0 gap-8 overflow-hidden lg:grid-cols-2"
                        : "flex max-w-4xl flex-col"
                }`}
            >
                <section
                    className={`min-w-0 [overflow-wrap:anywhere] ${coding ? "flex flex-col overflow-y-auto" : ""}`}
                    aria-live="polite"
                >
                    <p className="label-caps mb-3">{t("ui.question")}</p>
                    <h1 className="text-3xl font-black leading-[1.08] tracking-[-0.03em] md:text-4xl">
                        {q.title}
                    </h1>
                    {description && (
                        <FormattedText
                            text={description}
                            className="mt-4 text-[var(--oq-ink-soft)] md:text-lg"
                        />
                    )}
                    {coding && (
                        <p className="mt-5 border-l-2 border-[var(--oq-accent)] pl-4 text-sm leading-relaxed text-[var(--oq-ink-soft)]">
                            {t(
                                "ui.writeCodeThatPassesTheHiddenTestsRunItBelowToCheckYourOutputBeforeSubmitting",
                            )}
                        </p>
                    )}
                    {feedback && (
                        <div className="relative">
                            {feedback.ok && (
                                <div className="motes" aria-hidden="true">
                                    {Array.from({ length: 8 }, (_, i) => (
                                        <i
                                            key={i}
                                            style={{
                                                left: `${(i + 1) * 11}%`,
                                                animationDelay: `${i * 70}ms`,
                                            }}
                                        />
                                    ))}
                                </div>
                            )}
                            <div
                                aria-live="polite"
                                className={`feedback-banner mt-5 ${feedback.ok ? "ok" : "bad"}`}
                            >
                                <span className={feedback.ok ? "score-pop" : ""}>
                                    {feedback.text}
                                </span>
                            </div>
                        </div>
                    )}
                </section>

                <section
                    className={`min-w-0 flex flex-col gap-4 ${
                        coding ? "min-h-0 overflow-hidden" : "mt-8"
                    }`}
                >
                    <ErrorBoundary key={q.id}>
                        <QuestionRendererHost
                            question={q}
                            onResponse={setResponse}
                            revealSignal={revealSignal}
                        />
                    </ErrorBoundary>

                    <Button
                        onClick={doSubmit}
                        disabled={submitted || !hasResponse}
                        className={`w-full font-bold shrink-0 ${shake ? "question-shake" : ""}`}
                    >
                        {submitted
                            ? t("ui.answerLockedIn")
                            : coding
                              ? t("ui.compileAndSubmit")
                              : t("ui.lockInAnswer")}
                    </Button>
                </section>
            </main>
        </div>
    );
}
