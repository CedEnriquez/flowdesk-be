package com.cedrick.flowdeskbe.dto;

import com.cedrick.flowdeskbe.enums.RequestStatus;

import java.time.Instant;

public record ActivityResponse(
        Long id,
        RequestStatus previousStatus,
        RequestStatus newStatus,
        String actorName,
        String comment,
        Instant createdAt) {
}
