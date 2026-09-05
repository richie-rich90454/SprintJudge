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
}
