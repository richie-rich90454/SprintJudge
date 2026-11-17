package com.sprintjudge.domain.dto;

import java.util.List;
import java.util.Objects;

/** Outcome of one battle round, broadcast at reveal. */
public final class BattleResult {
    private String type;
    private int round;
    private List<Match> matches;
    private List<String> winners;

    public BattleResult() {}

    public BattleResult(String type, int round, List<Match> matches, List<String> winners) {
        this.type = type;
        this.round = round;
        this.matches = matches;
        this.winners = winners;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public List<Match> getMatches() { return matches; }
    public void setMatches(List<Match> matches) { this.matches = matches; }

    public List<String> getWinners() { return winners; }
    public void setWinners(List<String> winners) { this.winners = winners; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BattleResult)) return false;
        BattleResult other = (BattleResult) o;
        return round == other.round
                && Objects.equals(type, other.type)
                && Objects.equals(matches, other.matches)
                && Objects.equals(winners, other.winners);
    }

    @Override public int hashCode() { return Objects.hash(type, round, matches, winners); }

    @Override public String toString() {
        return "BattleResult[type=" + type
                + ", round=" + round
                + ", matches=" + matches
                + ", winners=" + winners + "]";
    }

    public static final class Match {
        private String p1Uuid;
        private String p2Uuid;
        private String winnerUuid;
        private boolean bye;

        public Match() {}

        public Match(String p1Uuid, String p2Uuid, String winnerUuid, boolean bye) {
            this.p1Uuid = p1Uuid;
            this.p2Uuid = p2Uuid;
            this.winnerUuid = winnerUuid;
            this.bye = bye;
        }

        public String getP1Uuid() { return p1Uuid; }
        public void setP1Uuid(String p1Uuid) { this.p1Uuid = p1Uuid; }

        public String getP2Uuid() { return p2Uuid; }
        public void setP2Uuid(String p2Uuid) { this.p2Uuid = p2Uuid; }

        public String getWinnerUuid() { return winnerUuid; }
        public void setWinnerUuid(String winnerUuid) { this.winnerUuid = winnerUuid; }

        public boolean isBye() { return bye; }
        public void setBye(boolean bye) { this.bye = bye; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Match)) return false;
            Match other = (Match) o;
            return bye == other.bye
                    && Objects.equals(p1Uuid, other.p1Uuid)
                    && Objects.equals(p2Uuid, other.p2Uuid)
                    && Objects.equals(winnerUuid, other.winnerUuid);
        }

        @Override public int hashCode() { return Objects.hash(p1Uuid, p2Uuid, winnerUuid, bye); }

        @Override public String toString() {
            return "Match[p1Uuid=" + p1Uuid
                    + ", p2Uuid=" + p2Uuid
                    + ", winnerUuid=" + winnerUuid
                    + ", bye=" + bye + "]";
        }
    }
}
