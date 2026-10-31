package com.sprintjudge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sprintjudge.domain.event.RoomEffect;
import com.sprintjudge.domain.event.RoomEvent;
import com.sprintjudge.domain.models.RoomState;
import com.sprintjudge.domain.reducer.RoomReducer;

/** Determinism and coverage for the event fold plus shared records. */
class RoomReducerTest {

    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

    @Test
    void foldCoversEveryEventOnce() {
        RoomReducer.FoldedRoom state = RoomReducer.initial("r1");
        state = apply(state, new RoomEvent.RoomCreated("r1", 1, NOW, "CALIBRATION"));
        assertEquals("CALIBRATION", state.mode());
        state = apply(state, new RoomEvent.PlayerJoined("r1", 2, NOW, "p1", "Ada"));
        assertEquals("Ada", state.players().get("p1"));
        state = apply(state, new RoomEvent.RoundStarted("r1", 3, NOW, "round1", 0));
        assertEquals(1, state.roundsStarted());
        state = apply(state, new RoomEvent.AnswerSubmitted("r1", 4, NOW, "p1", "q1", true, 5.0));
        assertEquals(1, state.answersSubmitted());
        RoomReducer.FoldResult roundEnd = RoomReducer.apply(state,
                new RoomEvent.RoundEnded("r1", 5, NOW, "round1"));
        assertEquals(1, roundEnd.effects().size());
        state = roundEnd.state();
        RoomReducer.FoldResult scored = RoomReducer.apply(state,
                new RoomEvent.ScoreComputed("r1", 6, NOW, "p1", 80.4));
        assertTrue(scored.effects().get(0) instanceof RoomEffect.Award);
        state = apply(scored.state(), new RoomEvent.PlayerLeft("r1", 7, NOW, "p1"));
        assertTrue(state.players().isEmpty());
        state = apply(state, new RoomEvent.GameEnded("r1", 8, NOW));
        assertTrue(state.ended());
        assertEquals(8L, state.seq());
    }

    @Test
    void replayIsDeterministic() {
        List<RoomEvent> events = List.of(new RoomEvent.RoomCreated("r2", 1, NOW, "PRACTICE"),
                new RoomEvent.PlayerJoined("r2", 2, NOW, "p1", "Bo"),
                new RoomEvent.PlayerJoined("r2", 3, NOW, "p2", "Cy"));
        RoomReducer.FoldedRoom first = fold("r2", events);
        RoomReducer.FoldedRoom second = fold("r2", events);
        assertEquals(first, second);
    }

    @Test
    void sharedRecordsInstantiate() {
        RoomState snapshot = new RoomState("r1", "123456", "CALIBRATION", "LOBBY", "Host",
                java.util.Map.of("p1", "Ada"), List.of(), 0, 1L, NOW);
        assertEquals("123456", snapshot.pin());
        RoomEffect broadcast = new RoomEffect.Broadcast("r1", "ROOM_STATE", "{}");
        RoomEffect persist = new RoomEffect.Persist("r1", 1L, "ROUND_ENDED", "{}");
        RoomEffect schedule = new RoomEffect.Schedule("r1", "TIMEOUT", 1000L);
        RoomEffect award = new RoomEffect.Award("r1", "p1", 10);
        assertEquals("r1", broadcast.roomId());
        assertEquals("r1", persist.roomId());
        assertEquals("r1", schedule.roomId());
        assertEquals("r1", award.roomId());
    }

    private static RoomReducer.FoldedRoom fold(String roomId, List<RoomEvent> events) {
        RoomReducer.FoldedRoom state = RoomReducer.initial(roomId);
        for (RoomEvent event : events) {
            state = apply(state, event);
        }
        return state;
    }

    private static RoomReducer.FoldedRoom apply(RoomReducer.FoldedRoom state, RoomEvent event) {
        return RoomReducer.apply(state, event).state();
    }
}
