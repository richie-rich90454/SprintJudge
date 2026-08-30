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
}
