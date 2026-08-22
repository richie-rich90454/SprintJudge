package com.openquiz.domain.dto;

import java.util.List;

/**
 * Delta leaderboard broadcast. Clients apply {@code entries} and remember
 * {@code seq}; a gap triggers RESYNC_LEADERBOARD, answered by a full
 * {@code resync=true} batch. Ranks are exact at emission time.
 */
public record LeaderboardDelta(
        String type,
        long seq,
        boolean resync,
        List<LeaderboardEntry> entries
) {}
