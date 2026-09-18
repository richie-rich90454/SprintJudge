package com.sprintjudge.domain.models;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Snapshot view of a room. The live path still owns transitions; this
 * record is the frozen contract new snapshot and resume flows share.
 */
public record RoomState(String id, String pin, String mode, String phase, String hostName,
        Map<String, String> players, List<String> roundIds, int currentRoundIndex, long seq,
        Instant createdAt) {
}
