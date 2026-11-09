package com.cedrick.flowdeskbe.dto;

import com.cedrick.flowdeskbe.enums.RequestStatus;

public record AvailableActionResponse(RequestStatus toStatus, boolean requiresComment) {
}
