package com.cedrick.flowdeskbe.workflow;

import com.cedrick.flowdeskbe.enums.RequestStatus;
import com.cedrick.flowdeskbe.enums.Role;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.cedrick.flowdeskbe.enums.RequestStatus.*;
import static com.cedrick.flowdeskbe.enums.Role.ADMIN;
import static com.cedrick.flowdeskbe.enums.Role.REQUESTER;

/**
 * The single source of truth for the request lifecycle. The service, the
 * {@code availableActions} field and the tests all read from this table.
 * To change the workflow, edit the table - nothing else.
 */
public final class WorkflowRules {

    private static final Set<Role> ADMIN_ONLY = Set.of(ADMIN);
    private static final Set<Role> ANY_ROLE = Set.of(REQUESTER, ADMIN);

    private static final List<Transition> TRANSITIONS = List.of(
            //         from       to          roles       comment  ownerOnly
            new Transition(SUBMITTED, IN_REVIEW, ADMIN_ONLY, false, false),
            new Transition(SUBMITTED, CANCELLED, ANY_ROLE,   false, true),
            new Transition(IN_REVIEW, APPROVED,  ADMIN_ONLY, false, false),
            new Transition(IN_REVIEW, REJECTED,  ADMIN_ONLY, true,  false),
            new Transition(IN_REVIEW, CANCELLED, ANY_ROLE,   false, true),
            new Transition(APPROVED,  FULFILLED, ADMIN_ONLY, false, false)
    );

    private WorkflowRules() {
    }

    public static List<Transition> all() {
        return TRANSITIONS;
    }

    public static Optional<Transition> find(RequestStatus from, RequestStatus to) {
        return TRANSITIONS.stream()
                .filter(t -> t.from() == from && t.to() == to)
                .findFirst();
    }

    /** Transitions out of {@code from} that this user is actually allowed to perform. */
    public static List<Transition> availableFor(RequestStatus from, Role role, boolean isOwner) {
        return TRANSITIONS.stream()
                .filter(t -> t.from() == from)
                .filter(t -> isAllowed(t, role, isOwner))
                .toList();
    }

    public static boolean isAllowed(Transition t, Role role, boolean isOwner) {
        return t.allowedRoles().contains(role) && (!t.ownerOnly() || isOwner);
    }
}
