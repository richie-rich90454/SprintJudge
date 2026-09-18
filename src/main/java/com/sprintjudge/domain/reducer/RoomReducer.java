package com.sprintjudge.domain.reducer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.sprintjudge.domain.event.RoomEffect;
import com.sprintjudge.domain.event.RoomEvent;

/**
 * Pure fold over room events. No I/O, no clocks, deterministic for replay.
 * Kept beside the live manager during dual-write migration.
 */
public final class RoomReducer {

    private RoomReducer() {
    }

    /** Minimal folded view used by snapshot and resume flows. */
    public record FoldedRoom(String roomId, String mode, Map<String, String> players,
            int roundsStarted, int answersSubmitted, boolean ended, long seq) {
    }

    /**
     * Apply one event to the fold, returning the new fold plus effects.
     */
    public static FoldResult apply(FoldedRoom state, RoomEvent event) {
        Map<String, String> players = new TreeMap<>(state.players());
        String mode = state.mode();
        int rounds = state.roundsStarted();
        int answers = state.answersSubmitted();
        boolean ended = state.ended();
        List<RoomEffect> effects = new ArrayList<>();

        switch (event) {
            case RoomEvent.RoomCreated created -> mode = created.mode();
            case RoomEvent.PlayerJoined joined -> {
                players.put(joined.playerId(), joined.nickname());
                effects.add(new RoomEffect.Broadcast(joined.roomId(), "ROOM_STATE", joined.playerId()));
            }
            case RoomEvent.PlayerLeft left -> players.remove(left.playerId());
            case RoomEvent.RoundStarted started -> rounds += 1;
            case RoomEvent.AnswerSubmitted submitted -> answers += 1;
            case RoomEvent.RoundEnded ignored -> effects.add(new RoomEffect.Persist(ignored.roomId(), ignored.seq(), "ROUND_ENDED", "{}"));
            case RoomEvent.GameEnded finished -> ended = true;
            case RoomEvent.ScoreComputed scored -> effects.add(new RoomEffect.Award(scored.roomId(), scored.playerId(), (int) Math.round(scored.sjs())));
        }

        FoldedRoom next = new FoldedRoom(state.roomId(), mode, Map.copyOf(players), rounds, answers, ended, event.seq());
        return new FoldResult(next, List.copyOf(effects));
    }

    /** Starting fold for a room before any event. */
    public static FoldedRoom initial(String roomId) {
        return new FoldedRoom(roomId, "CALIBRATION", Map.of(), 0, 0, false, 0L);
    }

    public record FoldResult(FoldedRoom state, List<RoomEffect> effects) {
    }
}
