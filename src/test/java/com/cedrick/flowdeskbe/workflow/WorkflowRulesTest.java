package com.cedrick.flowdeskbe.workflow;

import org.junit.jupiter.api.Test;

import static com.cedrick.flowdeskbe.enums.RequestStatus.*;
import static com.cedrick.flowdeskbe.enums.Role.ADMIN;
import static com.cedrick.flowdeskbe.enums.Role.REQUESTER;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRulesTest {

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        for (var terminal : new com.cedrick.flowdeskbe.enums.RequestStatus[]{REJECTED, FULFILLED, CANCELLED}) {
            assertThat(WorkflowRules.all()).noneMatch(t -> t.from() == terminal);
        }
    }

    @Test
    void rejectRequiresCommentAndAdmin() {
        Transition reject = WorkflowRules.find(IN_REVIEW, REJECTED).orElseThrow();
        assertThat(reject.requiresComment()).isTrue();
        assertThat(WorkflowRules.isAllowed(reject, ADMIN, false)).isTrue();
        assertThat(WorkflowRules.isAllowed(reject, REQUESTER, true)).isFalse();
    }

    @Test
    void cancelIsOwnerOnly() {
        Transition cancel = WorkflowRules.find(SUBMITTED, CANCELLED).orElseThrow();
        assertThat(WorkflowRules.isAllowed(cancel, REQUESTER, true)).isTrue();
        assertThat(WorkflowRules.isAllowed(cancel, REQUESTER, false)).isFalse();
        assertThat(WorkflowRules.isAllowed(cancel, ADMIN, false)).isFalse();
    }

    @Test
    void cannotSkipReview() {
        assertThat(WorkflowRules.find(SUBMITTED, APPROVED)).isEmpty();
    }

    @Test
    void availableActionsDependOnRoleAndOwnership() {
        assertThat(WorkflowRules.availableFor(IN_REVIEW, REQUESTER, true))
                .extracting(Transition::to).containsExactly(CANCELLED);
        assertThat(WorkflowRules.availableFor(IN_REVIEW, ADMIN, false))
                .extracting(Transition::to).containsExactlyInAnyOrder(APPROVED, REJECTED);
    }
}
