package com.sprintjudge.domain.dto;

import java.util.List;

/** Full battle bracket: every round played so far plus champion state. */
public record BattleBracket(String type, List<Round> rounds, String championUuid, String championName) {
    public record Pair(String p1Uuid, String p2Uuid, String winnerUuid, boolean bye) {}
    public record Round(int number, List<Pair> pairs) {}
}
