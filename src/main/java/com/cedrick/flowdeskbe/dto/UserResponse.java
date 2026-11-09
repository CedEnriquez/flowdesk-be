package com.cedrick.flowdeskbe.dto;

import com.cedrick.flowdeskbe.enums.Role;

public record UserResponse(Long id, String name, String email, Role role) {
}
