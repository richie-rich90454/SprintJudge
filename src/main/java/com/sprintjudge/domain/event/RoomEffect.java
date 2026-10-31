package com.sprintjudge.domain.event;

/**
 * Side effects a reducer may request. The handler runs them; the domain
 * only describes them, keeping the fold pure and testable.
 */
public sealed interface RoomEffect permits RoomEffect.Broadcast, RoomEffect.Persist,
        RoomEffect.Schedule, RoomEffect.Award {

    String roomId();

    record Broadcast(String roomId, String type, String payloadJson) implements RoomEffect {
    }

    record Persist(String roomId, long seq, String type, String payloadJson) implements RoomEffect {
    }

    record Schedule(String roomId, String kind, long delayMs) implements RoomEffect {
    }

    record Award(String roomId, String playerId, int points) implements RoomEffect {
    }
}
