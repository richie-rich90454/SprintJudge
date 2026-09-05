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
}
