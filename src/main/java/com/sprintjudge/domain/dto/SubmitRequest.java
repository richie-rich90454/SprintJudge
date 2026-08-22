package com.openquiz.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SubmitRequest(
        String type,
        String questionId,
        String language,
        JsonNode response
) {}
