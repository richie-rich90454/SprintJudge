package com.sprintjudge.service;

import com.sprintjudge.domain.dto.LeaderboardEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@SuppressWarnings("all")
class GameRoomTest {

	@Test
	void addPlayerUnderCapacity() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 2);
		assertTrue(r.addPlayer(new Player("u1", "a", 0, "ss", true)));
		assertTrue(r.addPlayer(new Player("u2", "b", 0, "ss", true)));
		assertFalse(r.addPlayer(new Player("u3", "c", 0, "ss", true)));
		assertTrue(r.isFull());
	}

	@Test
	void legacyCtorDefaultsCap() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE");
		assertFalse(r.isFull());
		assertTrue(r.addPlayer(new Player("u", "n", 0, "ss", true)));
		assertEquals(500, r.capacity());
	}

	@Test
	void softRemoveKeepsBoardRow() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("u1", "a", 0, "ss", true));
		r.softRemove("u1");
		Player gp = r.getPlayer("u1");
		assertNotNull(gp);
		assertFalse(gp.isConnected());
		r.softRemove("missing");
	}

	@Test
	void hardRemoveDropsBoardRow() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("u1", "a", 0, "ss", true));
		r.hardRemove("u1");
		assertNull(r.getPlayer("u1"));
		r.hardRemove("missing");
	}

	@Test
	void reclaimFlow() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		assertNull(r.reclaim(null, "s"));
		assertNull(r.supersededSessionId(null));
		// a connected player forces the reclaim loop's !connected() short-circuit
		// branch
		r.addPlayer(new Player("u0", "z", 0, "ss", true, "othertok"));
		// a disconnected player with a non-matching token exercises the && false branch
		r.addPlayer(new Player("ux", "x", 0, "ss", false, "wrongtok"));
		r.addPlayer(new Player("u1", "a", 0, "old", false, "tok"));
		assertEquals("old", r.supersededSessionId("tok"));
		Player reclaimed = r.reclaim("tok", "news");
		assertNotNull(reclaimed);
		assertTrue(reclaimed.isConnected());
		assertEquals("news", reclaimed.getSessionId());
		// player is in the players map but not the board -> exercises players() second
		// loop
		List<Player> list = r.getPlayers();
		assertEquals(3, list.size());
		assertNull(r.reclaim("wrong", "x"));
		assertNull(r.supersededSessionId("wrong"));
	}

	@Test
	void playersWithOrphanBoardRow() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("u1", "a", 0, "ss", true));
		// board row with no matching players-map entry -> exercises players() null
		// check
		r.getBoard().join("ghost", "ghost");
		r.getBoard().remove("u1");
		List<Player> list = r.getPlayers();
		assertEquals(1, list.size());
		assertEquals("u1", list.get(0).getUuid());
	}

	@Test
	void getPlayerUnknown() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		assertNull(r.getPlayer("nope"));
	}

	@Test
	void emptyBattlePairsDoNotAppendABracketRound() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10, GameRoom.GameMode.BATTLE);
		r.beginBattleRound(List.of());
		assertEquals(0, r.getBracketRounds().size());
		r.beginBattleRound(List.of(new GameRoom.BattlePair("a", "b", null, false)));
		assertEquals(1, r.getBracketRounds().size());
	}

	@Test
	void bestScoreOnlyAwardsImprovements() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("u1", "a", 0, "ss", true));
		assertEquals(0, r.bestScore("q1", "u1"));
		assertEquals(100, r.applyBestScore("q1", "u1", 100));
		assertEquals(0, r.applyBestScore("q1", "u1", 80));
		assertEquals(20, r.applyBestScore("q1", "u1", 120));
		assertEquals(120, r.bestScore("q1", "u1"));
		assertEquals(120, r.getPlayer("u1").getScore());
	}

	@Test
	void applyScoreAndLeaderboard() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("u1", "a", 0, "ss", true));
		r.applyScore("u1", 100);
		List<LeaderboardEntry> lb = r.getLeaderboard();
		assertEquals(1, lb.size());
		assertEquals(100, lb.get(0).getScore());
	}

	@Test
	void attemptsAndStreaks() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		assertTrue(r.tryBeginAttempt("q1", "u1", 3));
		assertTrue(r.tryBeginAttempt("q1", "u1", 3));
		assertTrue(r.tryBeginAttempt("q1", "u1", 3));
		assertFalse(r.tryBeginAttempt("q1", "u1", 3));
		assertEquals(3, r.attemptCount("q1", "u1"));
		r.bumpStreak("u1");
		r.bumpStreak("u1");
		assertEquals(2, r.streakOf("u1"));
		r.resetStreak("u1");
		assertEquals(0, r.streakOf("u1"));
	}

	@Test
	void rounds() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.recordRound("u1", 50, 5);
		assertArrayEquals(new int[]{50, 5}, r.roundOf("u1"));
		assertArrayEquals(new int[]{0, 0}, r.roundOf("nope"));
		r.clearRounds();
		assertArrayEquals(new int[]{0, 0}, r.roundOf("u1"));
	}

	@Test
	void connectedCountAndLifecycle() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("u1", "a", 0, "ss", true));
		r.addPlayer(new Player("u2", "b", 0, "ss", false));
		assertEquals(1, r.connectedCount());
		r.touch();
		assertTrue(r.idleMs(System.currentTimeMillis() + 1000) > 0);
		assertEquals(10, r.capacity());
		r.setStatus("ENDED");
		assertEquals("ENDED", r.getStatus());
		r.setHostUuid("h");
		assertEquals("h", r.getHostUuid());
		r.setCurrentQuestionIndex(2);
		assertEquals(2, r.getCurrentQuestionIndex());
		r.setCurrentQuestionId("q");
		assertEquals("q", r.getCurrentQuestionId());
		r.setCurrentQuestionEndEpochMs(123L);
		assertEquals(123L, r.getCurrentQuestionEndEpochMs());
	}

	@Test
	void addHostAtCapacityReturnsFalse() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 1);
		assertTrue(r.addPlayer(new Player("u1", "a", 0, "s1", true)));
		assertTrue(r.isFull());
		assertFalse(r.addHost(new Player("h", "host", 0, "sh", true)));
	}

	@Test
	void addHostOccupiesSeatWithoutBoardRow() {
		GameRoom r = new GameRoom("s", "q", "pin", "LOBBY", 10);
		assertTrue(r.addHost(new Player("h", "Host", 0, "sh", true)));
		assertEquals(0, r.getLeaderboard().size());
		assertEquals(1, r.getPlayers().size());
		assertEquals("h", r.getPlayers().get(0).getUuid());
	}

	@Test
	void addHostUnderCapacitySucceeds() {
		GameRoom r = new GameRoom("s", "q", "pin", "LOBBY", 2);
		assertTrue(r.addHost(new Player("h", "H", 0, "sh", true)));
		assertFalse(r.isFull());
	}

	@Test
	void reclaimConnectedSeatReturnsNull() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("u1", "a", 0, "s1", true, "tok1"));
		assertNull(r.reclaim("tok1", "s2"));
	}

	@Test
	void reclaimUnknownTokenReturnsNullWhenAllDisconnected() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("u1", "a", 0, "s1", false, "tok1"));
		assertNull(r.reclaim("nope", "s2"));
	}

	@Test
	void reclaimEmptyRoomReturnsNull() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		assertNull(r.reclaim("tok", "s"));
	}

	@Test
	void refundDecrementsAttempt() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		assertTrue(r.tryBeginAttempt("q1", "u1", 5));
		assertTrue(r.tryBeginAttempt("q1", "u1", 5));
		assertEquals(2, r.attemptCount("q1", "u1"));
		r.refundAttempt("q1", "u1");
		assertEquals(1, r.attemptCount("q1", "u1"));
	}

	@Test
	void refundAtZeroStaysAtZero() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		assertTrue(r.tryBeginAttempt("q1", "u1", 5));
		r.refundAttempt("q1", "u1");
		assertEquals(0, r.attemptCount("q1", "u1"));
		r.refundAttempt("q1", "u1");
		assertEquals(0, r.attemptCount("q1", "u1"));
	}

	@Test
	void refundMissingKeyIsNoOp() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.refundAttempt("ghost-q", "ghost-u");
		assertEquals(0, r.attemptCount("ghost-q", "ghost-u"));
	}

	@Test
	void isFullExactlyAtCapacity() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 2);
		assertFalse(r.isFull());
		r.addPlayer(new Player("u1", "a", 0, "s1", true));
		assertFalse(r.isFull());
		r.addPlayer(new Player("u2", "b", 0, "s2", true));
		assertTrue(r.isFull());
	}

	@Test
	void getPlayerReflectsLiveBoardScore() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("u1", "a", 0, "s1", true));
		r.applyScore("u1", 250);
		assertEquals(250, r.getPlayer("u1").getScore());
	}

	@Test
	void applyScoreUnknownUuidReturnsMinusOne() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		assertEquals(-1, r.applyScore("ghost", 10));
	}

	@Test
	void playersIncludesHostSeatOutsideBoard() {
		GameRoom r = new GameRoom("s", "q", "pin", "LOBBY", 10);
		r.addHost(new Player("h", "Host", 0, "sh", true));
		r.addPlayer(new Player("u1", "a", 0, "s1", true));
		List<Player> all = r.getPlayers();
		assertEquals(2, all.size());
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.service.Player p : all) {
				if (p.getUuid().equals("h")) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.service.Player p : all) {
				if (p.getUuid().equals("u1")) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void softRemoveUnknownIsNoOp() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("u1", "a", 0, "s1", true));
		r.softRemove("ghost");
		assertEquals(1, r.getPlayers().size());
		assertTrue(r.getPlayer("u1").isConnected());
	}

	@Test
	void attemptCountMissingKeyIsZero() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		assertEquals(0, r.attemptCount("no-q", "no-u"));
	}

	@Test
	void streakOfUnknownIsZero() {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		assertEquals(0, r.streakOf("ghost"));
	}

	@Test
	void concurrentEightThreadsApplyScoreToOneSeat() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 20);
		r.addPlayer(new Player("solo", "S", 0, "ss", true));
		int threads = 8;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 150; i++)
							r.applyScore("solo", 4);
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(8 * 150 * 4, r.getPlayer("solo").getScore());
	}

	@Test
	void concurrentTwelveThreadsAddDistinctSeats() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 20);
		int threads = 12;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			final int idx = t;
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						r.addPlayer(new Player("n" + idx, "N", 0, "s" + idx, true));
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(12, r.getPlayers().size());
		assertEquals(12, r.getLeaderboard().size());
	}

	@Test
	void concurrentSoftRemoveAllThenConnectedZero() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 15);
		for (int i = 0; i < 10; i++)
			r.addPlayer(new Player("d" + i, "N", 0, "s" + i, true));
		int threads = 10;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			final String uuid = "d" + t;
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						r.softRemove(uuid);
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(0, r.connectedCount());
		assertEquals(10, r.getPlayers().size());
	}

	@Test
	void concurrentReclaimSingleDisconnectedSeatOneWin() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("rc", "R", 0, "old", true, "tok-rc"));
		r.applyScore("rc", 123);
		r.softRemove("rc");
		int threads = 8;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		java.util.concurrent.atomic.AtomicInteger wins = new java.util.concurrent.atomic.AtomicInteger();
		String[] winnerSess = {null};
		for (int t = 0; t < threads; t++) {
			final String sess = "ns-" + t;
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						Player got = r.reclaim("tok-rc", sess);
						if (got != null) {
							wins.incrementAndGet();
							synchronized (winnerSess) {
								winnerSess[0] = sess;
							}
						}
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(1, wins.get());
		assertEquals(winnerSess[0], r.getPlayer("rc").getSessionId());
		assertEquals(123, r.getPlayer("rc").getScore());
	}

	@Test
	void concurrentTryBeginAttemptRespectsCapTen() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		int threads = 10;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		java.util.concurrent.atomic.AtomicInteger ok = new java.util.concurrent.atomic.AtomicInteger();
		for (int t = 0; t < threads; t++) {
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 20; i++)
							if (r.tryBeginAttempt("qq", "uu", 10))
								ok.incrementAndGet();
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(10, ok.get());
		assertEquals(10, r.attemptCount("qq", "uu"));
	}

	@Test
	void concurrentBumpStreakTenThreadsHundredEach() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		int threads = 10;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 100; i++)
							r.bumpStreak("st");
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(1000, r.streakOf("st"));
	}

	@Test
	void concurrentMixedAddScoreRemoveKeepsInvariants() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 30);
		int threads = 12;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			final int idx = t;
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						r.addPlayer(new Player("m" + idx, "M", 0, "s" + idx, true));
						for (int i = 0; i < 40; i++)
							r.applyScore("m" + idx, 5);
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(12, r.getLeaderboard().size());
		for (int i = 0; i < 12; i++)
			assertEquals(200, r.getPlayer("m" + i).getScore());
	}

	@Test
	void concurrentGetPlayerDuringScoresSeesMonotonicGrowth() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("g", "G", 0, "ss", true));
		int writers = 8;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(writers + 2);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(writers + 2);
		java.util.concurrent.atomic.AtomicInteger maxSeen = new java.util.concurrent.atomic.AtomicInteger(0);
		for (int t = 0; t < writers; t++) {
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 80; i++)
							r.applyScore("g", 10);
						done.countDown();
					}
				}
			});
		}
		for (int t = 0; t < 2; t++) {
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 200; i++) {
							int s = r.getPlayer("g").getScore();
							maxSeen.accumulateAndGet(s, new java.util.function.IntBinaryOperator() {
								@Override
								public int applyAsInt(int a, int b) {
									return Math.max(a, b);
								}
							});
						}
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(8 * 80 * 10, r.getPlayer("g").getScore());
		org.junit.jupiter.api.Assertions.assertTrue(maxSeen.get() <= 8 * 80 * 10);
		org.junit.jupiter.api.Assertions.assertTrue(maxSeen.get() >= 0);
	}

	@Test
	void concurrentIsFullStableAtCapacity() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 6);
		int threads = 12;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			final int idx = t;
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						r.addPlayer(new Player("f" + idx, "F", 0, "s" + idx, true));
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertTrue(r.isFull());
		assertEquals(6, r.getPlayers().size());
	}

	@Test
	void concurrentPlayersListDuringChurnNeverNull() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 25);
		for (int i = 0; i < 10; i++)
			r.addPlayer(new Player("p" + i, "N", 0, "s" + i, true));
		int threads = 10;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		java.util.concurrent.atomic.AtomicInteger nulls = new java.util.concurrent.atomic.AtomicInteger();
		for (int t = 0; t < threads; t++) {
			final int idx = t;
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 60; i++) {
							if (idx % 2 == 0)
								r.applyScore("p" + (i % 10), 3);
							else if (r.getPlayers() == null)
								nulls.incrementAndGet();
							else
								r.getPlayers().size();
						}
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(0, nulls.get());
	}

	@Test
	void concurrentApplyScoreAndRefundKeepAttemptsCapped() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("ar", "A", 0, "ss", true));
		int threads = 8;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 50; i++) {
							r.tryBeginAttempt("qa", "ar", 200);
							r.applyScore("ar", 7);
							if (i % 5 == 0)
								r.refundAttempt("qa", "ar");
						}
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		org.junit.jupiter.api.Assertions.assertTrue(r.attemptCount("qa", "ar") <= 200);
		assertEquals((long) 8 * 50 * 7, r.getPlayer("ar").getScore());
	}

	@Test
	void concurrentHardRemoveUnknownNeverAffectsBoard() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("keep", "K", 0, "ss", true));
		r.applyScore("keep", 250);
		int threads = 8;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 100; i++)
							r.hardRemove("ghost-" + i);
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(250, r.getPlayer("keep").getScore());
		assertEquals(1, r.getLeaderboard().size());
	}

	@Test
	void concurrentReclaimWrongTokenAlwaysNull() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		r.addPlayer(new Player("wt", "W", 0, "old", false, "real-tok"));
		int threads = 8;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
		for (int t = 0; t < threads; t++) {
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 50; i++)
							if (r.reclaim("wrong-tok", "s") != null)
								hits.incrementAndGet();
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(0, hits.get());
	}

	@Test
	void concurrentLeaderboardSizeStableUnderScoreStorm() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 20);
		for (int i = 0; i < 10; i++)
			r.addPlayer(new Player("sz" + i, "N", 0, "s" + i, true));
		int threads = 10;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			final String uuid = "sz" + t;
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 70; i++)
							r.applyScore(uuid, 11);
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(10, r.getLeaderboard().size());
		for (int i = 0; i < 10; i++)
			assertEquals(770, r.getPlayer("sz" + i).getScore());
	}

	@Test
	void concurrentTeamCreateAndJoinConverge() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 30);
		for (int i = 0; i < 10; i++)
			r.addPlayer(new Player("tj" + i, "N", 0, "s" + i, true));
		GameRoom.Team team = r.createTeam("United");
		int threads = 10;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			final String uuid = "tj" + t;
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						r.joinTeam(team.getId(), uuid);
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		org.junit.jupiter.api.Assertions.assertTrue(r.getTeam(team.getId()).getMemberUuids().size() <= 10);
		org.junit.jupiter.api.Assertions.assertTrue(r.getTeam(team.getId()).getMemberUuids().size() >= 1);
	}

	@Test
	void concurrentBracketWritesKeepLastValue() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		int threads = 8;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			final int idx = t;
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 50; i++)
							r.beginBattleRound(
									java.util.List.of(new GameRoom.BattlePair("p" + idx, "q" + i, null, false)));
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(threads * 50, r.getBracketRounds().size());
		org.junit.jupiter.api.Assertions.assertNotNull(r.getBracketRounds().get(0));
	}

	@Test
	void concurrentStreakResetsKeepZeroFloor() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		int threads = 8;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 60; i++)
							r.resetStreak("floor");
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(0, r.streakOf("floor"));
	}

	@Test
	void concurrentUnknownScoreReturnsMinusOneAlways() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		int threads = 8;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		java.util.concurrent.atomic.AtomicInteger bad = new java.util.concurrent.atomic.AtomicInteger();
		for (int t = 0; t < threads; t++) {
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						for (int i = 0; i < 100; i++)
							if (r.applyScore("nope", 10) != -1)
								bad.incrementAndGet();
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(0, bad.get());
	}

	@Test
	void concurrentClearRoundsEmptiesAllPlayerRounds() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 10);
		for (int i = 0; i < 5; i++)
			r.recordRound("cl" + i, 100 + i, i);
		int threads = 4;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						r.clearRounds();
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		for (int i = 0; i < 5; i++)
			assertEquals(0, r.roundOf("cl" + i)[0]);
	}

	@Test
	void concurrentAddBeyondCapacityKeepsExactlyFull() throws Exception {
		GameRoom r = new GameRoom("s", "q", "pin", "ACTIVE", 4);
		int threads = 12;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			final int idx = t;
			pool.submit(new java.lang.Runnable() {
				@Override
				public void run() {
					{
						try {
							start.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						r.addPlayer(new Player("cap" + idx, "C", 0, "s" + idx, true));
						done.countDown();
					}
				}
			});
		}
		start.countDown();
		org.junit.jupiter.api.Assertions.assertTrue(done.await(9, java.util.concurrent.TimeUnit.SECONDS));
		pool.shutdown();
		pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(4, r.getPlayers().size());
		assertTrue(r.isFull());
	}
}
