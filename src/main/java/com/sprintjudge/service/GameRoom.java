package com.sprintjudge.service;

import org.eclipse.jdt.annotation.Nullable;
import com.sprintjudge.domain.dto.LeaderboardEntry;
import com.sprintjudge.service.leaderboard.LiveLeaderboard;
import com.sprintjudge.service.leaderboard.RankedSkipList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live, in-memory state for a single game room.
 *
 * <p>
 * Identity/sessions live in a lock-free map; ranking truth lives in the exact
 * {@link LiveLeaderboard} (order-statistic skip list + delta ledger). Score
 * mutations are O(log n); full leaderboards are O(n) with zero sorting.
 *
 * <p>
 * Disconnected players stay in the board (ranked, marked offline) so a rejoin
 * or refresh reclaims their seat and final standings keep them a kicked player
 * is hard-removed instead.
 */
public class GameRoom {

	public static final class GameMode {
		public static final String STANDARD = "STANDARD";
		public static final String AUTO_PILOT = "AUTO_PILOT";
		public static final String PRACTICE = "PRACTICE";
		public static final String EXAM = "EXAM";
		public static final String TEAM = "TEAM";
		public static final String BATTLE = "BATTLE";

		private static final java.util.Set<String> VALUES = java.util.Set.of(STANDARD, AUTO_PILOT, PRACTICE, EXAM, TEAM,
				BATTLE);

		private GameMode() {
		}

		public static boolean isValid(String value) {
			return value != null && VALUES.contains(value);
		}
	}

	private final String sessionId;
	private final String quizId;
	private final String pin;
	private volatile String status;
	private volatile int currentQuestionIndex;
	private volatile String currentQuestionId;
	private volatile long currentQuestionEndEpochMs;
	private volatile long currentQuestionStartEpochMs;
	// Original deadline at round start; extendTimer caps total growth at +300s over
	// this.
	private volatile long questionEndBaseEpochMs;
	private volatile String hostUuid;
	private final int maxPlayers;
	private final String gameMode;

	private final Map<String, Player> players = new ConcurrentHashMap<>();
	// Read-only projector connections. They receive every broadcast but never
	// score, never count toward the roster, and never take the host seat.
	private final Map<String, Player> spectators = new ConcurrentHashMap<>();
	private final LiveLeaderboard board = new LiveLeaderboard();

	// In-memory per-(question,player) attempt counter (authoritative; the async
	// write buffer used to lag behind so rapid resubmits scored full marks).
	private final ConcurrentHashMap<String, Integer> attempts = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Integer> bestScores = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Integer> streaks = new ConcurrentHashMap<>();
	// Per-round contribution for the reveal screen: [roundScore, bonus].
	private final ConcurrentHashMap<String, int[]> lastRound = new ConcurrentHashMap<>();
	// Authoritative correctness for the last round. Partial credit can score
	// points while still being an incorrect answer, so correctness cannot be
	// inferred from the score at reveal time.
	private final ConcurrentHashMap<String, Boolean> lastRoundCorrect = new ConcurrentHashMap<>();

	// Exam mode: total end time across all questions.
	private volatile long totalEndEpochMs;

	// Team mode: team management.
	public static final class Team {
		private String id;
		private String name;
		private java.util.Set<String> memberUuids;
		private long score;

		@SuppressWarnings("all")
		public Team() {
		}

		public Team(String id, String name, java.util.Set<String> memberUuids, long score) {
			this.id = id;
			this.name = name;
			this.memberUuids = memberUuids;
			this.score = score;
		}

		public Team withScore(long s) {
			return new Team(id, name, memberUuids, s);
		}

		public Team addMember(String uuid) {
			java.util.HashSet<String> m = new java.util.HashSet<>(memberUuids);
			m.add(uuid);
			return new Team(id, name, m, score);
		}

		public String getId() {
			return id;
		}
		public void setId(String id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}

		public java.util.Set<String> getMemberUuids() {
			return memberUuids;
		}
		public void setMemberUuids(java.util.Set<String> memberUuids) {
			this.memberUuids = memberUuids;
		}

		public long getScore() {
			return score;
		}
		public void setScore(long score) {
			this.score = score;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (this == o)
				return true;
			if (!(o instanceof Team))
				return false;
			Team other = (Team) o;
			return score == other.score && java.util.Objects.equals(id, other.id)
					&& java.util.Objects.equals(name, other.name)
					&& java.util.Objects.equals(memberUuids, other.memberUuids);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(id, name, memberUuids, score);
		}

