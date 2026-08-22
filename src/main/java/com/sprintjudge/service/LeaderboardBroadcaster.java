package com.sprintjudge.service;

/**
 * Outbound port for leaderboard transport, consumed by the async judge
 * pipeline. Exists to invert the natural GameRoomManager ⇄
 * SubmissionProcessor dependency into a clean acyclic edge:
 *
 * <pre>SubmissionProcessor ──▶ LeaderboardBroadcaster ◀── GameRoomManager</pre>
 */
public interface LeaderboardBroadcaster {

    /** Marks the room dirty on the 16 ms coalescing tick. */
    void broadcastLeaderboard(String pin);

    /** Pushes an authoritative full snapshot to one session (resync). */
    void sendFullLeaderboard(String pin, String sessionId);
}
