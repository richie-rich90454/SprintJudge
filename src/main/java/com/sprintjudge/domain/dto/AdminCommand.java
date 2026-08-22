package com.openquiz.domain.dto;

public record AdminCommand(
        String type,
        String action,
        Object payload
) {}