		@Override
		public String toString() {
			return "Team[id=" + id + ", name=" + name + ", memberUuids=" + memberUuids + ", score=" + score + "]";
		}
	}
	private final ConcurrentHashMap<String, Team> teams = new ConcurrentHashMap<>();
	private final java.util.concurrent.atomic.AtomicInteger teamSeq = new java.util.concurrent.atomic.AtomicInteger(0);

	// Battle mode: matchmaking and bracket.
	public static final class BattleMatch {
		private String id;
		private String p1Uuid;
		private String p2Uuid;
		private String questionId;
		private String p1Answer;
		private String p2Answer;
		private boolean p1Correct;
		private boolean p2Correct;
		private String winnerUuid;

		@SuppressWarnings("all")
		public BattleMatch() {
		}

		public BattleMatch(String id, String p1Uuid, String p2Uuid, String questionId, String p1Answer, String p2Answer,
				boolean p1Correct, boolean p2Correct, String winnerUuid) {
			this.id = id;
			this.p1Uuid = p1Uuid;
			this.p2Uuid = p2Uuid;
			this.questionId = questionId;
			this.p1Answer = p1Answer;
			this.p2Answer = p2Answer;
			this.p1Correct = p1Correct;
			this.p2Correct = p2Correct;
			this.winnerUuid = winnerUuid;
		}

		public String getId() {
			return id;
		}
		public void setId(String id) {
			this.id = id;
		}

		public String getP1Uuid() {
			return p1Uuid;
		}
		public void setP1Uuid(String p1Uuid) {
			this.p1Uuid = p1Uuid;
		}

		public String getP2Uuid() {
			return p2Uuid;
		}
		public void setP2Uuid(String p2Uuid) {
			this.p2Uuid = p2Uuid;
		}

		public String getQuestionId() {
			return questionId;
		}
		public void setQuestionId(String questionId) {
			this.questionId = questionId;
		}

		public String getP1Answer() {
			return p1Answer;
		}
		public void setP1Answer(String p1Answer) {
			this.p1Answer = p1Answer;
		}

		public String getP2Answer() {
			return p2Answer;
		}
		public void setP2Answer(String p2Answer) {
			this.p2Answer = p2Answer;
		}

		public boolean isP1Correct() {
			return p1Correct;
		}
		public void setP1Correct(boolean p1Correct) {
			this.p1Correct = p1Correct;
		}

		public boolean isP2Correct() {
			return p2Correct;
		}
		public void setP2Correct(boolean p2Correct) {
			this.p2Correct = p2Correct;
		}

		public String getWinnerUuid() {
			return winnerUuid;
		}
		public void setWinnerUuid(String winnerUuid) {
			this.winnerUuid = winnerUuid;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (this == o)
				return true;
			if (!(o instanceof BattleMatch))
				return false;
			BattleMatch other = (BattleMatch) o;
			return p1Correct == other.p1Correct && p2Correct == other.p2Correct
					&& java.util.Objects.equals(id, other.id) && java.util.Objects.equals(p1Uuid, other.p1Uuid)
					&& java.util.Objects.equals(p2Uuid, other.p2Uuid)
					&& java.util.Objects.equals(questionId, other.questionId)
					&& java.util.Objects.equals(p1Answer, other.p1Answer)
					&& java.util.Objects.equals(p2Answer, other.p2Answer)
					&& java.util.Objects.equals(winnerUuid, other.winnerUuid);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(id, p1Uuid, p2Uuid, questionId, p1Answer, p2Answer, p1Correct, p2Correct,
					winnerUuid);
		}

		@Override
		public String toString() {
			return "BattleMatch[id=" + id + ", p1Uuid=" + p1Uuid + ", p2Uuid=" + p2Uuid + ", questionId=" + questionId
					+ ", p1Answer=" + p1Answer + ", p2Answer=" + p2Answer + ", p1Correct=" + p1Correct + ", p2Correct="
					+ p2Correct + ", winnerUuid=" + winnerUuid + "]";
		}
	}
	private final java.util.concurrent.CopyOnWriteArrayList<BattleMatch> battleMatches = new CopyOnWriteArrayList<>();
	public static final class BattlePair {
		private String p1Uuid;
		private String p2Uuid;
		private String winnerUuid;
		private boolean bye;

		@SuppressWarnings("all")
		public BattlePair() {
		}

