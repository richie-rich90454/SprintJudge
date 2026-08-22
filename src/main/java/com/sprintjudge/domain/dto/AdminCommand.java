package com.sprintjudge.domain.dto;

public record AdminCommand(
        String type,
        String action,
        Object payload
) {}
