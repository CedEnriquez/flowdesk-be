package com.cedrick.flowdeskbe.controller;

import com.cedrick.flowdeskbe.dto.*;
import com.cedrick.flowdeskbe.enums.RequestStatus;
import com.cedrick.flowdeskbe.security.AuthUserPrincipal;
import com.cedrick.flowdeskbe.service.RequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** No PUT/PATCH/DELETE: status only changes through /transitions, audit rows are never editable. */
@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;

    @PostMapping
    public ResponseEntity<ApiResponse<RequestDetailResponse>> create(
            @Valid @RequestBody CreateRequestDto dto,
            @AuthenticationPrincipal AuthUserPrincipal currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(requestService.create(dto, currentUser)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RequestSummaryResponse>>> list(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthUserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(requestService.list(status, page, size, currentUser)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RequestDetailResponse>> get(
            @PathVariable Long id, @AuthenticationPrincipal AuthUserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(requestService.get(id, currentUser)));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<ApiResponse<List<ActivityResponse>>> activity(
            @PathVariable Long id, @AuthenticationPrincipal AuthUserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(requestService.getActivity(id, currentUser)));
    }

    @PostMapping("/{id}/transitions")
    public ResponseEntity<ApiResponse<RequestDetailResponse>> transition(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequestDto dto,
            @AuthenticationPrincipal AuthUserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", requestService.transition(id, dto, currentUser)));
    }
}