		public BattlePair(String p1Uuid, String p2Uuid, String winnerUuid, boolean bye) {
			this.p1Uuid = p1Uuid;
			this.p2Uuid = p2Uuid;
			this.winnerUuid = winnerUuid;
			this.bye = bye;
		}

		public BattlePair withWinner(String winner) {
			return new BattlePair(p1Uuid, p2Uuid, winner, bye);
		}

		public String getP1Uuid() {
			return p1Uuid;
		}
		public void setP1Uuid(String p1Uuid) {
			this.p1Uuid = p1Uuid;
		}

		public String getP2Uuid() {
			return p2Uuid;
		}
		public void setP2Uuid(String p2Uuid) {
			this.p2Uuid = p2Uuid;
		}

		public String getWinnerUuid() {
			return winnerUuid;
		}
		public void setWinnerUuid(String winnerUuid) {
			this.winnerUuid = winnerUuid;
		}

		public boolean isBye() {
			return bye;
		}
		public void setBye(boolean bye) {
			this.bye = bye;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (this == o)
				return true;
			if (!(o instanceof BattlePair))
				return false;
			BattlePair other = (BattlePair) o;
			return bye == other.bye && java.util.Objects.equals(p1Uuid, other.p1Uuid)
					&& java.util.Objects.equals(p2Uuid, other.p2Uuid)
					&& java.util.Objects.equals(winnerUuid, other.winnerUuid);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(p1Uuid, p2Uuid, winnerUuid, bye);
		}

		@Override
		public String toString() {
			return "BattlePair[p1Uuid=" + p1Uuid + ", p2Uuid=" + p2Uuid + ", winnerUuid=" + winnerUuid + ", bye=" + bye
					+ "]";
		}
	}
	public static final class BattleRound {
		private int number;
		private List<BattlePair> pairs;

		@SuppressWarnings("all")
		public BattleRound() {
		}

		public BattleRound(int number, List<BattlePair> pairs) {
			this.number = number;
			this.pairs = pairs;
		}

		public int getNumber() {
			return number;
		}
		public void setNumber(int number) {
			this.number = number;
		}

		public List<BattlePair> getPairs() {
			return pairs;
		}
		public void setPairs(List<BattlePair> pairs) {
			this.pairs = pairs;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (this == o)
				return true;
			if (!(o instanceof BattleRound))
				return false;
			BattleRound other = (BattleRound) o;
			return number == other.number && java.util.Objects.equals(pairs, other.pairs);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(number, pairs);
		}

		@Override
		public String toString() {
			return "BattleRound[number=" + number + ", pairs=" + pairs + "]";
		}
	}
	private final java.util.concurrent.CopyOnWriteArrayList<BattleRound> bracketRounds = new CopyOnWriteArrayList<>();
	private final ConcurrentHashMap<String, Long> battleAnswerTimes = new ConcurrentHashMap<>();
	private volatile int battleRound;
	private volatile List<String> battleWinners = List.of();
	private volatile String championUuid;

	private volatile long lastActivityMs = System.currentTimeMillis();

	public GameRoom(String sessionId, String quizId, String pin, String status, int maxPlayers, String gameMode) {
		this.sessionId = sessionId;
		this.quizId = quizId;
		this.pin = pin;
		this.status = status;
		this.maxPlayers = maxPlayers;
		this.gameMode = gameMode;
	}

	/** Legacy ctor kept for tests/tools: default cap 500, standard mode. */
	public GameRoom(String sessionId, String quizId, String pin, String status) {
		this(sessionId, quizId, pin, status, 500, GameMode.STANDARD);
	}

	/** 5-arg ctor for computeIfAbsent: defaults to STANDARD mode. */
	public GameRoom(String sessionId, String quizId, String pin, String status, int maxPlayers) {
		this(sessionId, quizId, pin, status, maxPlayers, GameMode.STANDARD);
	}

	/** Returns false when the room is at capacity. */
	public synchronized boolean addPlayer(Player p) {
		if (players.size() >= maxPlayers)
			return false;
		players.put(p.getUuid(), p);
		board.join(p.getUuid(), p.getName());
		return true;
	}

	/** Hosts occupy a roster seat but never enter the scored board. */
	public synchronized boolean addHost(Player p) {
		if (players.size() >= maxPlayers)
			return false;
		players.put(p.getUuid(), p);
		return true;
	}

