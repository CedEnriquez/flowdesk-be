package com.cedrick.flowdeskbe.dto;

import com.cedrick.flowdeskbe.enums.Priority;
import com.cedrick.flowdeskbe.enums.RequestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Deliberately has no owner, role or user id: identity comes from the session only. */
public record CreateRequestDto(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotNull RequestType type,
        @NotNull Priority priority) {
}
