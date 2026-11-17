package com.sprintjudge.domain.dto;

import java.util.Objects;

/** Final battle state: one player left standing. */
public final class BattleChampion {
	private String type;
	private String championUuid;
	private String championName;

	public BattleChampion() {
	}

	public BattleChampion(String type, String championUuid, String championName) {
		this.type = type;
		this.championUuid = championUuid;
		this.championName = championName;
	}

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
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
		if (!(o instanceof BattleChampion))
			return false;
		BattleChampion other = (BattleChampion) o;
		return Objects.equals(type, other.type) && Objects.equals(championUuid, other.championUuid)
				&& Objects.equals(championName, other.championName);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, championUuid, championName);
	}

	@Override
	public String toString() {
		return "BattleChampion[type=" + type + ", championUuid=" + championUuid + ", championName=" + championName
				+ "]";
	}
}
