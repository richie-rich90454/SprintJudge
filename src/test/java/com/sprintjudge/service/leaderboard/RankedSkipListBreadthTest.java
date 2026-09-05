package com.sprintjudge.service.leaderboard;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankedSkipListBreadthTest {

    @Test
    void ascendingInsertsHeadIsMax() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 25; i++) sl.upsert("u" + i, "N" + i, i, i, Long.MIN_VALUE);
        assertEquals(25, sl.size());
        assertEquals(25, sl.snapshot().get(0).score());
        assertEquals("u25", sl.at(1).uuid());
        assertEquals("u1", sl.at(25).uuid());
        assertNull(sl.at(26));
    }

    @Test
    void descendingInsertsKeepOrder() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 25; i >= 1; i--) sl.upsert("u" + i, "N" + i, i, 26 - i, Long.MIN_VALUE);
        assertEquals("u25", sl.snapshot().get(0).uuid());
        assertEquals(1, sl.rankOf("u25"));
        assertEquals(25, sl.rankOf("u1"));
    }

    @Test
    void tenWayTieOrdersByJoinSeq() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 10; i++) sl.upsert("t" + i, "T" + i, 100, i + 1, Long.MIN_VALUE);
        for (int i = 0; i < 10; i++) {
            assertEquals("t" + i, sl.snapshot().get(i).uuid());
            assertEquals(i + 1, sl.rankOf("t" + i));
            assertEquals("t" + i, sl.at(i + 1).uuid());
        }
    }

    @Test
    void boostToTopReRanks() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 50, 2, Long.MIN_VALUE);
        assertEquals(1, sl.upsert("b", "B", 150, 2, 50));
        assertEquals("b", sl.snapshot().get(0).uuid());
        assertEquals(1, sl.rankOf("b"));
        assertEquals(2, sl.rankOf("a"));
        assertEquals(2, sl.size());
    }

    @Test
    void demoteToBottomReRanks() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 90, 2, Long.MIN_VALUE);
        sl.upsert("a", "A", 10, 1, 100);
        assertEquals("b", sl.snapshot().get(0).uuid());
        assertEquals(2, sl.rankOf("a"));
        assertEquals(2, sl.size());
    }

    @Test
    void removeHeadPromotesSecondAcrossFive() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 5; i++) sl.upsert("u" + i, "N", i * 10, i, Long.MIN_VALUE);
        assertTrue(sl.remove("u5", 50, 5));
        assertEquals(4, sl.size());
        assertEquals("u4", sl.snapshot().get(0).uuid());
        assertEquals(1, sl.rankOf("u4"));
    }

    @Test
    void removeTailKeepsHead() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 5; i++) sl.upsert("u" + i, "N", i * 10, i, Long.MIN_VALUE);
        assertTrue(sl.remove("u1", 10, 1));
        assertEquals("u5", sl.snapshot().get(0).uuid());
        assertEquals(-1, sl.rankOf("u1"));
    }

    @Test
    void removeAllEmptiesBoard() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 5; i++) sl.upsert("u" + i, "N", i * 10, i, Long.MIN_VALUE);
        for (int i = 1; i <= 5; i++) assertTrue(sl.remove("u" + i, i * 10, i));
        assertEquals(0, sl.size());
        assertTrue(sl.snapshot().isEmpty());
        assertNull(sl.at(1));
    }

    @Test
    void removeByIdentityOnEmptyReturnsFalse() {
        assertFalse(new RankedSkipList().removeByIdentity("ghost"));
    }

    @Test
    void removeOrderedUnknownUuidReturnsFalse() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 10, 1, Long.MIN_VALUE);
        assertFalse(sl.remove("ghost", 10, 1));
        assertEquals(1, sl.size());
    }

    @Test
    void atLastRankThenOnePastIsNull() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 30, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 10, 2, Long.MIN_VALUE);
        assertEquals("b", sl.at(2).uuid());
        assertNull(sl.at(3));
    }

    @Test
    void rankMatchesSelectIndexForTwenty() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 20; i++) sl.upsert("u" + i, "N", i * 10L, i, Long.MIN_VALUE);
        for (int r = 1; r <= 20; r++) {
            RankedSkipList.Entry e = sl.at(r);
            assertNotNull(e);
            assertEquals(r, sl.rankOf(e.uuid()));
        }
    }

    @Test
    void snapshotIsADetachedCopy() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 10, 1, Long.MIN_VALUE);
        sl.snapshot().clear();
        assertEquals(1, sl.size());
        assertEquals(1, sl.snapshot().size());
    }

    @Test
    void reinsertUnknownPrevScoreReplacesRow() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "Old", 10, 1, Long.MIN_VALUE);
        sl.upsert("a", "New", 99, 1, Long.MIN_VALUE);
        assertEquals(1, sl.size());
        assertEquals("New", sl.snapshot().get(0).name());
        assertEquals(99, sl.snapshot().get(0).score());
    }

    @Test
    void zeroScoresTieByJoinOrder() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("z2", "B", 0, 2, Long.MIN_VALUE);
        sl.upsert("z1", "A", 0, 1, Long.MIN_VALUE);
        sl.upsert("neg", "C", -5, 3, Long.MIN_VALUE);
        assertEquals("z1", sl.snapshot().get(0).uuid());
        assertEquals("z2", sl.snapshot().get(1).uuid());
        assertEquals("neg", sl.snapshot().get(2).uuid());
    }

    @Test
    void longSequenceWithReRanksStaysConsistent() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 20; i++) sl.upsert("u" + i, "N" + i, i * 10L, i, Long.MIN_VALUE);
        for (int i = 2; i <= 20; i += 2) sl.upsert("u" + i, "N" + i, i * 10L + 1000, i, i * 10L);
        assertEquals(20, sl.size());
        Set<String> seen = new HashSet<>();
        for (int r = 1; r <= 20; r++) {
            RankedSkipList.Entry e = sl.at(r);
            assertNotNull(e);
            assertTrue(seen.add(e.uuid()));
            assertEquals(r, sl.rankOf(e.uuid()));
        }
        assertEquals("u20", sl.snapshot().get(0).uuid());
    }

    @Test
    void fiftyPlayerAscendingSnapshotHeadsMax() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 1; i <= 50; i++) sl.upsert("u" + i, "N" + i, i * 10L, i, Long.MIN_VALUE);
        assertEquals(50, sl.size());
        var snap = sl.snapshot();
        assertEquals("u50", snap.get(0).uuid());
        assertEquals("u1", snap.get(49).uuid());
        assertEquals(500L, snap.get(0).score());
        assertEquals(10L, snap.get(49).score());
    }

    @Test
    void hundredPlayerDescendingKeepsHead() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 100; i >= 1; i--) sl.upsert("p" + i, "P" + i, i * 7L, 101 - i, Long.MIN_VALUE);
        assertEquals(100, sl.size());
        var snap = sl.snapshot();
        assertEquals("p100", snap.get(0).uuid());
        assertEquals("p1", snap.get(99).uuid());
    }

    @Test
    void twoHundredPlayerLinearSnapshotExact() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 200; i++) sl.upsert("w" + i, "W" + i, i * 5L, i + 1, Long.MIN_VALUE);
        assertEquals(200, sl.size());
        var snap = sl.snapshot();
        assertEquals("w199", snap.get(0).uuid());
        assertEquals("w0", snap.get(199).uuid());
        assertEquals(995L, snap.get(0).score());
    }

    @Test
    void eightyPlayerTieSnapshotFollowsJoinSeq() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 80; i++) sl.upsert("t" + i, "T" + i, 500L, i + 1, Long.MIN_VALUE);
        var snap = sl.snapshot();
        for (int i = 0; i < 80; i++) assertEquals("t" + i, snap.get(i).uuid());
        assertEquals(80, sl.size());
    }

    @Test
    void sixtyPlayerTwoTierSnapshotSplits() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 30; i++) sl.upsert("hi" + i, "H" + i, 1000L, i + 1, Long.MIN_VALUE);
        for (int i = 0; i < 30; i++) sl.upsert("lo" + i, "L" + i, 10L, 31 + i, Long.MIN_VALUE);
        var snap = sl.snapshot();
        assertEquals("hi0", snap.get(0).uuid());
        assertEquals("hi29", snap.get(29).uuid());
        assertEquals("lo0", snap.get(30).uuid());
        assertEquals("lo29", snap.get(59).uuid());
    }

    @Test
    void hundredPlayerBoostLowestToTopSnapshotMoves() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 100; i++) sl.upsert("b" + i, "B" + i, i * 10L, i + 1, Long.MIN_VALUE);
        sl.upsert("b0", "B0", 5000L, 1, 0L);
        var snap = sl.snapshot();
        assertEquals("b0", snap.get(0).uuid());
        assertEquals(5000L, snap.get(0).score());
        assertEquals(100, snap.size());
    }

    @Test
    void ninetyPlayerDemoteHeadToTailSnapshotMoves() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 90; i++) sl.upsert("d" + i, "D" + i, 1000L + i, i + 1, Long.MIN_VALUE);
        sl.upsert("d89", "D89", -100L, 90, 1089L);
        var snap = sl.snapshot();
        assertEquals("d88", snap.get(0).uuid());
        assertEquals("d89", snap.get(snap.size() - 1).uuid());
        assertEquals(90, snap.size());
    }

    @Test
    void fiftyPlayerRemoveHeadPromotesSecondInSnapshot() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 50; i++) sl.upsert("r" + i, "R" + i, i * 10L, i + 1, Long.MIN_VALUE);
        assertTrue(sl.remove("r49", 490L, 50));
        var snap = sl.snapshot();
        assertEquals(49, snap.size());
        assertEquals("r48", snap.get(0).uuid());
    }

    @Test
    void hundredPlayerRemoveBottomKeepsHeadSnapshot() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 100; i++) sl.upsert("k" + i, "K" + i, i * 10L, i + 1, Long.MIN_VALUE);
        assertTrue(sl.remove("k0", 0L, 1));
        var snap = sl.snapshot();
        assertEquals(99, snap.size());
        assertEquals("k99", snap.get(0).uuid());
        assertEquals("k1", snap.get(snap.size() - 1).uuid());
    }

    @Test
    void sixtyPlayerReinsertWithKnownPrevScoreKeepsSize() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 60; i++) sl.upsert("v" + i, "V" + i, i * 10L, i + 1, Long.MIN_VALUE);
        sl.upsert("v0", "V0", 5000L, 1, 0L);
        assertEquals(60, sl.size());
        assertEquals("v0", sl.snapshot().get(0).uuid());
        sl.upsert("v0", "V0", 5L, 1, 5000L);
        assertEquals(60, sl.size());
        assertEquals("v0", sl.snapshot().get(sl.snapshot().size() - 1).uuid());
    }

    @Test
    void hundredPlayerNegativeScoresTailInSnapshot() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 50; i++) sl.upsert("pos" + i, "P" + i, 100L + i, i + 1, Long.MIN_VALUE);
        for (int i = 0; i < 50; i++) sl.upsert("neg" + i, "N" + i, -100L - i, 51 + i, Long.MIN_VALUE);
        var snap = sl.snapshot();
        assertEquals(100, snap.size());
        assertEquals("pos49", snap.get(0).uuid());
        assertEquals("neg49", snap.get(99).uuid());
    }

    @Test
    void fortyPlayerAtRankMatchesSnapshotOrder() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 40; i++) sl.upsert("a" + i, "A" + i, i * 11L, i + 1, Long.MIN_VALUE);
        var snap = sl.snapshot();
        for (int r = 1; r <= 40; r++) {
            assertNotNull(sl.at(r));
            assertEquals(snap.get(r - 1).uuid(), sl.at(r).uuid());
        }
    }

    @Test
    void fiftyPlayerJoinRemoveRejoinSnapshotStability() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 50; i++) sl.upsert("s" + i, "S" + i, i * 10L, i + 1, Long.MIN_VALUE);
        assertTrue(sl.remove("s25", 250L, 26));
        assertEquals(49, sl.size());
        sl.upsert("s25", "S25", 0L, 51, Long.MIN_VALUE);
        assertEquals(50, sl.size());
        var snap = sl.snapshot();
        assertEquals("s49", snap.get(0).uuid());
        assertEquals("s25", snap.get(snap.size() - 1).uuid());
    }

    @Test
    void hundredPlayerFullDrainSnapshotUnchanged() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 100; i++) sl.upsert("f" + i, "F" + i, i * 3L, i + 1, Long.MIN_VALUE);
        var before = sl.snapshot();
        sl.snapshot().clear();
        assertEquals(100, sl.size());
        assertEquals(before.get(0).uuid(), sl.snapshot().get(0).uuid());
        assertEquals(100, sl.snapshot().size());
    }

    @Test
    void sixtyPlayerRenameKeepsPositionInSnapshot() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 60; i++) sl.upsert("n" + i, "N" + i, i * 10L, i + 1, Long.MIN_VALUE);
        sl.upsert("n59", "Renamed", 590L, 60, 590L);
        assertEquals(60, sl.size());
        assertEquals("n59", sl.snapshot().get(0).uuid());
        assertEquals("Renamed", sl.snapshot().get(0).name());
    }

    @Test
    void hundredPlayerRemoveEveryOtherKeepsHalf() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 100; i++) sl.upsert("h" + i, "H" + i, i * 10L, i + 1, Long.MIN_VALUE);
        for (int i = 0; i < 100; i += 2) assertTrue(sl.remove("h" + i, (long) i * 10, i + 1));
        assertEquals(50, sl.size());
        var snap = sl.snapshot();
        assertEquals("h99", snap.get(0).uuid());
        assertEquals("h1", snap.get(snap.size() - 1).uuid());
    }

    @Test
    void hundredTwentyPlayerAscendingSnapshotExact() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 120; i++) sl.upsert("a" + i, "A" + i, i * 6L, i + 1, Long.MIN_VALUE);
        var snap = sl.snapshot();
        assertEquals(120, snap.size());
        assertEquals("a119", snap.get(0).uuid());
        assertEquals("a0", snap.get(119).uuid());
    }

    @Test
    void hundredFiftyPlayerTieSnapshotStable() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 150; i++) sl.upsert("tie" + i, "T" + i, 777L, i + 1, Long.MIN_VALUE);
        var snap = sl.snapshot();
        for (int i = 0; i < 150; i++) assertEquals("tie" + i, snap.get(i).uuid());
    }

    @Test
    void seventyPlayerMixedScoresSnapshotMonotonic() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 70; i++) sl.upsert("mix" + i, "M" + i, (69 - i) * 17L, i + 1, Long.MIN_VALUE);
        var snap = sl.snapshot();
        assertEquals("mix0", snap.get(0).uuid());
        for (int i = 1; i < snap.size(); i++) {
            assertTrue(snap.get(i - 1).score() >= snap.get(i).score());
        }
    }

    @Test
    void eightyPlayerRemoveTopTwentyPromotesNext() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 80; i++) sl.upsert("top" + i, "T" + i, i * 10L, i + 1, Long.MIN_VALUE);
        for (int i = 79; i >= 60; i--) assertTrue(sl.remove("top" + i, (long) i * 10, i + 1));
        assertEquals(60, sl.size());
        assertEquals("top59", sl.snapshot().get(0).uuid());
    }

    @Test
    void ninetyPlayerBoostBottomThreeToTop() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 90; i++) sl.upsert("pool" + i, "P" + i, i * 10L, i + 1, Long.MIN_VALUE);
        sl.upsert("pool0", "P0", 9000L, 1, 0L);
        sl.upsert("pool1", "P1", 8000L, 2, 10L);
        sl.upsert("pool2", "P2", 7000L, 3, 20L);
        var snap = sl.snapshot();
        assertEquals("pool0", snap.get(0).uuid());
        assertEquals("pool1", snap.get(1).uuid());
        assertEquals("pool2", snap.get(2).uuid());
    }

    @Test
    void fiftyPlayerAtMatchesSnapshotForAllRanks() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 50; i++) sl.upsert("row" + i, "R" + i, i * 13L, i + 1, Long.MIN_VALUE);
        var snap = sl.snapshot();
        for (int r = 1; r <= 50; r++) assertEquals(snap.get(r - 1).uuid(), sl.at(r).uuid());
    }

    @Test
    void hundredPlayerClearViaRemoveByIdentityEmpties() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 100; i++) sl.upsert("clr" + i, "C" + i, i, i + 1, Long.MIN_VALUE);
        for (int i = 0; i < 100; i++) assertTrue(sl.removeByIdentity("clr" + i));
        assertEquals(0, sl.size());
        assertTrue(sl.snapshot().isEmpty());
    }

    @Test
    void sixtyPlayerNegativeAndPositiveSnapshotSplit() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 30; i++) sl.upsert("pp" + i, "P" + i, 500L + i, i + 1, Long.MIN_VALUE);
        for (int i = 0; i < 30; i++) sl.upsert("nn" + i, "N" + i, -500L - i, 31 + i, Long.MIN_VALUE);
        var snap = sl.snapshot();
        assertEquals("pp29", snap.get(0).uuid());
        assertEquals("nn29", snap.get(59).uuid());
    }

    @Test
    void fortyPlayerReinsertSameScoreKeepsJoinOrder() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 40; i++) sl.upsert("ord" + i, "O" + i, 250L, i + 1, Long.MIN_VALUE);
        sl.upsert("ord0", "Ord0-New", 250L, 1, 250L);
        assertEquals(40, sl.size());
        assertEquals("ord0", sl.snapshot().get(0).uuid());
        assertEquals("Ord0-New", sl.snapshot().get(0).name());
    }

    @Test
    void twoHundredPlayerSnapshotHeadAndTailExact() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 200; i++) sl.upsert("big" + i, "B" + i, i * 2L, i + 1, Long.MIN_VALUE);
        var snap = sl.snapshot();
        assertEquals("big199", snap.get(0).uuid());
        assertEquals("big0", snap.get(199).uuid());
        assertEquals(398L, snap.get(0).score());
        assertEquals(0L, snap.get(199).score());
    }

    @Test
    void fiftyFivePlayerRemoveMiddleKeepsNeighbors() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 55; i++) sl.upsert("mid" + i, "M" + i, 300L, i + 1, Long.MIN_VALUE);
        assertTrue(sl.remove("mid27", 300L, 28));
        var snap = sl.snapshot();
        assertEquals(54, snap.size());
        assertEquals("mid0", snap.get(0).uuid());
        assertEquals("mid54", snap.get(53).uuid());
    }

    @Test
    void hundredPlayerSequentialRerankKeepsAll() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 100; i++) sl.upsert("seq" + i, "S" + i, i * 10L, i + 1, Long.MIN_VALUE);
        for (int i = 0; i < 10; i++) sl.upsert("seq" + i, "S" + i, 5000L + i, i + 1, (long) i * 10);
        assertEquals(100, sl.size());
        var snap = sl.snapshot();
        assertEquals("seq9", snap.get(0).uuid());
        assertEquals("seq99", snap.get(10).uuid());
    }

    @Test
    void sixtyPlayerZeroScoresSnapshotByJoin() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 60; i++) sl.upsert("zero" + i, "Z" + i, 0L, 60 - i, Long.MIN_VALUE);
        var snap = sl.snapshot();
        assertEquals(60, snap.size());
        assertEquals("zero59", snap.get(0).uuid());
        assertEquals("zero0", snap.get(59).uuid());
    }

    @Test
    void hundredPlayerRemoveUnknownKeepsSize() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 100; i++) sl.upsert("keep" + i, "K" + i, i * 5L, i + 1, Long.MIN_VALUE);
        assertFalse(sl.remove("ghost", 100L, 1));
        assertFalse(sl.removeByIdentity("ghost2"));
        assertEquals(100, sl.size());
    }

    @Test
    void fiftyPlayerUpsertNewNameReflectsInSnapshot() {
        RankedSkipList sl = new RankedSkipList();
        for (int i = 0; i < 50; i++) sl.upsert("nm" + i, "Old" + i, i * 10L, i + 1, Long.MIN_VALUE);
        sl.upsert("nm49", "NewTop", 490L, 50, 490L);
        assertEquals("NewTop", sl.snapshot().get(0).name());
        assertEquals(50, sl.size());
    }
}
