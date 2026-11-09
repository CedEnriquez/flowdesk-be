package com.cedrick.flowdeskbe.dto;

import com.cedrick.flowdeskbe.enums.Priority;
import com.cedrick.flowdeskbe.enums.RequestStatus;
import com.cedrick.flowdeskbe.enums.RequestType;

import java.time.Instant;

public record RequestSummaryResponse(
        Long id,
        String title,
        RequestType type,
        Priority priority,
        RequestStatus status,
        String ownerName,
        Instant createdAt,
        Instant updatedAt) {
}
