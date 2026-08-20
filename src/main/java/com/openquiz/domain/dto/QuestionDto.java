package com.openquiz.domain.dto;

import java.util.List;

public record QuestionDto(
        String id,
        String type,
        String title,
        String description,
        int timeLimitSec,
        int pointsBase,
        List<String> languagesAllowed,
        Object config
) {}
