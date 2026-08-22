package com.sprintjudge.domain.dto;

public record JoinRequest(
        String type,
        String role,
        String name,
        String pin
) {}
