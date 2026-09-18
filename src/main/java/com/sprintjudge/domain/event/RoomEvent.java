package com.sprintjudge.domain.event;

import java.time.Instant;

/**
 * Append-only room history. State is a fold over these events.
 * Kept additive: new event types are added, never renamed or removed.
 */
public sealed interface RoomEvent permits RoomEvent.RoomCreated, RoomEvent.PlayerJoined,
        RoomEvent.PlayerLeft, RoomEvent.RoundStarted, RoomEvent.AnswerSubmitted,
        RoomEvent.RoundEnded, RoomEvent.GameEnded, RoomEvent.ScoreComputed {

    String roomId();

    long seq();

    Instant at();

    record RoomCreated(String roomId, long seq, Instant at, String mode) implements RoomEvent {
    }

    record PlayerJoined(String roomId, long seq, Instant at, String playerId, String nickname) implements RoomEvent {
    }

    record PlayerLeft(String roomId, long seq, Instant at, String playerId) implements RoomEvent {
    }

    record RoundStarted(String roomId, long seq, Instant at, String roundId, int index) implements RoomEvent {
    }

    record AnswerSubmitted(String roomId, long seq, Instant at, String playerId, String questionId,
            boolean correct, double seconds) implements RoomEvent {
    }

    record RoundEnded(String roomId, long seq, Instant at, String roundId) implements RoomEvent {
    }

    record GameEnded(String roomId, long seq, Instant at) implements RoomEvent {
    }

    record ScoreComputed(String roomId, long seq, Instant at, String playerId, double sjs) implements RoomEvent {
    }
}
