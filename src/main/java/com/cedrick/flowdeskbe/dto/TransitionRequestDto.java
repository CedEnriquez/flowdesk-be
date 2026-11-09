package com.cedrick.flowdeskbe.dto;

import com.cedrick.flowdeskbe.enums.RequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransitionRequestDto(
        @NotNull RequestStatus toStatus,
        @Size(max = 2000) String comment) {
}
