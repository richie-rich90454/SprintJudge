package com.sprintjudge.domain.dto;

import java.util.List;
import java.util.Objects;

/** Full battle bracket: every round played so far plus champion state. */
public final class BattleBracket {
	private String type;
	private List<Round> rounds;
	private String championUuid;
	private String championName;

	public BattleBracket() {
	}

	public BattleBracket(String type, List<Round> rounds, String championUuid, String championName) {
		this.type = type;
		this.rounds = rounds;
		this.championUuid = championUuid;
		this.championName = championName;
	}

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}

	public List<Round> getRounds() {
		return rounds;
	}
	public void setRounds(List<Round> rounds) {
		this.rounds = rounds;
	}

	public String getChampionUuid() {
		return championUuid;
	}
	public void setChampionUuid(String championUuid) {
		this.championUuid = championUuid;
	}

	public String getChampionName() {
		return championName;
	}
	public void setChampionName(String championName) {
		this.championName = championName;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof BattleBracket))
			return false;
		BattleBracket other = (BattleBracket) o;
		return Objects.equals(type, other.type) && Objects.equals(rounds, other.rounds)
				&& Objects.equals(championUuid, other.championUuid) && Objects.equals(championName, other.championName);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, rounds, championUuid, championName);
	}

	@Override
	public String toString() {
		return "BattleBracket[type=" + type + ", rounds=" + rounds + ", championUuid=" + championUuid
				+ ", championName=" + championName + "]";
	}

	public static final class Pair {
		private String p1Uuid;
		private String p2Uuid;
		private String winnerUuid;
		private boolean bye;

		public Pair() {
		}

		public Pair(String p1Uuid, String p2Uuid, String winnerUuid, boolean bye) {
			this.p1Uuid = p1Uuid;
			this.p2Uuid = p2Uuid;
			this.winnerUuid = winnerUuid;
			this.bye = bye;
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
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof Pair))
				return false;
			Pair other = (Pair) o;
			return bye == other.bye && Objects.equals(p1Uuid, other.p1Uuid) && Objects.equals(p2Uuid, other.p2Uuid)
					&& Objects.equals(winnerUuid, other.winnerUuid);
		}

		@Override
		public int hashCode() {
			return Objects.hash(p1Uuid, p2Uuid, winnerUuid, bye);
		}

		@Override
		public String toString() {
			return "Pair[p1Uuid=" + p1Uuid + ", p2Uuid=" + p2Uuid + ", winnerUuid=" + winnerUuid + ", bye=" + bye + "]";
		}
	}

	public static final class Round {
		private int number;
		private List<Pair> pairs;

		public Round() {
		}

		public Round(int number, List<Pair> pairs) {
			this.number = number;
			this.pairs = pairs;
		}

		public int getNumber() {
			return number;
		}
		public void setNumber(int number) {
			this.number = number;
		}

		public List<Pair> getPairs() {
			return pairs;
		}
		public void setPairs(List<Pair> pairs) {
			this.pairs = pairs;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof Round))
				return false;
			Round other = (Round) o;
			return number == other.number && Objects.equals(pairs, other.pairs);
		}

		@Override
		public int hashCode() {
			return Objects.hash(number, pairs);
		}

		@Override
		public String toString() {
			return "Round[number=" + number + ", pairs=" + pairs + "]";
		}
	}
}
