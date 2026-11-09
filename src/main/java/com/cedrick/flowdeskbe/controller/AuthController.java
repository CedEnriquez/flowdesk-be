package com.cedrick.flowdeskbe.controller;

import com.cedrick.flowdeskbe.dto.ApiResponse;
import com.cedrick.flowdeskbe.dto.LoginRequestDto;
import com.cedrick.flowdeskbe.dto.UserResponse;
import com.cedrick.flowdeskbe.mapper.RequestMapper;
import com.cedrick.flowdeskbe.security.AuthUserPrincipal;
import com.cedrick.flowdeskbe.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequestDto dto,
                                                           HttpServletRequest request,
                                                           HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok("Logged in", authService.login(dto, request, response)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.ok("Logged out", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(RequestMapper.toUserResponse(principal)));
    }
}
