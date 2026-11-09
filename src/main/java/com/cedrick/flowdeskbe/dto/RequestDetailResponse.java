package com.cedrick.flowdeskbe.dto;

import com.cedrick.flowdeskbe.enums.Priority;
import com.cedrick.flowdeskbe.enums.RequestStatus;
import com.cedrick.flowdeskbe.enums.RequestType;

import java.time.Instant;
import java.util.List;

public record RequestDetailResponse(
        Long id,
        String title,
        String description,
        RequestType type,
        Priority priority,
        RequestStatus status,
        String ownerName,
        String ownerEmail,
        Instant createdAt,
        Instant updatedAt,
        List<AvailableActionResponse> availableActions) {
}