	/** Spectators watch the room without a roster seat or a score. */
	public synchronized boolean addSpectator(Player p) {
		if (players.size() >= maxPlayers)
			return false;
		spectators.put(p.getUuid(), p);
		return true;
	}

	public synchronized void removeSpectator(String uuid) {
		if (uuid != null)
			spectators.remove(uuid);
	}

	public synchronized List<Player> getSpectators() {
		return new ArrayList<>(spectators.values());
	}

	/** Disconnect: keep the board row so standings/rankings survive the gap. */
	public synchronized void softRemove(String uuid) {
		Player p = players.get(uuid);
		if (p != null)
			players.put(uuid, p.disconnected());
	}

	/** Hard remove (kick or sweep): drop from board too. */
	public synchronized void hardRemove(String uuid) {
		players.remove(uuid);
		board.remove(uuid);
	}

	/** Session id of the seat a fresh rejoin would take over, if any. */
	public synchronized String supersededSessionId(String token) {
		if (token == null)
			return null;
		for (Player p : players.values()) {
			if (!p.isConnected() && token.equals(p.getToken()))
				return p.getSessionId();
		}
		return null;
	}

	public synchronized Player reclaim(String token, String sessionId) {
		if (token == null)
			return null;
		for (Player p : players.values()) {
			if (!p.isConnected() && token.equals(p.getToken())) {
				Player reclaimed = p.withSession(sessionId);
				players.put(reclaimed.getUuid(), reclaimed);
				return reclaimed;
			}
		}
		return null;
	}

	public Player getPlayer(String uuid) {
		if (uuid == null)
			return null;
		Player p = players.get(uuid);
		return p == null ? null : p.withScore((int) board.scoreOf(uuid));
	}

	/** Applies a score delta; returns the new exact rank (or -1 for unknown). */
	public int applyScore(String uuid, long delta) {
		return board.applyScore(uuid, delta);
	}

	/**
	 * Applies only the improvement over this player's best result for a question.
	 * Repeated submissions must not keep adding the full base score to the board.
	 * The compare-and-set is atomic with the award so concurrent judges cannot both
	 * claim the same improvement.
	 */
	public synchronized int applyBestScore(String questionId, String uuid, int total) {
		String key = questionId + " " + uuid;
		Integer previous = bestScores.get(key);
		if (previous != null && previous.intValue() >= total)
			return 0;
		bestScores.put(key, Integer.valueOf(total));
		int delta = total - (previous == null ? 0 : previous.intValue());
		applyScore(uuid, delta);
		return delta;
	}

	public int bestScore(String questionId, String uuid) {
		Integer value = bestScores.get(questionId + " " + uuid);
		return value == null ? 0 : value.intValue();
	}

	public synchronized List<Player> getPlayers() {
		List<Player> out = new ArrayList<>(players.size());
		for (RankedSkipList.Entry e : board.snapshot()) {
			Player p = players.get(e.getUuid());
			if (p != null)
				out.add(p.withScore((int) e.getScore()));
		}
		for (Player p : players.values()) {
			boolean found = false;
			for (Player x : out) {
				if (x.getUuid().equals(p.getUuid())) {
					found = true;
					break;
				}
			}
			if (!found)
				out.add(p);
		}
		return out;
	}

	public List<LeaderboardEntry> getLeaderboard() {
		List<RankedSkipList.Entry> snap = board.snapshot();
		List<LeaderboardEntry> out = new ArrayList<>(snap.size());
		int rank = 1;
		for (RankedSkipList.Entry e : snap) {
			out.add(new LeaderboardEntry(e.getUuid(), e.getName(), (int) e.getScore(), rank++));
		}
		return out;
	}

	public LiveLeaderboard getBoard() {
		return board;
	}

	public boolean isFull() {
		return players.size() >= maxPlayers;
	}

	public int connectedCount() {
		int n = spectators.size();
		for (Player p : players.values())
			if (p.isConnected())
				n++;
		return n;
	}

	// ---------- attempts / streaks / round stats ----------

	public boolean tryBeginAttempt(String questionId, String uuid, int max) {
		String key = questionId + " " + uuid;
		boolean[] accepted = {false};
		attempts.compute(key, new java.util.function.BiFunction<String, Integer, Integer>() {
			@Override
			public Integer apply(String k, Integer v) {
				int cur = v == null ? 0 : v;
				if (cur >= max)
					return v;
				accepted[0] = true;
				return cur + 1;
			}
		});
		return accepted[0];
	}

