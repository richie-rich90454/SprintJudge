package com.sprintjudge.service.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens to domain events and records Prometheus counters.
 * Decouples metrics collection from business logic.
 */
@Component
public class GameEventListener {

    private final Counter playersJoined;
    private final Counter playersLeft;
    private final Counter questionsStarted;
    private final Counter questionsCompleted;
    private final Counter submissionsCorrect;
    private final Counter submissionsTotal;
    private final Counter gamesCreated;
    private final Counter gamesEnded;

    public GameEventListener(MeterRegistry registry) {
        this.playersJoined = Counter.builder("sprintjudge.players.joined").register(registry);
        this.playersLeft = Counter.builder("sprintjudge.players.left").register(registry);
        this.questionsStarted = Counter.builder("sprintjudge.questions.started").register(registry);
        this.questionsCompleted = Counter.builder("sprintjudge.questions.completed").register(registry);
        this.submissionsCorrect = Counter.builder("sprintjudge.submissions.correct").register(registry);
        this.submissionsTotal = Counter.builder("sprintjudge.submissions.total").register(registry);
        this.gamesCreated = Counter.builder("sprintjudge.games.created").register(registry);
        this.gamesEnded = Counter.builder("sprintjudge.games.ended").register(registry);
    }

    @EventListener
    public void onPlayerJoined(GameEvent.PlayerJoined e) {
        playersJoined.increment();
    }

    @EventListener
    public void onPlayerLeft(GameEvent.PlayerLeft e) {
        playersLeft.increment();
    }

    @EventListener
    public void onQuestionStarted(GameEvent.QuestionStarted e) {
        questionsStarted.increment();
    }

    @EventListener
    public void onQuestionCompleted(GameEvent.QuestionCompleted e) {
        questionsCompleted.increment();
    }

    @EventListener
    public void onSubmission(GameEvent.SubmissionReceived e) {
        submissionsTotal.increment();
        if (e.correct()) submissionsCorrect.increment();
    }

    @EventListener
    public void onGameCreated(GameEvent.GameCreated e) {
        gamesCreated.increment();
    }

    @EventListener
    public void onGameEnded(GameEvent.GameEnded e) {
        gamesEnded.increment();
    }
}
