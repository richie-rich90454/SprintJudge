package com.sprintjudge.service.event;

/**
 * Domain events published via Spring ApplicationEventPublisher.
 * Decouples game lifecycle from side effects (logging, metrics, persistence).
 */
public sealed interface GameEvent {
    record PlayerJoined(String pin, String playerName, String playerUuid) implements GameEvent {}
    record PlayerLeft(String pin, String playerUuid) implements GameEvent {}
    record QuestionStarted(String pin, String questionId, int questionIndex) implements GameEvent {}
    record QuestionCompleted(String pin, String questionId, int questionIndex) implements GameEvent {}
    record SubmissionReceived(String pin, String questionId, String playerUuid, boolean correct) implements GameEvent {}
    record GameCreated(String pin, String quizId, String gameMode) implements GameEvent {}
    record GameEnded(String pin, int totalPlayers, int totalQuestions) implements GameEvent {}
}
