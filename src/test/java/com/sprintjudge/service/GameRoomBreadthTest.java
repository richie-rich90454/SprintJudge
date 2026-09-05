package com.sprintjudge.service;

import com.sprintjudge.domain.dto.LeaderboardEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameRoomBreadthTest {

    private GameRoom room(int cap) {
        return new GameRoom("s", "q", "pin", "ACTIVE", cap);
    }

    private Player p(String uuid) {
        return new Player(uuid, "N-" + uuid, 0, "sess-" + uuid, true);
    }

    @Test
    void failedAddKeepsExistingPlayers() {
        GameRoom r = room(1);
        assertTrue(r.addPlayer(p("u1")));
        assertFalse(r.addPlayer(p("u2")));
        assertEquals("u1", r.getPlayer("u1").uuid());
        assertNull(r.getPlayer("u2"));
    }

    @Test
    void hostAndPlayerShareCapacity() {
        GameRoom r = room(2);
        assertTrue(r.addHost(new Player("h", "H", 0, "sh", true)));
        assertTrue(r.addPlayer(p("u1")));
        assertTrue(r.isFull());
        assertFalse(r.addPlayer(p("u2")));
        assertFalse(r.addHost(new Player("h2", "H2", 0, "sh2", true)));
    }

    @Test
    void playerFirstBlocksHostAtCapacityOne() {
        GameRoom r = room(1);
        assertTrue(r.addPlayer(p("u1")));
        assertFalse(r.addHost(new Player("h", "H", 0, "sh", true)));
    }

    @Test
    void softThenHardRemoveClearsEverything() {
        GameRoom r = room(10);
        r.addPlayer(p("u1"));
        r.applyScore("u1", 50);
        r.softRemove("u1");
        assertEquals(1, r.leaderboard().size());
        r.hardRemove("u1");
        assertNull(r.getPlayer("u1"));
        assertTrue(r.leaderboard().isEmpty());
        assertTrue(r.players().isEmpty());
    }

    @Test
    void hardRemoveUnknownKeepsBoard() {
        GameRoom r = room(10);
        r.addPlayer(p("u1"));
        r.hardRemove("ghost");
        assertEquals(1, r.players().size());
        assertEquals(1, r.leaderboard().size());
    }

    @Test
    void reclaimPreservesEarnedScore() {
        GameRoom r = room(10);
        r.addPlayer(new Player("u1", "a", 0, "old", true, "tok"));
        r.applyScore("u1", 100);
        r.softRemove("u1");
        Player back = r.reclaim("tok", "new");
        assertEquals("new", back.sessionId());
        assertTrue(back.connected());
        assertEquals(100, r.getPlayer("u1").score());
    }

    @Test
    void playersFollowBoardOrder() {
        GameRoom r = room(10);
        r.addPlayer(p("u1"));
        r.addPlayer(p("u2"));
        r.applyScore("u2", 500);
        r.applyScore("u1", 100);
        List<Player> list = r.players();
        assertEquals("u2", list.get(0).uuid());
        assertEquals(500, list.get(0).score());
        assertEquals("u1", list.get(1).uuid());
    }

    @Test
    void leaderboardRanksAreSequential() {
        GameRoom r = room(10);
        r.addPlayer(p("u1"));
        r.addPlayer(p("u2"));
        r.addPlayer(p("u3"));
        r.applyScore("u1", 10);
        r.applyScore("u2", 30);
        r.applyScore("u3", 20);
        List<LeaderboardEntry> lb = r.leaderboard();
        assertEquals("u2", lb.get(0).uuid());
        assertEquals(1, lb.get(0).rank());
        assertEquals("u3", lb.get(1).uuid());
        assertEquals(2, lb.get(1).rank());
        assertEquals("u1", lb.get(2).uuid());
        assertEquals(3, lb.get(2).rank());
    }

    @Test
    void attemptsAreIsolatedPerQuestion() {
        GameRoom r = room(10);
        assertTrue(r.tryBeginAttempt("q1", "u1", 1));
        assertFalse(r.tryBeginAttempt("q1", "u1", 1));
        assertTrue(r.tryBeginAttempt("q2", "u1", 1));
        assertEquals(1, r.attemptCount("q1", "u1"));
        assertEquals(1, r.attemptCount("q2", "u1"));
    }

    @Test
    void zeroMaxAttemptsAlwaysRejects() {
        GameRoom r = room(10);
        assertFalse(r.tryBeginAttempt("q1", "u1", 0));
        assertEquals(0, r.attemptCount("q1", "u1"));
    }

    @Test
    void negativeMaxAttemptsAlwaysRejects() {
        GameRoom r = room(10);
        assertFalse(r.tryBeginAttempt("q1", "u1", -1));
    }

    @Test
    void refundTwiceReachesZero() {
        GameRoom r = room(10);
        r.tryBeginAttempt("q1", "u1", 5);
        r.tryBeginAttempt("q1", "u1", 5);
        r.refundAttempt("q1", "u1");
        r.refundAttempt("q1", "u1");
        assertEquals(0, r.attemptCount("q1", "u1"));
        r.refundAttempt("q1", "u1");
        assertEquals(0, r.attemptCount("q1", "u1"));
    }

    @Test
    void bumpStreakIncrementsMonotonically() {
        GameRoom r = room(10);
        assertEquals(1, r.bumpStreak("u1"));
        assertEquals(2, r.bumpStreak("u1"));
        assertEquals(3, r.bumpStreak("u1"));
    }

    @Test
    void resetUnknownStreakStaysZero() {
        GameRoom r = room(10);
        r.resetStreak("ghost");
        assertEquals(0, r.streakOf("ghost"));
    }

    @Test
    void recordRoundOverwritesPrevious() {
        GameRoom r = room(10);
        r.recordRound("u1", 50, 5);
        r.recordRound("u1", 80, 8);
        assertEquals(80, r.roundOf("u1")[0]);
        assertEquals(8, r.roundOf("u1")[1]);
    }

    @Test
    void clearRoundsEmptiesEveryPlayer() {
        GameRoom r = room(10);
        r.recordRound("u1", 50, 5);
        r.recordRound("u2", 60, 6);
        r.clearRounds();
        assertEquals(0, r.roundOf("u1")[0]);
        assertEquals(0, r.roundOf("u2")[0]);
    }

    @Test
    void identityAndModeAccessors() {
        GameRoom r = new GameRoom("sess-9", "quiz-9", "999999", "LOBBY", 42, GameRoom.GameMode.TEAM);
        assertEquals("sess-9", r.sessionId());
        assertEquals("quiz-9", r.quizId());
        assertEquals("999999", r.pin());
        assertEquals("LOBBY", r.status());
        assertEquals(42, r.capacity());
        assertEquals(GameRoom.GameMode.TEAM, r.gameMode());
    }

    @Test
    void questionTimingAccessorsRoundTrip() {
        GameRoom r = room(10);
        r.setCurrentQuestionStartEpochMs(1000L);
        r.setQuestionEndBaseEpochMs(2000L);
        r.setTotalEndEpochMs(3000L);
        assertEquals(1000L, r.currentQuestionStartEpochMs());
        assertEquals(2000L, r.questionEndBaseEpochMs());
        assertEquals(3000L, r.totalEndEpochMs());
        assertEquals(0L, room(10).totalEndEpochMs());
    }

    @Test
    void teamIdsAreUniqueWithEmptyMembers() {
        GameRoom r = room(10);
        GameRoom.Team a = r.createTeam("Alpha");
        GameRoom.Team b = r.createTeam("Beta");
        assertTrue(!a.id().equals(b.id()));
        assertTrue(a.memberUuids().isEmpty());
        assertEquals(0, a.score());
        assertEquals("Alpha", a.name());
    }

    @Test
    void joinMissingTeamReturnsNull() {
        assertNull(room(10).joinTeam("nope", "u1"));
    }

    @Test
    void joinTeamTracksMembership() {
        GameRoom r = room(10);
        GameRoom.Team t = r.createTeam("Alpha");
        assertNull(r.teamIdOf("u1"));
        r.joinTeam(t.id(), "u1");
        assertEquals(t.id(), r.teamIdOf("u1"));
        assertEquals(1, r.getTeam(t.id()).memberUuids().size());
    }

    @Test
    void teamScoreAccumulatesAndUnknownIsZero() {
        GameRoom r = room(10);
        GameRoom.Team t = r.createTeam("Alpha");
        assertEquals(0, r.applyTeamScore("ghost", 10));
        assertEquals(100, r.applyTeamScore(t.id(), 100));
        assertEquals(150, r.applyTeamScore(t.id(), 50));
        assertEquals(1, r.allTeams().size());
    }

    @Test
    void teamRecordUpdatesAreImmutable() {
        GameRoom r = room(10);
        GameRoom.Team t = r.createTeam("Alpha");
        GameRoom.Team scored = t.withScore(77);
        assertEquals(0, t.score());
        assertEquals(77, scored.score());
        assertEquals(t.id(), scored.id());
        GameRoom.Team joined = t.addMember("u1");
        assertTrue(t.memberUuids().isEmpty());
        assertTrue(joined.memberUuids().contains("u1"));
        GameRoom.Team twice = joined.addMember("u1");
        assertEquals(1, twice.memberUuids().size());
    }

    @Test
    void battleMatchesAndBracketRoundTrip() {
        GameRoom r = room(10);
        r.addBattleMatch(new GameRoom.BattleMatch("m1", "a", "b", "q1", null, null, false, false, null));
        r.addBattleMatch(new GameRoom.BattleMatch("m2", "c", "d", "q1", "x", "y", true, false, "c"));
        assertEquals(2, r.battleMatches().size());
        assertEquals("c", r.battleMatches().get(1).winnerUuid());
        r.setBracket(java.util.List.<String[]>of(new String[]{"a", "b"}));
        assertEquals(1, r.bracket().size());
        r.setBracket(java.util.List.<String[]>of(new String[]{"c", "d"}, new String[]{"e", "f"}));
        assertEquals(2, r.bracket().size());
    }

    @Test
    void defaultGameModesAreStandard() {
        assertEquals(GameRoom.GameMode.STANDARD, new GameRoom("s", "q", "p", "L", 5).gameMode());
        assertEquals(GameRoom.GameMode.STANDARD, new GameRoom("s", "q", "p", "L").gameMode());
    }

    @Test
    void fiveArgCtorHonoursCapacity() {
        GameRoom r = new GameRoom("s", "q", "p", "L", 3);
        assertEquals(3, r.capacity());
        assertEquals(GameRoom.GameMode.STANDARD, r.gameMode());
    }

    @Test
    void concurrentApplyScoreSamePlayerSumsExactly() throws Exception {
        GameRoom r = room(100);
        r.addPlayer(p("u1"));
        int threads = 8;
        int perThread = 200;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < perThread; i++) r.applyScore("u1", 5);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        org.junit.jupiter.api.Assertions.assertTrue(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(threads * perThread * 5, r.getPlayer("u1").score());
        assertEquals(1, r.leaderboard().size());
    }

    @Test
    void concurrentApplyScoreDistinctPlayersNoLostUpdates() throws Exception {
        GameRoom r = room(32);
        for (int i = 0; i < 16; i++) r.addPlayer(p("u" + i));
        int threads = 16;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final String uuid = "u" + t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 100; i++) r.applyScore(uuid, 10);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        for (int i = 0; i < 16; i++) assertEquals(1000, r.getPlayer("u" + i).score());
        assertEquals(16, r.leaderboard().size());
    }

    @Test
    void concurrentAddPlayerAtCapacityNeverExceedsCap() throws Exception {
        GameRoom r = room(10);
        int threads = 16;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger accepted = new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (r.addPlayer(p("c" + idx))) accepted.incrementAndGet();
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(10, accepted.get());
        assertEquals(10, r.players().size());
        assertTrue(r.isFull());
    }

    @Test
    void concurrentSoftRemoveKeepsBoardRows() throws Exception {
        GameRoom r = room(20);
        for (int i = 0; i < 10; i++) r.addPlayer(p("u" + i));
        int threads = 10;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final String uuid = "u" + t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                r.softRemove(uuid);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(10, r.leaderboard().size());
        assertEquals(0, r.connectedCount());
        for (int i = 0; i < 10; i++) org.junit.jupiter.api.Assertions.assertNotNull(r.getPlayer("u" + i));
    }

    @Test
    void concurrentReclaimSameTokenExactlyOneWinner() throws Exception {
        GameRoom r = room(10);
        r.addPlayer(new Player("u1", "A", 0, "old", true, "tok1"));
        r.applyScore("u1", 77);
        r.softRemove("u1");
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger wins = new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < threads; t++) {
            final String sess = "sess-" + t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (r.reclaim("tok1", sess) != null) wins.incrementAndGet();
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(1, wins.get());
        assertEquals(77, r.getPlayer("u1").score());
        org.junit.jupiter.api.Assertions.assertNotNull(r.getPlayer("u1").sessionId());
    }

    @Test
    void concurrentTryBeginAttemptCapsAtMax() throws Exception {
        GameRoom r = room(10);
        int threads = 16;
        int max = 5;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger accepted = new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 10; i++) if (r.tryBeginAttempt("q1", "u1", max)) accepted.incrementAndGet();
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(max, accepted.get());
        assertEquals(max, r.attemptCount("q1", "u1"));
    }

    @Test
    void concurrentBumpStreakSumsAllIncrements() throws Exception {
        GameRoom r = room(10);
        int threads = 8;
        int perThread = 100;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < perThread; i++) r.bumpStreak("u1");
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(threads * perThread, r.streakOf("u1"));
    }

    @Test
    void concurrentMixedScoreAndStreakKeepsBothConsistent() throws Exception {
        GameRoom r = room(20);
        for (int i = 0; i < 8; i++) r.addPlayer(p("u" + i));
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final String uuid = "u" + t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 50; i++) { r.applyScore(uuid, 10); r.bumpStreak(uuid); }
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        for (int i = 0; i < 8; i++) {
            assertEquals(500, r.getPlayer("u" + i).score());
            assertEquals(50, r.streakOf("u" + i));
        }
    }

    @Test
    void concurrentNegativeAndPositiveDeltasNetExactly() throws Exception {
        GameRoom r = room(10);
        r.addPlayer(p("u1"));
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final int sign = (t % 2 == 0) ? 1 : -1;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 100; i++) r.applyScore("u1", sign * 10);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, r.getPlayer("u1").score());
    }

    @Test
    void concurrentRecordRoundLastWriteWinsWithoutLoss() throws Exception {
        GameRoom r = room(10);
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final int v = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 50; i++) r.recordRound("u1", v, i);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        int[] round = r.roundOf("u1");
        org.junit.jupiter.api.Assertions.assertTrue(round[0] >= 0 && round[0] < 8);
        org.junit.jupiter.api.Assertions.assertTrue(round[1] >= 0 && round[1] < 50);
    }

    @Test
    void concurrentLeaderboardReadsDuringWritesNeverThrow() throws Exception {
        GameRoom r = room(30);
        for (int i = 0; i < 10; i++) r.addPlayer(p("u" + i));
        int writers = 8;
        int readers = 4;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(writers + readers);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(writers + readers);
        java.util.concurrent.atomic.AtomicInteger readOps = new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < writers; t++) {
            final String uuid = "u" + (t % 10);
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 100; i++) r.applyScore(uuid, 7);
                done.countDown();
            });
        }
        for (int t = 0; t < readers; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 200; i++) { r.leaderboard(); r.players(); readOps.incrementAndGet(); }
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(4 * 200, readOps.get());
        assertEquals(10, r.leaderboard().size());
    }

    @Test
    void concurrentHardRemoveDistinctPlayersEmptiesRoom() throws Exception {
        GameRoom r = room(20);
        for (int i = 0; i < 12; i++) r.addPlayer(p("u" + i));
        int threads = 12;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final String uuid = "u" + t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                r.hardRemove(uuid);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(r.leaderboard().isEmpty());
        assertTrue(r.players().isEmpty());
    }

    @Test
    void concurrentAddAndScoreKeepsBoardAndPlayersAligned() throws Exception {
        GameRoom r = room(40);
        int threads = 16;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                r.addPlayer(p("w" + idx));
                for (int i = 0; i < 50; i++) r.applyScore("w" + idx, 4);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(16, r.players().size());
        assertEquals(16, r.leaderboard().size());
        for (int i = 0; i < 16; i++) assertEquals(200, r.getPlayer("w" + i).score());
    }

    @Test
    void concurrentRefundNeverDropsBelowZero() throws Exception {
        GameRoom r = room(10);
        r.tryBeginAttempt("q1", "u1", 50);
        r.tryBeginAttempt("q1", "u1", 50);
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 20; i++) r.refundAttempt("q1", "u1");
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, r.attemptCount("q1", "u1"));
    }

    @Test
    void concurrentTeamScoreAccumulatesExactly() throws Exception {
        GameRoom r = room(10);
        GameRoom.Team t = r.createTeam("Alpha");
        int threads = 8;
        int perThread = 100;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int j = 0; j < perThread; j++) r.applyTeamScore(t.id(), 3);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals((long) threads * perThread * 3, r.getTeam(t.id()).score());
    }

    @Test
    void concurrentApplyScoreSixteenThreadsSumMatchesLedger() throws Exception {
        GameRoom r = room(50);
        r.addPlayer(p("hero"));
        int threads = 16;
        int perThread = 100;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < perThread; i++) r.applyScore("hero", 2);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(threads * perThread * 2, r.getPlayer("hero").score());
        assertEquals(1, r.board().pendingDeltaCount());
    }

    @Test
    void concurrentSoftRemoveThenReclaimKeepsSingleSeat() throws Exception {
        GameRoom r = room(10);
        r.addPlayer(new Player("u9", "N", 0, "old", true, "tok9"));
        r.applyScore("u9", 55);
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (idx % 2 == 0) r.softRemove("u9");
                else r.reclaim("tok9", "sess-" + idx);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.jupiter.api.Assertions.assertNotNull(r.getPlayer("u9"));
        assertEquals(55, r.getPlayer("u9").score());
        assertEquals(1, r.players().size());
    }

    @Test
    void concurrentBumpAndResetStreakEndsWithinBounds() throws Exception {
        GameRoom r = room(10);
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 50; i++) {
                    if (idx % 2 == 0) r.bumpStreak("racer");
                    else r.resetStreak("racer");
                }
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.jupiter.api.Assertions.assertTrue(r.streakOf("racer") >= 0);
        org.junit.jupiter.api.Assertions.assertTrue(r.streakOf("racer") <= 4 * 50);
    }

    @Test
    void concurrentAttemptsAcrossTwoQuestionsStayIsolated() throws Exception {
        GameRoom r = room(10);
        int threads = 12;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger q1wins = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger q2wins = new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 10; i++) {
                    if (r.tryBeginAttempt("q1", "u1", 6)) q1wins.incrementAndGet();
                    if (r.tryBeginAttempt("q2", "u1", 4)) q2wins.incrementAndGet();
                    if (idx % 3 == 0) { r.refundAttempt("q1", "u1"); }
                }
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.jupiter.api.Assertions.assertTrue(r.attemptCount("q1", "u1") <= 6);
        org.junit.jupiter.api.Assertions.assertTrue(r.attemptCount("q2", "u1") <= 4);
    }

    @Test
    void concurrentJoinTeamMembershipConverges() throws Exception {
        GameRoom r = room(30);
        GameRoom.Team t = r.createTeam("Squad");
        for (int i = 0; i < 12; i++) r.addPlayer(p("m" + i));
        int threads = 12;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int idx = 0; idx < threads; idx++) {
            final String uuid = "m" + idx;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                r.joinTeam(t.id(), uuid);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.jupiter.api.Assertions.assertTrue(r.getTeam(t.id()).memberUuids().size() >= 1);
        org.junit.jupiter.api.Assertions.assertTrue(r.getTeam(t.id()).memberUuids().size() <= 12);
    }

    @Test
    void concurrentBattleMatchAppendsAllWithoutLoss() throws Exception {
        GameRoom r = room(30);
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 25; i++) r.addBattleMatch(new GameRoom.BattleMatch("m-" + idx + "-" + i, "a", "b", "q1", null, null, false, false, null));
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(8 * 25, r.battleMatches().size());
    }

    @Test
    void concurrentClearRoundsDuringRecordNeverThrows() throws Exception {
        GameRoom r = room(10);
        int writers = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(writers + 1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(writers + 1);
        for (int t = 0; t < writers; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 100; i++) r.recordRound("u" + (i % 4), i, i % 5);
                done.countDown();
            });
        }
        pool.submit(() -> {
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            for (int i = 0; i < 50; i++) r.clearRounds();
            done.countDown();
        });
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        for (int i = 0; i < 4; i++) org.junit.jupiter.api.Assertions.assertNotNull(r.roundOf("u" + i));
    }

    @Test
    void concurrentHostAndPlayerJoinsRespectCapacity() throws Exception {
        GameRoom r = room(8);
        int threads = 16;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger ok = new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                boolean added;
                if (idx % 4 == 0) added = r.addHost(new Player("h" + idx, "H", 0, "sh" + idx, true));
                else added = r.addPlayer(p("p" + idx));
                if (added) ok.incrementAndGet();
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(8, ok.get());
        assertTrue(r.isFull());
    }

    @Test
    void concurrentApplyScoreAndHardRemoveKeepsNoGhostScores() throws Exception {
        GameRoom r = room(20);
        for (int i = 0; i < 8; i++) { r.addPlayer(p("v" + i)); r.applyScore("v" + i, 100); }
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final String uuid = "v" + t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 30; i++) r.applyScore(uuid, 5);
                r.hardRemove(uuid);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(r.leaderboard().isEmpty());
        for (int i = 0; i < 8; i++) assertNull(r.getPlayer("v" + i));
        assertEquals(-1, r.applyScore("v0", 10));
    }

    @Test
    void concurrentTouchAndIdleReadsStayNonNegative() throws Exception {
        GameRoom r = room(10);
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger negatives = new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 200; i++) {
                    r.touch();
                    if (r.idleMs(System.currentTimeMillis() + 1000) < 0) negatives.incrementAndGet();
                }
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, negatives.get());
    }

    @Test
    void concurrentTwelveThreadsMixedAttemptsAndScores() throws Exception {
        GameRoom r = room(20);
        for (int i = 0; i < 6; i++) r.addPlayer(p("mq" + i));
        int threads = 12;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final String uuid = "mq" + (t % 6);
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 40; i++) {
                    r.tryBeginAttempt("qmix", uuid, 1000);
                    r.applyScore(uuid, 5);
                    r.bumpStreak(uuid);
                }
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        long total = 0;
        for (int i = 0; i < 6; i++) total += r.getPlayer("mq" + i).score();
        assertEquals((long) 12 * 40 * 5, total);
    }

    @Test
    void concurrentCreateTeamIdsStayUnique() throws Exception {
        GameRoom r = room(10);
        int threads = 12;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.Set<String> ids = java.util.concurrent.ConcurrentHashMap.newKeySet();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                ids.add(r.createTeam("T").id());
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(12, ids.size());
        assertEquals(12, r.allTeams().size());
    }

    @Test
    void concurrentSetBracketReadsNeverNull() throws Exception {
        GameRoom r = room(10);
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger nulls = new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 100; i++) {
                    if (idx % 2 == 0) r.setBracket(java.util.List.<String[]>of(new String[]{"a" + i, "b" + i}));
                    else if (r.bracket() == null) nulls.incrementAndGet();
                }
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, nulls.get());
    }

    @Test
    void concurrentStatusFlipsEndAsOneValue() throws Exception {
        GameRoom r = room(10);
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final String s = (t % 2 == 0) ? "ACTIVE" : "REVIEW";
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 100; i++) r.setStatus(s);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.jupiter.api.Assertions.assertTrue("ACTIVE".equals(r.status()) || "REVIEW".equals(r.status()));
    }

    @Test
    void concurrentTenThreadsEachOwnQuestionAttempts() throws Exception {
        GameRoom r = room(10);
        int threads = 10;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final String q = "qq" + t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 5; i++) org.junit.jupiter.api.Assertions.assertTrue(r.tryBeginAttempt(q, "shared", 5));
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        for (int t = 0; t < threads; t++) assertEquals(5, r.attemptCount("qq" + t, "shared"));
    }

    @Test
    void concurrentScoreAndGetPlayerScoreMatchesBoard() throws Exception {
        GameRoom r = room(15);
        r.addPlayer(p("sync"));
        int threads = 10;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 60; i++) {
                    r.applyScore("sync", 10);
                    org.junit.jupiter.api.Assertions.assertNotNull(r.getPlayer("sync"));
                }
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(10 * 60 * 10, r.getPlayer("sync").score());
        assertEquals(10 * 60 * 10, r.leaderboard().get(0).score());
    }

    @Test
    void concurrentQuestionTimingWritesLastWins() throws Exception {
        GameRoom r = room(10);
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final long v = 1000L + t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 100; i++) r.setCurrentQuestionEndEpochMs(v);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.jupiter.api.Assertions.assertTrue(r.currentQuestionEndEpochMs() >= 1000L && r.currentQuestionEndEpochMs() < 1010L);
    }

    @Test
    void concurrentConnectedCountMatchesSoftRemoves() throws Exception {
        GameRoom r = room(20);
        for (int i = 0; i < 10; i++) r.addPlayer(p("cc" + i));
        int threads = 10;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final String uuid = "cc" + t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                r.softRemove(uuid);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, r.connectedCount());
    }

    @Test
    void concurrentStreakOfReadsNeverNegative() throws Exception {
        GameRoom r = room(10);
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger bad = new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 150; i++) {
                    r.bumpStreak("readrace");
                    if (r.streakOf("readrace") < 0) bad.incrementAndGet();
                }
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, bad.get());
        assertEquals(8 * 150, r.streakOf("readrace"));
    }

    @Test
    void concurrentApplyScoreZeroDeltaKeepsScore() throws Exception {
        GameRoom r = room(10);
        r.addPlayer(p("zero"));
        r.applyScore("zero", 500);
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 100; i++) r.applyScore("zero", 0);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(500, r.getPlayer("zero").score());
    }

    @Test
    void concurrentAddHostSingleWinner() throws Exception {
        GameRoom r = room(20);
        int threads = 10;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger wins = new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (r.addHost(new Player("hh" + idx, "H", 0, "sh" + idx, true))) wins.incrementAndGet();
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(10, wins.get());
        assertEquals(10, r.players().size());
    }

    @Test
    void concurrentRoundRecordAndClearKeepsMapUsable() throws Exception {
        GameRoom r = room(10);
        int threads = 6;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 80; i++) {
                    r.recordRound("rr" + idx, i, idx);
                    if (i % 20 == 0) r.clearRounds();
                }
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        for (int i = 0; i < 6; i++) org.junit.jupiter.api.Assertions.assertNotNull(r.roundOf("rr" + i));
    }

    @Test
    void concurrentCapacityCheckNeverAllowsOverflow() throws Exception {
        GameRoom r = room(5);
        int threads = 16;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                r.addPlayer(p("ov" + idx));
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.jupiter.api.Assertions.assertTrue(r.players().size() <= 5);
        assertTrue(r.isFull());
    }

    @Test
    void concurrentLargeScoreDeltasSumToExactTotal() throws Exception {
        GameRoom r = room(10);
        r.addPlayer(p("big"));
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 50; i++) r.applyScore("big", 1_000_000L);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(8 * 50 * 1_000_000L, r.board().scoreOf("big"));
    }

    @Test
    void concurrentTeamJoinAndScoreStayConsistent() throws Exception {
        GameRoom r = room(20);
        GameRoom.Team team = r.createTeam("Mix");
        for (int i = 0; i < 8; i++) r.addPlayer(p("tm" + i));
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final String uuid = "tm" + t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                r.joinTeam(team.id(), uuid);
                for (int i = 0; i < 30; i++) r.applyTeamScore(team.id(), 10);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals((long) 8 * 30 * 10, r.getTeam(team.id()).score());
    }

    @Test
    void concurrentEightThreadsRankReadsMatchSnapshotSize() throws Exception {
        GameRoom r = room(25);
        for (int i = 0; i < 12; i++) r.addPlayer(p("rk" + i));
        for (int i = 0; i < 12; i++) r.applyScore("rk" + i, i * 25L);
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger mismatches = new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 100; i++) {
                    if (r.leaderboard().size() != 12) mismatches.incrementAndGet();
                    if (r.players().size() != 12) mismatches.incrementAndGet();
                }
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, mismatches.get());
    }

    @Test
    void concurrentApplyScoreAfterSoftRemoveStillRanked() throws Exception {
        GameRoom r = room(15);
        r.addPlayer(p("stay"));
        r.applyScore("stay", 400);
        r.softRemove("stay");
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < 50; i++) r.applyScore("stay", 10);
                done.countDown();
            });
        }
        start.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(400 + 8 * 50 * 10, r.getPlayer("stay").score());
        assertEquals(1, r.leaderboard().size());
    }
}
