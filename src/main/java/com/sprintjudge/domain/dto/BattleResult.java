package com.sprintjudge.domain.dto;

import java.util.List;

/** Outcome of one battle round, broadcast at reveal. */
public record BattleResult(String type, int round, List<Match> matches, List<String> winners) {
    public record Match(String p1Uuid, String p2Uuid, String winnerUuid, boolean bye) {}
}
