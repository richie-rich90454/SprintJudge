package com.sprintjudge.domain.dto;

/** Final battle state: one player left standing. */
public record BattleChampion(String type, String championUuid, String championName) {}