	public int attemptCount(String questionId, String uuid) {
		return attempts.getOrDefault(questionId + " " + uuid, 0);
	}

	/**
	 * Returns one attempt (busy-rejected judge submissions must not consume the
	 * cap).
	 */
	public void refundAttempt(String questionId, String uuid) {
		attempts.computeIfPresent(questionId + " " + uuid,
				new java.util.function.BiFunction<String, Integer, Integer>() {
					@Override
					public Integer apply(String k, Integer v) {
						return v > 0 ? v - 1 : v;
					}
				});
	}

	public int bumpStreak(String uuid) {
		return streaks.merge(uuid, 1, new java.util.function.BiFunction<Integer, Integer, Integer>() {
			@Override
			public Integer apply(Integer a, Integer b) {
				return a + b;
			}
		});
	}

	public void resetStreak(String uuid) {
		streaks.put(uuid, 0);
	}

	public int streakOf(String uuid) {
		return streaks.getOrDefault(uuid, 0);
	}

	public void recordRound(String uuid, int roundScore, int bonus) {
		recordRound(uuid, roundScore, bonus, roundScore > 0);
	}

	public void recordRound(String uuid, int roundScore, int bonus, boolean correct) {
		lastRound.put(uuid, new int[]{roundScore, bonus});
		lastRoundCorrect.put(uuid, Boolean.valueOf(correct));
	}

	public void clearRounds() {
		lastRound.clear();
		lastRoundCorrect.clear();
	}

	public int[] roundOf(String uuid) {
		return lastRound.getOrDefault(uuid, new int[]{0, 0});
	}

	public boolean roundCorrect(String uuid) {
		return lastRoundCorrect.getOrDefault(uuid, Boolean.FALSE).booleanValue();
	}

	// ---------- lifecycle bookkeeping ----------

	public void touch() {
		lastActivityMs = System.currentTimeMillis();
	}

	public long idleMs(long now) {
		return now - lastActivityMs;
	}

	public int capacity() {
		return maxPlayers;
	}

	// ---------- accessors ----------

