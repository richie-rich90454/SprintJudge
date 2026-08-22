package com.openquiz.domain.dto;

public record JoinRequest(
        String type,
        String role,
        String name,
        String pin
) {}
