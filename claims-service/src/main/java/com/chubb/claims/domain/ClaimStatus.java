package com.chubb.claims.domain;

import java.util.Map;
import java.util.Set;

/**
 * Claim lifecycle. The allowed transitions are declared here so the rule lives in one place
 * and can be unit tested without Spring or a database.
 *
 * <pre>
 * SUBMITTED -> UNDER_REVIEW -> INFO_REQUESTED -> UNDER_REVIEW
 *                           -> ASSESSED -> APPROVED -> SETTLED
 *                           -> REJECTED
 *              ASSESSED     -> REJECTED
 * </pre>
 */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    INFO_REQUESTED,
    ASSESSED,
    APPROVED,
    REJECTED,
    SETTLED;

    private static final Map<ClaimStatus, Set<ClaimStatus>> ALLOWED_TRANSITIONS = Map.of(
            SUBMITTED, Set.of(UNDER_REVIEW),
            UNDER_REVIEW, Set.of(INFO_REQUESTED, ASSESSED, REJECTED),
            INFO_REQUESTED, Set.of(UNDER_REVIEW),
            ASSESSED, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(SETTLED),
            REJECTED, Set.<ClaimStatus>of(),
            SETTLED, Set.<ClaimStatus>of()
    );

    public boolean canTransitionTo(ClaimStatus target) {
        return target != null && ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public Set<ClaimStatus> allowedTransitions() {
        return ALLOWED_TRANSITIONS.get(this);
    }

    public boolean isTerminal() {
        return this == REJECTED || this == SETTLED;
    }
}
