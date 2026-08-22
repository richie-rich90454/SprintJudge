package com.openquiz.domain.dto;

public record ErrorMessage(
        String type,
        String message
) {}
