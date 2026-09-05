package com.sprintjudge.service.leaderboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveLeaderboardBreadthTest {

    @Test
    void joinAssignsIncreasingSequences() {
        LiveLeaderboard lb = new LiveLeaderboard();
        long first = lb.join("u1", "A");
        long second = lb.join("u2", "B");
        assertTrue(second > first);
        assertEquals(2, lb.size());
    }

    @Test
    void rejoinResetsScoreToZero() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "A");
        lb.applyScore("u1", 100);
        lb.join("u1", "A");
        assertEquals(1, lb.size());
        assertEquals(0, lb.scoreOf("u1"));
    }

    @Test
    void applyScoreUnknownAndRemovedReturnMinusOne() {
        LiveLeaderboard lb = new LiveLeaderboard();
        assertEquals(-1, lb.applyScore("ghost", 10));
        lb.join("u1", "A");
        lb.remove("u1");
        assertEquals(-1, lb.applyScore("u1", 10));
    }

    @Test
    void negativeDeltaLowersScore() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "A");
        lb.applyScore("u1", 100);
        lb.applyScore("u1", -30);
        assertEquals(70, lb.scoreOf("u1"));
    }

    @Test
    void overtakeUpdatesRanks() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        lb.applyScore("a", 100);
        lb.applyScore("b", 50);
        assertEquals(1, lb.rankOf("a"));
        lb.applyScore("b", 100);
        assertEquals(1, lb.rankOf("b"));
        assertEquals(2, lb.rankOf("a"));
    }

    @Test
    void removeUnknownIsNoop() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "A");
        lb.remove("ghost");
        assertEquals(1, lb.size());
        assertEquals(0, lb.scoreOf("ghost"));
        assertNull(lb.nameOf("ghost"));
        assertEquals("A", lb.nameOf("u1"));
    }

    @Test
    void drainMergesJoinsAndScoresWithFreshRanks() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        lb.applyScore("a", 100);
        lb.applyScore("b", 200);
        DeltaLedger.Batch batch = lb.drainDeltas(false);
        assertEquals(2, batch.upserts().size());
        for (DeltaLedger.Delta d : batch.upserts()) {
            if (d.uuid().equals("b")) {
                assertEquals(1, d.rank());
                assertEquals(200, d.score());
            } else {
                assertEquals(2, d.rank());
            }
        }
        assertEquals(0, lb.pendingDeltaCount());
    }

    @Test
    void fullBatchDoesNotConsumeLedger() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        long seq = lb.currentSeq();
        DeltaLedger.Batch full = lb.fullBatch();
        assertTrue(full.resync());
        assertEquals(1, full.upserts().size());
        assertEquals(seq, full.seq());
        assertEquals(seq, lb.currentSeq());
        assertEquals(1, lb.pendingDeltaCount());
        assertEquals(1, lb.drainDeltas(false).upserts().size());
    }

    @Test
    void snapshotOrderMatchesRankOrder() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        lb.join("c", "C");
        lb.applyScore("c", 300);
        lb.applyScore("a", 100);
        List<RankedSkipList.Entry> snap = lb.snapshot();
        assertEquals("c", snap.get(0).uuid());
        assertEquals("a", snap.get(1).uuid());
        assertEquals("b", snap.get(2).uuid());
    }

    @Test
    void forceResyncDrainReturnsResyncBatch() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        DeltaLedger.Batch b = lb.drainDeltas(true);
        assertTrue(b.resync());
        assertEquals(1, lb.pendingDeltaCount());
    }

    @Test
    void removedPlayerRankIsMinusOne() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        lb.remove("a");
        assertEquals(-1, lb.rankOf("a"));
        assertEquals(1, lb.rankOf("b"));
        assertEquals(1, lb.size());
    }

    @Test
    void applyScoreReturnsNewRank() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("a", "A");
        lb.join("b", "B");
        assertEquals(1, lb.applyScore("b", 50));
        assertEquals(2, lb.applyScore("a", 10));
    }

    @Test
    void drainAfterRemoveKeepsRecordedRank() {
        LiveLeaderboard lb = new LiveLeaderboard();
        lb.join("u1", "A");
        lb.remove("u1");
        var batch = lb.drainDeltas(false);
        assertEquals(1, batch.upserts().size());
        assertEquals(1, batch.upserts().get(0).rank());
    }

    @Test
    void fiftyPlayerLinearTournamentRanksExactly() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 50; i++) lb.join("u" + i, "N" + i);
        for (int i = 0; i < 50; i++) lb.applyScore("u" + i, i * 10L);
        assertEquals(50, lb.size());
        assertEquals(1, lb.rankOf("u49"));
        assertEquals(50, lb.rankOf("u0"));
        assertEquals(25, lb.rankOf("u25"));
        var snap = lb.snapshot();
        assertEquals("u49", snap.get(0).uuid());
        assertEquals("u0", snap.get(49).uuid());
        assertEquals(490L, lb.scoreOf("u49"));
    }

    @Test
    void hundredPlayerTournamentTopThreeExact() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 100; i++) lb.join("p" + i, "P" + i);
        for (int i = 0; i < 100; i++) lb.applyScore("p" + i, i * 7L);
        assertEquals(1, lb.rankOf("p99"));
        assertEquals(2, lb.rankOf("p98"));
        assertEquals(3, lb.rankOf("p97"));
        assertEquals(100, lb.rankOf("p0"));
        var snap = lb.snapshot();
        assertEquals("p99", snap.get(0).uuid());
        assertEquals("p98", snap.get(1).uuid());
        assertEquals("p97", snap.get(2).uuid());
    }

    @Test
    void twoHundredPlayerTournamentBottomHoldsZero() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 200; i++) lb.join("w" + i, "W" + i);
        for (int i = 1; i < 200; i++) lb.applyScore("w" + i, i * 5L);
        assertEquals(1, lb.rankOf("w199"));
        assertEquals(200, lb.rankOf("w0"));
        assertEquals(0L, lb.scoreOf("w0"));
        assertEquals(200, lb.size());
        assertEquals(200, lb.snapshot().size());
    }

    @Test
    void seventyFivePlayerQuadraticScoresRankBySquare() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 75; i++) lb.join("q" + i, "Q" + i);
        for (int i = 0; i < 75; i++) lb.applyScore("q" + i, (long) i * i);
        assertEquals(1, lb.rankOf("q74"));
        assertEquals(75, lb.rankOf("q0"));
        assertEquals(2, lb.rankOf("q73"));
        assertEquals((long) 74 * 74, lb.scoreOf("q74"));
    }

    @Test
    void sixtyPlayerTieGroupOrdersByJoinSeq() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 60; i++) lb.join("t" + i, "T" + i);
        for (int i = 0; i < 60; i++) lb.applyScore("t" + i, 100L);
        for (int i = 0; i < 60; i++) assertEquals(i + 1, lb.rankOf("t" + i));
        var snap = lb.snapshot();
        for (int i = 0; i < 60; i++) assertEquals("t" + i, snap.get(i).uuid());
    }

    @Test
    void eightyPlayerTwoTierScoresSplitRanks() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 80; i++) lb.join("s" + i, "S" + i);
        for (int i = 0; i < 40; i++) lb.applyScore("s" + i, 1000L);
        for (int i = 40; i < 80; i++) lb.applyScore("s" + i, 10L);
        assertEquals(1, lb.rankOf("s0"));
        assertEquals(40, lb.rankOf("s39"));
        assertEquals(41, lb.rankOf("s40"));
        assertEquals(80, lb.rankOf("s79"));
    }

    @Test
    void hundredPlayerOvertakeLateJoinerTakesLead() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 100; i++) lb.join("o" + i, "O" + i);
        for (int i = 0; i < 100; i++) lb.applyScore("o" + i, i * 10L);
        assertEquals(1, lb.rankOf("o99"));
        lb.applyScore("o0", 5000L);
        assertEquals(1, lb.rankOf("o0"));
        assertEquals(2, lb.rankOf("o99"));
        assertEquals(5000L, lb.scoreOf("o0"));
    }

    @Test
    void fiftyPlayerDrainThenResyncHealsWithFullBatch() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 50; i++) lb.join("d" + i, "D" + i);
        for (int i = 0; i < 50; i++) lb.applyScore("d" + i, i * 10L);
        var first = lb.drainDeltas(false);
        assertEquals(50, first.upserts().size());
        assertEquals(0, lb.pendingDeltaCount());
        var empty = lb.drainDeltas(false);
        org.junit.jupiter.api.Assertions.assertTrue(empty.resync());
        var full = lb.fullBatch();
        org.junit.jupiter.api.Assertions.assertTrue(full.resync());
        assertEquals(50, full.upserts().size());
        assertEquals("d49", full.upserts().get(0).uuid());
        assertEquals(1, full.upserts().get(0).rank());
    }

    @Test
    void hundredPlayerForceResyncKeepsPendingForReplacement() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 100; i++) lb.join("f" + i, "F" + i);
        int pendingBefore = lb.pendingDeltaCount();
        assertEquals(100, pendingBefore);
        var forced = lb.drainDeltas(true);
        org.junit.jupiter.api.Assertions.assertTrue(forced.resync());
        assertEquals(100, lb.pendingDeltaCount());
        var real = lb.drainDeltas(false);
        org.junit.jupiter.api.Assertions.assertFalse(real.resync());
        assertEquals(100, real.upserts().size());
    }

    @Test
    void sixtyPlayerRemoveMiddleShiftsRanksUp() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 60; i++) lb.join("r" + i, "R" + i);
        for (int i = 0; i < 60; i++) lb.applyScore("r" + i, i * 10L);
        assertEquals(1, lb.rankOf("r59"));
        lb.remove("r59");
        assertEquals(-1, lb.rankOf("r59"));
        assertEquals(1, lb.rankOf("r58"));
        assertEquals(59, lb.size());
        assertEquals("r58", lb.snapshot().get(0).uuid());
    }

    @Test
    void fiftyPlayerRemoveBottomKeepsHead() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 50; i++) lb.join("b" + i, "B" + i);
        for (int i = 0; i < 50; i++) lb.applyScore("b" + i, i * 10L);
        lb.remove("b0");
        assertEquals(-1, lb.rankOf("b0"));
        assertEquals(1, lb.rankOf("b49"));
        assertEquals(49, lb.size());
        assertEquals("b49", lb.snapshot().get(0).uuid());
    }

    @Test
    void seventyPlayerRejoinResetsToBottom() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 8; i++) lb.join("j" + i, "J" + i);
        for (int i = 0; i < 8; i++) lb.applyScore("j" + i, 500L + i);
        assertEquals("j7", lb.snapshot().get(0).uuid());
        lb.remove("j7");
        lb.join("j7", "J7");
        assertEquals(0L, lb.scoreOf("j7"));
        var snap = lb.snapshot();
        assertEquals("j6", snap.get(0).uuid());
        assertEquals("j7", snap.get(snap.size() - 1).uuid());
        assertEquals(8, snap.size());
    }

    @Test
    void ninetyPlayerJoinRemoveRejoinRankStability() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 90; i++) lb.join("k" + i, "K" + i);
        for (int i = 0; i < 90; i++) lb.applyScore("k" + i, i * 10L);
        lb.remove("k45");
        assertEquals(89, lb.size());
        assertEquals(-1, lb.rankOf("k45"));
        lb.join("k45", "K45");
        assertEquals(90, lb.size());
        assertEquals(0L, lb.scoreOf("k45"));
        assertEquals(90, lb.rankOf("k45"));
        assertEquals(1, lb.rankOf("k89"));
    }

    @Test
    void hundredPlayerNegativeScoresOrderBelowZero() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 100; i++) lb.join("n" + i, "N" + i);
        for (int i = 0; i < 50; i++) lb.applyScore("n" + i, 100L);
        for (int i = 50; i < 100; i++) lb.applyScore("n" + i, -50L);
        assertEquals(1, lb.rankOf("n0"));
        assertEquals(100, lb.rankOf("n99"));
        assertEquals(-50L, lb.scoreOf("n99"));
        var snap = lb.snapshot();
        assertEquals("n0", snap.get(0).uuid());
        assertEquals("n99", snap.get(99).uuid());
    }

    @Test
    void fiftyPlayerInterleavedDrainKeepsExactRanks() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 50; i++) lb.join("v" + i, "V" + i);
        lb.drainDeltas(false);
        for (int i = 0; i < 50; i++) lb.applyScore("v" + i, i * 20L);
        var batch = lb.drainDeltas(false);
        org.junit.jupiter.api.Assertions.assertFalse(batch.resync());
        assertEquals(50, batch.upserts().size());
        java.util.Map<String, Integer> ranks = new java.util.HashMap<>();
        for (var d : batch.upserts()) ranks.put(d.uuid(), d.rank());
        assertEquals(1, (int) ranks.get("v49"));
        assertEquals(50, (int) ranks.get("v0"));
        assertEquals(1, lb.rankOf("v49"));
    }

    @Test
    void hundredTwentyPlayerDrainCoalescesToLatestScores() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 120; i++) lb.join("c" + i, "C" + i);
        lb.drainDeltas(false);
        for (int r = 0; r < 3; r++) for (int i = 0; i < 120; i++) lb.applyScore("c" + i, 10L);
        var batch = lb.drainDeltas(false);
        assertEquals(120, batch.upserts().size());
        for (var d : batch.upserts()) assertEquals(30L, d.score());
        assertEquals(0, lb.pendingDeltaCount());
    }

    @Test
    void fullBatchAfterDrainStillRanksByScore() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 60; i++) lb.join("g" + i, "G" + i);
        for (int i = 0; i < 60; i++) lb.applyScore("g" + i, i * 3L);
        lb.drainDeltas(false);
        var full = lb.fullBatch();
        org.junit.jupiter.api.Assertions.assertTrue(full.resync());
        assertEquals(60, full.upserts().size());
        assertEquals("g59", full.upserts().get(0).uuid());
        assertEquals(1, full.upserts().get(0).rank());
        assertEquals("g0", full.upserts().get(59).uuid());
    }

    @Test
    void hundredFiftyPlayerScriptedComebackSequence() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 150; i++) lb.join("e" + i, "E" + i);
        for (int i = 0; i < 150; i++) lb.applyScore("e" + i, 100L);
        for (int i = 0; i < 10; i++) lb.applyScore("e" + i, 900L);
        var snap = lb.snapshot();
        for (int i = 0; i < 10; i++) assertEquals("e" + i, snap.get(i).uuid());
        assertEquals("e10", snap.get(10).uuid());
        assertEquals(1000L, lb.scoreOf("e0"));
        assertEquals(100L, lb.scoreOf("e10"));
        assertEquals(150, snap.size());
    }

    @Test
    void fiftyFivePlayerAlternatingScoresSnapshotExact() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 55; i++) lb.join("alt" + i, "A" + i);
        for (int i = 0; i < 55; i++) lb.applyScore("alt" + i, (i % 2 == 0) ? 1000L : 10L);
        var snap = lb.snapshot();
        assertEquals(55, snap.size());
        assertEquals("alt0", snap.get(0).uuid());
        assertEquals(1000L, snap.get(0).score());
        assertEquals("alt53", snap.get(snap.size() - 1).uuid());
        assertEquals(10L, snap.get(snap.size() - 1).score());
    }

    @Test
    void sixtyFivePlayerStaircaseThenTopRemoved() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 65; i++) lb.join("st" + i, "S" + i);
        for (int i = 0; i < 65; i++) lb.applyScore("st" + i, i * 11L);
        lb.remove("st64");
        lb.remove("st63");
        assertEquals(63, lb.size());
        assertEquals("st62", lb.snapshot().get(0).uuid());
        assertEquals(-1, lb.rankOf("st64"));
    }

    @Test
    void hundredPlayerTwoDrainsSecondHealsViaFull() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 100; i++) lb.join("h" + i, "H" + i);
        for (int i = 0; i < 100; i++) lb.applyScore("h" + i, i * 4L);
        lb.drainDeltas(false);
        lb.applyScore("h0", 5000L);
        var batch = lb.drainDeltas(false);
        org.junit.jupiter.api.Assertions.assertFalse(batch.resync());
        assertEquals(1, batch.upserts().size());
        assertEquals("h0", batch.upserts().get(0).uuid());
        assertEquals(5000L, batch.upserts().get(0).score());
        var gap = lb.drainDeltas(false);
        org.junit.jupiter.api.Assertions.assertTrue(gap.resync());
        assertEquals(100, lb.fullBatch().upserts().size());
    }

    @Test
    void eightyPlayerBulkNegativeThenPositiveRecovery() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 80; i++) lb.join("rec" + i, "R" + i);
        for (int i = 0; i < 80; i++) lb.applyScore("rec" + i, -100L);
        for (int i = 0; i < 10; i++) lb.applyScore("rec" + i, 1000L);
        var snap = lb.snapshot();
        assertEquals("rec0", snap.get(0).uuid());
        assertEquals(900L, snap.get(0).score());
        assertEquals("rec79", snap.get(snap.size() - 1).uuid());
    }

    @Test
    void fiftyPlayerDrainPendingZeroThenJoinAddsOne() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 50; i++) lb.join("pz" + i, "P" + i);
        lb.drainDeltas(false);
        assertEquals(0, lb.pendingDeltaCount());
        lb.join("late", "Late");
        assertEquals(1, lb.pendingDeltaCount());
        var batch = lb.drainDeltas(false);
        assertEquals(1, batch.upserts().size());
        assertEquals("late", batch.upserts().get(0).uuid());
    }

    @Test
    void hundredTenPlayerSnapshotScoresDescendMonotonically() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 110; i++) lb.join("mono" + i, "M" + i);
        for (int i = 0; i < 110; i++) lb.applyScore("mono" + i, (109 - i) * 13L);
        var snap = lb.snapshot();
        assertEquals("mono0", snap.get(0).uuid());
        for (int i = 1; i < snap.size(); i++) {
            org.junit.jupiter.api.Assertions.assertTrue(snap.get(i - 1).score() >= snap.get(i).score());
        }
    }

    @Test
    void sixtyPlayerRemoveAllThenRejoinFive() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 60; i++) lb.join("gone" + i, "G" + i);
        for (int i = 0; i < 60; i++) lb.remove("gone" + i);
        assertEquals(0, lb.size());
        assertTrue(lb.snapshot().isEmpty());
        for (int i = 0; i < 5; i++) lb.join("back" + i, "B" + i);
        assertEquals(5, lb.size());
        assertEquals(5, lb.snapshot().size());
        assertEquals("back0", lb.snapshot().get(0).uuid());
    }

    @Test
    void ninetyPlayerScoreOfUnknownIsZeroRankMinusOne() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 90; i++) lb.join("ex" + i, "E" + i);
        assertEquals(0L, lb.scoreOf("ghost"));
        assertEquals(-1, lb.rankOf("ghost"));
        assertNull(lb.nameOf("ghost"));
        assertEquals(90, lb.size());
    }

    @Test
    void fiftyPlayerFullBatchRanksSequentialFromOne() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 50; i++) lb.join("fb" + i, "F" + i);
        for (int i = 0; i < 50; i++) lb.applyScore("fb" + i, i * 9L);
        var full = lb.fullBatch();
        org.junit.jupiter.api.Assertions.assertTrue(full.resync());
        for (int i = 0; i < 50; i++) assertEquals(i + 1, full.upserts().get(i).rank());
        assertEquals("fb49", full.upserts().get(0).uuid());
    }

    @Test
    void hundredPlayerApplyReturnsRankOneForNewLeader() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 100; i++) lb.join("lr" + i, "L" + i);
        for (int i = 0; i < 99; i++) lb.applyScore("lr" + i, 50L);
        int rank = lb.applyScore("lr99", 5000L);
        assertEquals(1, rank);
        assertEquals("lr99", lb.snapshot().get(0).uuid());
    }

    @Test
    void seventyPlayerDrainAfterMixedOpsHasAtMostSeventyRows() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 70; i++) lb.join("mx" + i, "M" + i);
        for (int i = 0; i < 70; i++) lb.applyScore("mx" + i, i * 6L);
        lb.remove("mx0");
        lb.remove("mx1");
        var batch = lb.drainDeltas(false);
        org.junit.jupiter.api.Assertions.assertTrue(batch.upserts().size() <= 70);
        assertEquals(68, lb.size());
    }

    @Test
    void sixtyPlayerNameSurvivesScoreUpdates() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 60; i++) lb.join("nm" + i, "Name" + i);
        for (int i = 0; i < 60; i++) lb.applyScore("nm" + i, 200L);
        for (int i = 0; i < 60; i++) assertEquals("Name" + i, lb.nameOf("nm" + i));
        var snap = lb.snapshot();
        assertEquals("Name0", snap.get(0).name());
    }

    @Test
    void hundredThirtyPlayerEvenOddSplitRanks() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 130; i++) lb.join("eo" + i, "E" + i);
        for (int i = 0; i < 130; i += 2) lb.applyScore("eo" + i, 800L);
        var snap = lb.snapshot();
        assertEquals("eo0", snap.get(0).uuid());
        assertEquals(800L, snap.get(0).score());
        assertEquals("eo129", snap.get(snap.size() - 1).uuid());
        assertEquals(0L, snap.get(snap.size() - 1).score());
    }

    @Test
    void fiftyPlayerCurrentSeqEqualsJoinsPlusScores() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 50; i++) lb.join("sq" + i, "S" + i);
        long afterJoin = lb.currentSeq();
        assertEquals(50, afterJoin);
        for (int i = 0; i < 50; i++) lb.applyScore("sq" + i, 10L);
        assertEquals(100, lb.currentSeq());
    }

    @Test
    void hundredPlayerRemoveTopTenPromotesEleventh() {
        LiveLeaderboard lb = new LiveLeaderboard();
        for (int i = 0; i < 100; i++) lb.join("pr" + i, "P" + i);
        for (int i = 0; i < 100; i++) lb.applyScore("pr" + i, i * 10L);
        for (int i = 99; i >= 90; i--) lb.remove("pr" + i);
        assertEquals(90, lb.size());
        assertEquals("pr89", lb.snapshot().get(0).uuid());
    }
}
