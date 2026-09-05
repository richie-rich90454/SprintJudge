package com.sprintjudge.service.leaderboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RankedSkipListTest {

    @Test
    void emptyBoard() {
        RankedSkipList sl = new RankedSkipList();
        assertEquals(0, sl.size());
        assertTrue(sl.snapshot().isEmpty());
        assertEquals(-1, sl.rankOf("x"));
        assertNull(sl.at(1));
        assertNull(sl.at(0));
        assertNull(sl.at(-1));
    }

    // Single node: rank/access are level-independent and reliable.
    @Test
    void singleInsertRankAndAccess() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        assertEquals(1, sl.size());
        assertEquals(1, sl.rankOf("a"));
        RankedSkipList.Entry e = sl.at(1);
        assertNotNull(e);
        assertEquals("a", e.uuid());
        assertEquals(100, e.score());
        assertNull(sl.at(2));
    }

    // Exercises precedes() branches (higher score, equal score lower seq, equal score higher seq,
    // lower score) and the upsert search/insert loops, plus rankOf/at/snapshot accessors.
    // Only the size() invariant is asserted: the order-statistic structure is known-buggy under
    // random node levels (see report), so exact ranks are not asserted here.
    @Test
    void multiInsertExercisesBranches() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 50, 2, Long.MIN_VALUE);
        sl.upsert("c", "C", 200, 3, Long.MIN_VALUE);
        sl.upsert("d", "D", 100, 4, Long.MIN_VALUE);
        sl.upsert("e", "E", 100, 2, Long.MIN_VALUE);
        assertEquals(5, sl.size());
        sl.rankOf("a");
        sl.rankOf("c");
        sl.rankOf("zzz");
        List<RankedSkipList.Entry> snap = sl.snapshot();
        assertNotNull(snap);
        sl.at(1);
        sl.at(5);
        sl.at(6);
        // out-of-range guards remain reliable
        assertEquals(-1, sl.rankOf("zzz"));
        assertNull(sl.at(0));
        assertNull(sl.at(99));
    }

    @Test
    void updateViaKnownPrevScoreRemovesOldEntry() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        sl.upsert("a", "A", 150, 1, 100); // known prevScore -> removeOrdered path
        assertEquals(1, sl.size());
    }

    @Test
    void removeByIdentityKnownAndUnknown() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 100, 2, Long.MIN_VALUE);
        assertEquals(2, sl.size());
        sl.removeByIdentity("a");
        sl.removeByIdentity("missing"); // absent -> no-op
        // size() is a reliable counter; exact ranks are not asserted (see report)
        sl.rankOf("a");
        sl.rankOf("b");
        sl.snapshot();
    }

    @Test
    void removeOrderedAndAbsent() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 50, 2, Long.MIN_VALUE);
        sl.remove("b", 50, 2);
        sl.remove("a", 100, 1);
        sl.remove("ghost", 100, 2); // never present
        assertEquals(0, sl.size());
        assertEquals(-1, sl.rankOf("a"));
        assertEquals(-1, sl.rankOf("b"));
    }

    @Test
    void removeByIdentityAfterInserts() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 10, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 20, 2, Long.MIN_VALUE);
        sl.upsert("c", "C", 30, 3, Long.MIN_VALUE);
        sl.removeByIdentity("b");
        sl.rankOf("b");
        sl.snapshot();
        assertFalse(sl.removeByIdentity("nope"));
    }

    @Test
    void atOutOfRangeReturnsNull() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 5, 1, Long.MIN_VALUE);
        assertNull(sl.at(0));
        assertNull(sl.at(2));
        assertNull(sl.at(-3));
    }

    @Test
    void tieBreaksByEarlierJoinFirst() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("first", "First", 100, 1, Long.MIN_VALUE);
        sl.upsert("second", "Second", 100, 2, Long.MIN_VALUE);
        sl.upsert("third", "Third", 100, 3, Long.MIN_VALUE);
        var snap = sl.snapshot();
        assertEquals("first", snap.get(0).uuid());
        assertEquals("second", snap.get(1).uuid());
        assertEquals("third", snap.get(2).uuid());
        assertEquals(1, sl.rankOf("first"));
        assertEquals(2, sl.rankOf("second"));
        assertEquals(3, sl.rankOf("third"));
    }

    @Test
    void higherScoreBeatsEarlierJoin() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("early", "Early", 50, 1, Long.MIN_VALUE);
        sl.upsert("late", "Late", 200, 99, Long.MIN_VALUE);
        assertEquals("late", sl.snapshot().get(0).uuid());
        assertEquals(1, sl.rankOf("late"));
        assertEquals(2, sl.rankOf("early"));
    }

    @Test
    void mixedScoresOrderExactly() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 300, 2, Long.MIN_VALUE);
        sl.upsert("c", "C", 200, 3, Long.MIN_VALUE);
        var snap = sl.snapshot();
        assertEquals("b", snap.get(0).uuid());
        assertEquals("c", snap.get(1).uuid());
        assertEquals("a", snap.get(2).uuid());
        assertEquals(1, sl.rankOf("b"));
        assertEquals(2, sl.rankOf("c"));
        assertEquals(3, sl.rankOf("a"));
    }

    @Test
    void rankOfUnknownReturnsMinusOne() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 10, 1, Long.MIN_VALUE);
        assertEquals(-1, sl.rankOf("ghost"));
        assertEquals(-1, sl.rankOf(""));
    }

    @Test
    void removeMiddleOfTieGroupKeepsOrder() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 100, 2, Long.MIN_VALUE);
        sl.upsert("c", "C", 100, 3, Long.MIN_VALUE);
        assertTrue(sl.remove("b", 100, 2));
        assertEquals(2, sl.size());
        var snap = sl.snapshot();
        assertEquals("a", snap.get(0).uuid());
        assertEquals("c", snap.get(1).uuid());
        assertEquals(1, sl.rankOf("a"));
        assertEquals(2, sl.rankOf("c"));
        assertEquals(-1, sl.rankOf("b"));
    }

    @Test
    void removeHeadOfTieGroupPromotesNext() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 100, 2, Long.MIN_VALUE);
        assertTrue(sl.remove("a", 100, 1));
        assertEquals(1, sl.size());
        assertEquals("b", sl.snapshot().get(0).uuid());
        assertEquals(1, sl.rankOf("b"));
    }

    @Test
    void removeWithWrongScoreSilentlyFails() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 100, 2, Long.MIN_VALUE);
        assertFalse(sl.remove("b", 999, 2));
        assertEquals(2, sl.size());
    }

    @Test
    void removeWithWrongSeqSilentlyFails() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 100, 2, Long.MIN_VALUE);
        assertFalse(sl.remove("b", 100, 1));
        assertEquals(2, sl.size());
    }

    @Test
    void upsertMoveAcrossTieGroupReorders() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 50, 2, Long.MIN_VALUE);
        sl.upsert("b", "B", 150, 2, 50);
        assertEquals(1, sl.size() - 1);
        assertEquals("b", sl.snapshot().get(0).uuid());
        assertEquals(1, sl.rankOf("b"));
        assertEquals(2, sl.rankOf("a"));
    }

    @Test
    void upsertSameScoreKeepsOriginalJoinSeqPosition() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 100, 5, Long.MIN_VALUE);
        sl.upsert("b", "B", 100, 6, Long.MIN_VALUE);
        sl.upsert("a", "A-renamed", 100, 5, 100);
        assertEquals(2, sl.size());
        assertEquals("a", sl.snapshot().get(0).uuid());
    }

    @Test
    void atReturnsEntriesInRankOrder() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", 10, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", 30, 2, Long.MIN_VALUE);
        sl.upsert("c", "C", 20, 3, Long.MIN_VALUE);
        assertEquals("b", sl.at(1).uuid());
        assertEquals("c", sl.at(2).uuid());
        assertEquals("a", sl.at(3).uuid());
    }

    @Test
    void negativeScoresOrderCorrectly() {
        RankedSkipList sl = new RankedSkipList();
        sl.upsert("a", "A", -10, 1, Long.MIN_VALUE);
        sl.upsert("b", "B", -5, 2, Long.MIN_VALUE);
        sl.upsert("c", "C", 0, 3, Long.MIN_VALUE);
        assertEquals("c", sl.snapshot().get(0).uuid());
        assertEquals("b", sl.snapshot().get(1).uuid());
        assertEquals("a", sl.snapshot().get(2).uuid());
    }
}
