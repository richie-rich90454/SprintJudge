package com.sprintjudge.service.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameEventListenerTest {

    private record Fixture(GameEventListener listener, SimpleMeterRegistry registry) {}

    private Fixture fixture() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new Fixture(new GameEventListener(registry), registry);
    }

    private double count(SimpleMeterRegistry r, String name) {
        return r.get(name).counter().count();
    }

    @Test
    void playerJoinedIncrementsCounter() {
        Fixture f = fixture();
        f.listener().onPlayerJoined(new GameEvent.PlayerJoined("123456", "Alice", "u1"));
        assertEquals(1.0, count(f.registry(), "sprintjudge.players.joined"));
    }

    @Test
    void playerJoinedAccumulatesAcrossJoins() {
        Fixture f = fixture();
        f.listener().onPlayerJoined(new GameEvent.PlayerJoined("123456", "A", "u1"));
        f.listener().onPlayerJoined(new GameEvent.PlayerJoined("123456", "B", "u2"));
        f.listener().onPlayerJoined(new GameEvent.PlayerJoined("999999", "C", "u3"));
        assertEquals(3.0, count(f.registry(), "sprintjudge.players.joined"));
    }

    @Test
    void playerLeftIncrementsCounter() {
        Fixture f = fixture();
        f.listener().onPlayerLeft(new GameEvent.PlayerLeft("123456", "u1"));
        assertEquals(1.0, count(f.registry(), "sprintjudge.players.left"));
    }

    @Test
    void playerLeftAccumulates() {
        Fixture f = fixture();
        f.listener().onPlayerLeft(new GameEvent.PlayerLeft("1", "a"));
        f.listener().onPlayerLeft(new GameEvent.PlayerLeft("1", "b"));
        assertEquals(2.0, count(f.registry(), "sprintjudge.players.left"));
    }

    @Test
    void questionStartedIncrementsCounter() {
        Fixture f = fixture();
        f.listener().onQuestionStarted(new GameEvent.QuestionStarted("123456", "q1", 0));
        assertEquals(1.0, count(f.registry(), "sprintjudge.questions.started"));
    }

    @Test
    void questionStartedTracksIndexVariations() {
        Fixture f = fixture();
        f.listener().onQuestionStarted(new GameEvent.QuestionStarted("1", "q1", 0));
        f.listener().onQuestionStarted(new GameEvent.QuestionStarted("1", "q2", 1));
        assertEquals(2.0, count(f.registry(), "sprintjudge.questions.started"));
    }

    @Test
    void questionCompletedIncrementsCounter() {
        Fixture f = fixture();
        f.listener().onQuestionCompleted(new GameEvent.QuestionCompleted("123456", "q1", 0));
        assertEquals(1.0, count(f.registry(), "sprintjudge.questions.completed"));
    }

    @Test
    void correctSubmissionIncrementsBothCounters() {
        Fixture f = fixture();
        f.listener().onSubmission(new GameEvent.SubmissionReceived("1", "q1", "u1", true));
        assertEquals(1.0, count(f.registry(), "sprintjudge.submissions.total"));
        assertEquals(1.0, count(f.registry(), "sprintjudge.submissions.correct"));
    }

    @Test
    void wrongSubmissionIncrementsTotalOnly() {
        Fixture f = fixture();
        f.listener().onSubmission(new GameEvent.SubmissionReceived("1", "q1", "u1", false));
        assertEquals(1.0, count(f.registry(), "sprintjudge.submissions.total"));
        assertEquals(0.0, count(f.registry(), "sprintjudge.submissions.correct"));
    }

    @Test
    void mixedSubmissionsTrackRatio() {
        Fixture f = fixture();
        f.listener().onSubmission(new GameEvent.SubmissionReceived("1", "q1", "u1", true));
        f.listener().onSubmission(new GameEvent.SubmissionReceived("1", "q1", "u2", false));
        f.listener().onSubmission(new GameEvent.SubmissionReceived("1", "q2", "u1", false));
        assertEquals(3.0, count(f.registry(), "sprintjudge.submissions.total"));
        assertEquals(1.0, count(f.registry(), "sprintjudge.submissions.correct"));
    }

    @Test
    void gameCreatedIncrementsCounter() {
        Fixture f = fixture();
        f.listener().onGameCreated(new GameEvent.GameCreated("123456", "qz", "STANDARD"));
        assertEquals(1.0, count(f.registry(), "sprintjudge.games.created"));
    }

    @Test
    void gameCreatedTracksModesDistinctly() {
        Fixture f = fixture();
        f.listener().onGameCreated(new GameEvent.GameCreated("1", "qz", "STANDARD"));
        f.listener().onGameCreated(new GameEvent.GameCreated("2", "qz", "EXAM"));
        assertEquals(2.0, count(f.registry(), "sprintjudge.games.created"));
    }

    @Test
    void gameEndedIncrementsCounter() {
        Fixture f = fixture();
        f.listener().onGameEnded(new GameEvent.GameEnded("123456", 4, 10));
        assertEquals(1.0, count(f.registry(), "sprintjudge.games.ended"));
    }

    @Test
    void gameEndedWithZeroPlayersStillCounts() {
        Fixture f = fixture();
        f.listener().onGameEnded(new GameEvent.GameEnded("1", 0, 0));
        assertEquals(1.0, count(f.registry(), "sprintjudge.games.ended"));
    }

    @Test
    void fullLifecycleCountersStayIndependent() {
        Fixture f = fixture();
        f.listener().onGameCreated(new GameEvent.GameCreated("1", "qz", "STANDARD"));
        f.listener().onPlayerJoined(new GameEvent.PlayerJoined("1", "A", "u1"));
        f.listener().onQuestionStarted(new GameEvent.QuestionStarted("1", "q1", 0));
        f.listener().onSubmission(new GameEvent.SubmissionReceived("1", "q1", "u1", true));
        f.listener().onQuestionCompleted(new GameEvent.QuestionCompleted("1", "q1", 0));
        f.listener().onPlayerLeft(new GameEvent.PlayerLeft("1", "u1"));
        f.listener().onGameEnded(new GameEvent.GameEnded("1", 1, 1));
        assertEquals(1.0, count(f.registry(), "sprintjudge.games.created"));
        assertEquals(1.0, count(f.registry(), "sprintjudge.players.joined"));
        assertEquals(1.0, count(f.registry(), "sprintjudge.questions.started"));
        assertEquals(1.0, count(f.registry(), "sprintjudge.submissions.total"));
        assertEquals(1.0, count(f.registry(), "sprintjudge.submissions.correct"));
        assertEquals(1.0, count(f.registry(), "sprintjudge.questions.completed"));
        assertEquals(1.0, count(f.registry(), "sprintjudge.players.left"));
        assertEquals(1.0, count(f.registry(), "sprintjudge.games.ended"));
    }
}
