package com.sprintjudge.domain.reducer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public static final class FoldedRoom {

        private String roomId;

        private String mode;

        private Map<String, String> players;

        private int roundsStarted;

        private int answersSubmitted;

        private boolean ended;

        private long seq;

        public FoldedRoom() {
        }

        public FoldedRoom(String roomId, String mode, Map<String, String> players, int roundsStarted,
                int answersSubmitted, boolean ended, long seq) {
            this.roomId = roomId;
            this.mode = mode;
            this.players = players;
            this.roundsStarted = roundsStarted;
            this.answersSubmitted = answersSubmitted;
            this.ended = ended;
            this.seq = seq;
        }

        public String getRoomId() {
            return roomId;
        }

        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Map<String, String> getPlayers() {
            return players;
        }

        public void setPlayers(Map<String, String> players) {
            this.players = players;
        }

        public int getRoundsStarted() {
            return roundsStarted;
        }

        public void setRoundsStarted(int roundsStarted) {
            this.roundsStarted = roundsStarted;
        }

        public int getAnswersSubmitted() {
            return answersSubmitted;
        }

        public void setAnswersSubmitted(int answersSubmitted) {
            this.answersSubmitted = answersSubmitted;
        }

        public boolean isEnded() {
            return ended;
        }

        public void setEnded(boolean ended) {
            this.ended = ended;
        }

        public long getSeq() {
            return seq;
        }

        public void setSeq(long seq) {
            this.seq = seq;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            FoldedRoom that = (FoldedRoom) obj;
            return roundsStarted == that.roundsStarted && answersSubmitted == that.answersSubmitted
                    && ended == that.ended && seq == that.seq && Objects.equals(roomId, that.roomId)
                    && Objects.equals(mode, that.mode) && Objects.equals(players, that.players);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomId, mode, players, roundsStarted, answersSubmitted, ended, seq);
        }

        @Override
        public String toString() {
            return "FoldedRoom[roomId=" + roomId + ", mode=" + mode + ", players=" + players + ", roundsStarted="
                    + roundsStarted + ", answersSubmitted=" + answersSubmitted + ", ended=" + ended + ", seq=" + seq
                    + "]";
        }
    }

    /**
     * Apply one event to the fold, returning the new fold plus effects.
     */
    public static FoldResult apply(FoldedRoom state, RoomEvent event) {
        Map<String, String> players = new TreeMap<>(state.getPlayers());
        String mode = state.getMode();
        int rounds = state.getRoundsStarted();
        int answers = state.getAnswersSubmitted();
        boolean ended = state.isEnded();
        List<RoomEffect> effects = new ArrayList<>();

        if (event instanceof RoomEvent.RoomCreated) {
            RoomEvent.RoomCreated created = (RoomEvent.RoomCreated) event;
            mode = created.getMode();
        } else if (event instanceof RoomEvent.PlayerJoined) {
            RoomEvent.PlayerJoined joined = (RoomEvent.PlayerJoined) event;
            players.put(joined.getPlayerId(), joined.getNickname());
            effects.add(new RoomEffect.Broadcast(joined.getRoomId(), "ROOM_STATE", joined.getPlayerId()));
        } else if (event instanceof RoomEvent.PlayerLeft) {
            RoomEvent.PlayerLeft left = (RoomEvent.PlayerLeft) event;
            players.remove(left.getPlayerId());
        } else if (event instanceof RoomEvent.RoundStarted) {
            rounds += 1;
        } else if (event instanceof RoomEvent.AnswerSubmitted) {
            answers += 1;
        } else if (event instanceof RoomEvent.RoundEnded) {
            RoomEvent.RoundEnded roundEnded = (RoomEvent.RoundEnded) event;
            effects.add(new RoomEffect.Persist(roundEnded.getRoomId(), roundEnded.getSeq(), "ROUND_ENDED", "{}"));
        } else if (event instanceof RoomEvent.GameEnded) {
            ended = true;
        } else if (event instanceof RoomEvent.ScoreComputed) {
            RoomEvent.ScoreComputed scored = (RoomEvent.ScoreComputed) event;
            effects.add(new RoomEffect.Award(scored.getRoomId(), scored.getPlayerId(),
                    (int) Math.round(scored.getSjs())));
        }

        FoldedRoom next = new FoldedRoom(state.getRoomId(), mode, Collections.unmodifiableMap(new TreeMap<>(players)),
                rounds, answers, ended, event.getSeq());
        return new FoldResult(next, Collections.unmodifiableList(new ArrayList<>(effects)));
    }

    /** Starting fold for a room before any event. */
    public static FoldedRoom initial(String roomId) {
        return new FoldedRoom(roomId, "CALIBRATION", Collections.emptyMap(), 0, 0, false, 0L);
    }

    public static final class FoldResult {

        private FoldedRoom state;

        private List<RoomEffect> effects;

        public FoldResult() {
        }

        public FoldResult(FoldedRoom state, List<RoomEffect> effects) {
            this.state = state;
            this.effects = effects;
        }

        public FoldedRoom getState() {
            return state;
        }

        public void setState(FoldedRoom state) {
            this.state = state;
        }

        public List<RoomEffect> getEffects() {
            return effects;
        }

        public void setEffects(List<RoomEffect> effects) {
            this.effects = effects;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            FoldResult that = (FoldResult) obj;
            return Objects.equals(state, that.state) && Objects.equals(effects, that.effects);
        }

        @Override
        public int hashCode() {
            return Objects.hash(state, effects);
        }

        @Override
        public String toString() {
            return "FoldResult[state=" + state + ", effects=" + effects + "]";
        }
    }
}
