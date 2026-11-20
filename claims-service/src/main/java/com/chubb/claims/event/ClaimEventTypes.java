package com.chubb.claims.event;

/** Event type values shared by the timeline, outbox and Kafka messages. */
public final class ClaimEventTypes {

    public static final String CLAIM_SUBMITTED = "CLAIM_SUBMITTED";
    public static final String CLAIM_ASSIGNED = "CLAIM_ASSIGNED";
    public static final String INFO_REQUESTED = "INFO_REQUESTED";
    public static final String INFO_PROVIDED = "INFO_PROVIDED";
    public static final String CLAIM_ASSESSED = "CLAIM_ASSESSED";
    public static final String CLAIM_DECIDED = "CLAIM_DECIDED";
    public static final String CLAIM_SETTLED = "CLAIM_SETTLED";
    public static final String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";

    private ClaimEventTypes() {
    }
}