	public String getSessionId() {
		return sessionId;
	}
	public String getQuizId() {
		return quizId;
	}
	public String getPin() {
		return pin;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public synchronized String getHostUuid() {
		return hostUuid;
	}
	public synchronized void setHostUuid(String hostUuid) {
		this.hostUuid = hostUuid;
	}
	public int getCurrentQuestionIndex() {
		return currentQuestionIndex;
	}
	public void setCurrentQuestionIndex(int i) {
		this.currentQuestionIndex = i;
	}
	public String getCurrentQuestionId() {
		return currentQuestionId;
	}
	public void setCurrentQuestionId(String id) {
		this.currentQuestionId = id;
	}
	public long getCurrentQuestionEndEpochMs() {
		return currentQuestionEndEpochMs;
	}
	public void setCurrentQuestionEndEpochMs(long ms) {
		this.currentQuestionEndEpochMs = ms;
	}
	public long getQuestionEndBaseEpochMs() {
		return questionEndBaseEpochMs;
	}
	public void setQuestionEndBaseEpochMs(long ms) {
		this.questionEndBaseEpochMs = ms;
	}
	public long getCurrentQuestionStartEpochMs() {
		return currentQuestionStartEpochMs;
	}
	public void setCurrentQuestionStartEpochMs(long ms) {
		this.currentQuestionStartEpochMs = ms;
	}
	public String getGameMode() {
		return gameMode;
	}
	public long getTotalEndEpochMs() {
		return totalEndEpochMs;
	}
	public void setTotalEndEpochMs(long ms) {
		this.totalEndEpochMs = ms;
	}

	// ---------- team mode ----------

	public Team createTeam(String name) {
		String id = "team-" + teamSeq.incrementAndGet();
		Team t = new Team(id, name, java.util.Set.of(), 0);
		teams.put(id, t);
		return t;
	}

	public Team joinTeam(String teamId, String playerUuid) {
		// A player belongs to exactly one team: move between teams without
		// leaving a duplicate membership that would double-count scores.
		leaveTeam(playerUuid);
		return teams.compute(teamId, new java.util.function.BiFunction<String, Team, Team>() {
			@Override
			public Team apply(String id, Team t) {
				if (t == null)
					return null;
				return t.addMember(playerUuid);
			}
		});
	}

	public void leaveTeam(String playerUuid) {
		for (Team t : teams.values()) {
			if (t.getMemberUuids().contains(playerUuid)) {
				teams.computeIfPresent(t.getId(), new java.util.function.BiFunction<String, Team, Team>() {
					@Override
					public Team apply(String id, Team cur) {
						java.util.HashSet<String> m = new java.util.HashSet<>(cur.getMemberUuids());
						m.remove(playerUuid);
						return new Team(cur.getId(), cur.getName(), m, cur.getScore());
					}
				});
			}
		}
	}

	public Team getTeam(String teamId) {
		return teams.get(teamId);
	}
	public java.util.Collection<Team> allTeams() {
		return teams.values();
	}

	/** Score-ranked team standings (host board and review). */
	public List<Team> teamsSorted() {
		List<Team> out = new ArrayList<>(teams.values());
		java.util.Collections.sort(out, new java.util.Comparator<Team>() {
			@Override
			public int compare(Team a, Team b) {
				int c = Long.compare(b.getScore(), a.getScore());
				if (c != 0)
					return c;
				String an = a.getName() == null ? "" : a.getName();
				String bn = b.getName() == null ? "" : b.getName();
				return an.compareTo(bn);
			}
		});
		return out;
	}

	public String teamIdOf(String playerUuid) {
		for (Team t : teams.values()) {
			if (t.getMemberUuids().contains(playerUuid))
				return t.getId();
		}
		return null;
	}

	public long applyTeamScore(String teamId, long delta) {
		Team result = teams.compute(teamId, new java.util.function.BiFunction<String, Team, Team>() {
			@Override
			public Team apply(String id, Team t) {
				if (t == null)
					return null;
				return t.withScore(t.getScore() + delta);
			}
		});
		return result == null ? 0 : result.getScore();
	}

	// ---------- battle mode ----------

	public void addBattleMatch(BattleMatch m) {
		battleMatches.add(m);
	}
	public java.util.List<BattleMatch> getBattleMatches() {
		return battleMatches;
	}

	/**
	 * Clears every artifact of a previous battle so a restart never stacks rounds.
	 */
	public void resetBattle() {
		battleMatches.clear();
		bracketRounds.clear();
		battleAnswerTimes.clear();
		battleRound = 0;
		battleWinners = List.of();
		championUuid = null;
	}

	/** Opens the next round and appends it to the bracket history. */
	public synchronized void beginBattleRound(List<BattlePair> pairs) {
		if (pairs.isEmpty())
			return;
		battleRound++;
		battleWinners = List.of();
		bracketRounds.add(new BattleRound(battleRound, List.copyOf(pairs)));
	}

	public int getBattleRound() {
		return battleRound;
	}

	public List<BattleRound> getBracketRounds() {
		return List.copyOf(bracketRounds);
	}

	/** The most recently opened round, or null before the battle starts. */
	public BattleRound currentBattleRound() {
		return bracketRounds.isEmpty() ? null : bracketRounds.get(bracketRounds.size() - 1);
	}

	/** Replaces the open round's pairs with their winners (same round number). */
	public synchronized void resolveCurrentBattleRound(List<BattlePair> resolved) {
		if (bracketRounds.isEmpty())
			return;
		BattleRound cur = bracketRounds.get(bracketRounds.size() - 1);
		bracketRounds.set(bracketRounds.size() - 1, new BattleRound(cur.getNumber(), List.copyOf(resolved)));
	}

	/** The pair the player fights in, or null when they are not in this round. */
	public BattlePair pairOf(String playerUuid) {
		BattleRound cur = currentBattleRound();
		if (cur == null || playerUuid == null)
			return null;
		for (BattlePair p : cur.getPairs()) {
			if (playerUuid.equals(p.getP1Uuid()) || playerUuid.equals(p.getP2Uuid()))
				return p;
		}
		return null;
	}

	public void setBattleWinners(List<String> winners) {
		battleWinners = List.copyOf(winners);
	}
	public List<String> getBattleWinners() {
		return battleWinners;
	}
	public void setChampionUuid(String uuid) {
		championUuid = uuid;
	}
	public String getChampionUuid() {
		return championUuid;
	}

	public void markBattleAnswer(String playerUuid, long epochMs) {
		battleAnswerTimes.putIfAbsent(playerUuid, epochMs);
	}

	public Long battleAnswerMs(String playerUuid) {
		return battleAnswerTimes.get(playerUuid);
	}

	public void clearBattleAnswerTimes() {
		battleAnswerTimes.clear();
	}
}
