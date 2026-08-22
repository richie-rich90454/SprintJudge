package com.sprintjudge.domain.dto;

public record JoinedMessage(
        String type,
        String uuid,
        RoomState room
) {}
