package com.sprintjudge.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SubmitRequest(
        String type,
        String questionId,
        String language,
        JsonNode response
) {}
