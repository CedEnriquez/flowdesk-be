package com.cedrick.flowdeskbe.mapper;

import com.cedrick.flowdeskbe.dto.*;
import com.cedrick.flowdeskbe.entity.RequestActivity;
import com.cedrick.flowdeskbe.entity.ServiceRequest;
import com.cedrick.flowdeskbe.entity.User;
import com.cedrick.flowdeskbe.security.AuthUserPrincipal;
import com.cedrick.flowdeskbe.workflow.WorkflowRules;

import java.util.List;

/** Entity -> DTO conversion. Never exposes password_hash or internal ids of other users. */
public final class RequestMapper {

    private RequestMapper() {
    }

    public static UserResponse toUserResponse(AuthUserPrincipal p) {
        return new UserResponse(p.getId(), p.getName(), p.getEmail(), p.getRole());
    }

    public static RequestSummaryResponse toSummary(ServiceRequest r) {
        return new RequestSummaryResponse(r.getId(), r.getTitle(), r.getType(), r.getPriority(),
                r.getStatus(), r.getOwner().getName(), r.getCreatedAt(), r.getUpdatedAt());
    }

    public static RequestDetailResponse toDetail(ServiceRequest r, AuthUserPrincipal viewer) {
        User owner = r.getOwner();
        boolean isOwner = owner.getId().equals(viewer.getId());
        List<AvailableActionResponse> actions = WorkflowRules
                .availableFor(r.getStatus(), viewer.getRole(), isOwner).stream()
                .map(t -> new AvailableActionResponse(t.to(), t.requiresComment()))
                .toList();
        return new RequestDetailResponse(r.getId(), r.getTitle(), r.getDescription(), r.getType(),
                r.getPriority(), r.getStatus(), owner.getName(), owner.getEmail(),
                r.getCreatedAt(), r.getUpdatedAt(), actions);
    }

    public static ActivityResponse toActivity(RequestActivity a) {
        return new ActivityResponse(a.getId(), a.getPreviousStatus(), a.getNewStatus(),
                a.getActor().getName(), a.getComment(), a.getCreatedAt());
    }
}
