package com.chubb.insights.event;

/** Topic names shared with claims-service. Keep in sync with the producer contract. */
public final class ClaimTopics {

    public static final String CLAIM_SUBMITTED = "claim.submitted";
    public static final String CLAIM_ASSIGNED = "claim.assigned";
    public static final String INFO_REQUESTED = "claim.info-requested";
    public static final String INFO_PROVIDED = "claim.info-provided";
    public static final String CLAIM_ASSESSED = "claim.assessed";
    public static final String CLAIM_DECIDED = "claim.decided";
    public static final String CLAIM_SETTLED = "claim.settled";

    private ClaimTopics() {
    }
}
