package com.cedrick.flowdeskbe.workflow;

import com.cedrick.flowdeskbe.enums.RequestStatus;
import com.cedrick.flowdeskbe.enums.Role;

import java.util.Set;

/** One row of the workflow table: from -> to, who may do it, and the extra conditions. */
public record Transition(
        RequestStatus from,
        RequestStatus to,
        Set<Role> allowedRoles,
        boolean requiresComment,
        boolean ownerOnly) {
}
